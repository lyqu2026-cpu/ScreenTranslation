package com.screentranslation.app.util

import android.app.Activity
import android.os.Build
import android.view.View
import java.lang.reflect.Proxy

/**
 * Predictive-back ([android.window.OnBackInvokedCallback], API 33+) registration
 * without any compile-time or load-time reference to `android.window.*`.
 *
 * All framework access here goes through reflection and is entered only when
 * `SDK_INT >= TIRAMISU`, so this file — and every caller — is safe to load on
 * Android 12/12L (API 31/32), where those classes do not exist. On API < 33
 * [register] returns `null` and callers keep their existing `KEYCODE_BACK`
 * handling (overlay `OnKeyListener` / activity `onKeyDown`/`onKeyUp`), which
 * still receives the back gesture on those releases.
 *
 * The returned lambda unregisters the callback; callers must invoke it (for
 * example in `onDestroy` / when leaving selection mode) to avoid leaking a
 * dispatcher reference.
 */
object BackInvokedCompat {
    fun register(view: View, onBack: () -> Unit): (() -> Unit)? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val dispatcher = runCatching {
            View::class.java.getMethod("findOnBackInvokedDispatcher").invoke(view)
        }.getOrNull() ?: return null
        return registerOnDispatcher(dispatcher, onBack)
    }

    fun register(activity: Activity, onBack: () -> Unit): (() -> Unit)? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val dispatcher = runCatching {
            Activity::class.java.getMethod("getOnBackInvokedDispatcher").invoke(activity)
        }.getOrNull() ?: return null
        return registerOnDispatcher(dispatcher, onBack)
    }

    private fun registerOnDispatcher(dispatcher: Any, onBack: () -> Unit): (() -> Unit)? {
        return runCatching {
            val callbackClass = Class.forName("android.window.OnBackInvokedCallback")
            val dispatcherClass = Class.forName("android.window.OnBackInvokedDispatcher")
            val callback = Proxy.newProxyInstance(
                callbackClass.classLoader,
                arrayOf(callbackClass),
            ) { _, method, _ ->
                if (method.name == "onBackInvoked") onBack()
                if (method.returnType == Boolean::class.javaPrimitiveType) false else null
            }
            val priority = dispatcherClass.getField("PRIORITY_OVERLAY").getInt(null)
            dispatcherClass
                .getMethod(
                    "registerOnBackInvokedCallback",
                    Int::class.javaPrimitiveType,
                    callbackClass,
                )
                .invoke(dispatcher, priority, callback)
            val unregister: () -> Unit = {
                runCatching {
                    dispatcherClass
                        .getMethod("unregisterOnBackInvokedCallback", callbackClass)
                        .invoke(dispatcher, callback)
                }
            }
            unregister
        }.getOrNull()
    }
}
