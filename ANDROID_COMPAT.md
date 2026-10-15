# Android 兼容性：Android 12+ (API 31+)

本分支把运行下限从 Android 16（`minSdk` / `targetSdk` 36）降到 **Android 12（`minSdk` 31）**，
`targetSdk` 降到 **Android 14（API 34）**，`compileSdk` 保持 **37**。
没有删除任何功能：Full · HY-MT2 Q4、Lite、Online 三个 edition 走同一套代码路径，
高版本行为不变，低版本走 fallback。

| 配置 | 修改前 | 修改后 | 说明 |
| --- | --- | --- | --- |
| `minSdk` | 36 | **31**（Android 12） | 首选目标已达成，无需退到 34 |
| `targetSdk` | 36 | **34**（Android 14） | 见下方“为什么是 34” |
| `compileSdk` | 37 | **37**（不变） | androidx.core 1.19.0+ 要求以 API 37 编译；只决定“能编译什么 API”，不提高运行下限 |
| `:llama-android` `minSdk` | 35 | **31** | 原 35 只是跟随应用的 Android 16 基线，不是原生要求 |

## 为什么 minSdk 是 31，而不是更低

31 是代码中无条件使用的最低 API 的天然下限，再往下需要继续分支：

- `PendingIntent.FLAG_IMMUTABLE`（API 31）：通知 / Worker / Tile 的全部 `PendingIntent` 都用它。
- `Notification.Builder.setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)`（API 31）：捕获通知。
- `android:dataExtractionRules`（API 31）：`AndroidManifest.xml`。
- `WindowManager.maximumWindowMetrics` / `WindowInsets.Type`（API 30）、`getSystemService(Class)`（API 23）等其余调用均 ≤ 31。

降到 API 30 需要再分支以上三处，本次不做。

## 为什么 targetSdk 是 34，而不是保持 36

- 保留 Android 14 的行为（前台服务类型 + `FOREGROUND_SERVICE_*` 权限、`POST_NOTIFICATIONS`），应用正是按这套行为写的。
- 避开 `targetSdk` 35+ 的强制 edge-to-edge 与 16 KiB 页面大小强制要求。CI 已核验 16 KiB 对齐（见 CHANGELOG 2.5.0），所以以后把 `targetSdk` 升回 35/36 的主要工作是重新验证 Android 15+ 的 edge-to-edge 行为，而不是修原生库。
- **注意：** Google Play 要求新应用 / 更新 `targetSdk` 35+。本分支的发布路径是 GitHub Releases 侧载，所以 34 可行；如果以后要上 Play，必须把 `targetSdk` 升回 35+ 再发。

## API 降级点与低版本 fallback

| API | 引入版本 | 修改 | 低版本行为 |
| --- | --- | --- | --- |
| `MediaProjectionConfig.createConfigForDefaultDisplay()` + `createScreenCaptureIntent(config)` | 34 | `ProjectionPermissionActivity.createScreenCaptureIntentCompat()`：≥34 用带 Config 的形式，31–33 用无参 `createScreenCaptureIntent()` | 请求同一默认显示屏捕获；无参形式是 34 之前唯一的写法，功能等价 |
| `OnBackInvokedCallback` / `OnBackInvokedDispatcher` | 33 | 新增 `util/BackInvokedCompat.kt`，全部反射调用，`SDK_INT < 33` 直接返回 `null`；`OverlayController` 与 `SelectionGestureGuardActivity` 不再直接引用 `android.window.*` | 框选阶段的系统返回由已有的 `KEYCODE_BACK` 处理兜底（overlay `OnKeyListener`、guard Activity 的 `onKeyDown`/`onKeyUp`）；无预测返回动画 |
| `Activity.overrideActivityTransition` | 34 | `SelectionGestureGuardActivity.finishTransitionCompat()`：≥34 用新 API，31–33 用 `overridePendingTransition(0, 0)` | 同样是无动画关闭 |
| `Intent.getParcelableExtra(name, Class)` | 33 | `ScreenTranslationService.getProjectionDataExtra()`：≥33 用带 Class 的重载，31/32 用已弃用的无参重载 | 返回同一个投影 `Intent`，功能等价 |
| `POST_NOTIFICATIONS` 运行时权限 | 33 | `MainActivity.hasNotificationPermission()` / `requestNotificationPermission()` 与 `CaptureShortcutNotification.show()`：`< 33` 直接视为已授权，不发起权限请求 | 33 以下通知默认开启（用户仍可在设置里关）；不修这里的话，`checkSelfPermission` 会对不存在的权限返回 DENIED，把首页就绪流程卡在“请求通知”上 |
| 动态广播接收器 export 声明 | 34（`targetSdk` 34 要求） | `ScreenTranslationService` 的 `SCREEN_ON/OFF` 接收器改用 `ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)`（与 `MainActivity` 已有写法一致） | androidx 在 31/32 上退化为普通两参注册 |

## 无需改动、已确认兼容的点

- `MediaProjection.Callback.onCapturedContentResize / onCapturedContentVisibilityChanged`（API 34）：只是重写方法，31–33 上父类没有这两个回调、永远不会被调用，留着无害。
- `ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION / _DATA_SYNC`（API 29+）、三参 `startForeground(id, notification, type)`（API 29+）：31 可用。
- `WindowManager.maximumWindowMetrics`（API 30）、`WindowInsetsController`（API 30）、`TileService`（API 24+）、`MediaProjection` / `VirtualDisplay`（API 21+）：31 可用。
- `enableOnBackInvokedCallback`（manifest，API 33）：31/32 上系统忽略该属性。
- `FOREGROUND_SERVICE_MEDIA_PROJECTION` / `FOREGROUND_SERVICE_DATA_SYNC`（manifest 权限，API 34）：31–33 上系统忽略未知权限。
- `java.time.ZonedDateTime`（`OnlineHttpPolicy`，API 26+）：31 可用。
- 原生层：Lite 的 Bergamot 预编译 runner 按 `android-28` 构建（见 `app/src/lite/cpp/prebuilt-manifest.json`）；Full 的 `:llama-android` JNI 只用 `jni.h` + `android/log.h`，无高版本 NDK 调用；ONNX Runtime Android 1.29.0 的 `minSdk` 为 21。以上均覆盖 31。
- 依赖（androidx.core 1.19.0、activity-ktx 1.13、appcompat 1.8、material 1.14、work 2.11.2、OkHttp 5.4）：`minSdk` 均 ≤ 24。

## 低版本上已知未验证 / 不可用的功能

以下在 Android 12/13 上**尚未真机验证**，发版前必须在 12/13 真机或模拟器上跑一遍：

1. **全屏增量覆盖（Experimental）仍以 Android 16 为验收基线。** 区域框选模式是 12+ 的主路径；全屏模式的代码可编译、可启动，但其持续识别、温控与内存表现只在小米 15 Pro / Android 16 上验收过。
2. **MediaProjection 授权语义不同。** Android 14+ 每次新会话都要重新授权；14 以下系统可能复用已授予的授权、不再弹窗。代码的“一次服务生命周期 = 一次投影会话”规则在低版本上是更严的约束，安全但可能让用户多点一次。
3. **同一投影 token 在 14 以下可创建多个 VirtualDisplay**，代码仍只建一个（复用 + `resize`），行为更保守，不影响正确性。
4. **预测返回动画**在 31/32 上没有；返回行为本身由 `KEYCODE_BACK` 保证。
5. **厂商 ROM（HyperOS / MIUI 等）的悬浮窗、电池优化、后台启动限制**在 12/13 上的表现未验证；`VendorAdapter` 已有 `SDK_INT < R` 的分支，但 12/13 的厂商行为需真机确认。
6. **通知样式**：`POST_NOTIFICATIONS` 在 31/32 上无运行时授权，通知默认开启；用户在系统设置里关闭通知后，常驻通知入口不会出现——这是系统行为，不是 bug。
7. **CI 仍只跑 API 36 模拟器 instrumentation**（`.github/workflows/ci.yml` 未改）。建议后续增加 API 31 / 33 模拟器 job 再发版。

## 验证命令（在有 Android Studio / SDK 的机器上）

```bash
# Lite（不依赖 llama.cpp submodule，最容易先验证）
./gradlew :app:assembleLiteDebug

# Full（HY-MT2 Q4；需要先拉 submodule，见下）
git submodule update --init --recursive
./gradlew :app:assembleFullDebug

# 单元测试（三个 edition）
./gradlew :app:testLiteDebugUnitTest :app:testFullDebugUnitTest :app:testOnlineDebugUnitTest

# Lint（Release 会 abortOnError）
./gradlew :app:lintLiteRelease :app:lintFullRelease :app:lintOnlineRelease
```

本 VM 内未完成 Gradle 编译：VM 无 JDK / Android SDK 且空闲内存不足以跑 Gradle（见分支提交记录外的任务报告）。上述命令在开发机上跑通之前，不要声称 12+ 已验证通过。
