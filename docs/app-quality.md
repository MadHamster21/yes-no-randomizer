# App quality and release verification

Reviewed against Google's [August 2026 app quality announcement](https://android-developers.googleblog.com/2026/08/app-quality-memory-optimization-secure-onboarding.html).

## Applicability

- Memory and DEX requirements start in February 2027. The [technical requirements](https://support.google.com/googleplay/android-developer/answer/17492799) assess memory by process state and RAM tier. DEX thresholds require at least 25% shrinking, optimization, and obfuscation for apps with more than 10 MB of DEX code.
- Zero-Tap Sign-In starts in April 2027 for apps with sign-in. This project has no accounts or authentication, so Restore Credentials does not apply. If accounts are introduced, revisit that requirement.
- Language selection is the only persisted app preference. [Auto Backup](https://developer.android.com/identity/data/autobackup) is already enabled in the manifest; the existing backup rules allow preference backup and device transfer. Questions and answers are currently transient UI state.

## Implementation

- Release builds enable R8 and resource shrinking using the optimizing default rules. The full-mode keep-rule compatibility opt-out has been removed. Keep project rules narrow; do not add package-wide keep rules or disable shrinking, optimization, or obfuscation to silence failures. See [Android's R8 guidance](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization).
- Compose compilation uses the Kotlin Compose compiler Gradle plugin. The legacy compiler artifact is no longer an app runtime dependency.
- Language splitting is disabled because the offline picker can select any translation, including a restored preference that differs from the device language. ABI and density splitting retain their defaults. This small translation set does not need a download library; see [Android's guidance for in-app language pickers](https://developer.android.com/guide/app-bundle/configure-base).
- The dice uses render-on-demand. Its GL view resumes at lifecycle start and pauses at stop or composition disposal, with EGL context preservation disabled so GPU resources can be released while hidden. Surface recreation rebuilds the cube and texture. See [GLSurfaceView lifecycle guidance](https://developer.android.com/reference/android/opengl/GLSurfaceView).
- Rotation updates run on the GL thread. Color changes are applied during drawing, when an EGL context is current, and unchanged colors do not regenerate the texture. The renderer reuses its matrix buffer and deletes shader objects after linking.
- The existing 512 x 512 ARGB texture upload uses a temporary bitmap (1 MiB of pixel data), recycled after upload, including when upload throws. This is not a measurement of total graphics or process memory.

## Before publishing

1. Use the Gradle wrapper with a compatible JDK (the Android Studio bundled JDK is suitable):

   ```powershell
   .\gradlew.bat test lintRelease bundleRelease
   ```

   The bundle is `app/build/outputs/bundle/release/app-release.aab`. Retain `app/build/outputs/mapping/release/` with the release artifacts for crash retracing and optimization review. This repository does not configure release signing.

2. Test a signed, optimized release on a device. Check cold launch, repeated rolls, background/foreground transitions while idle and during a roll, screen locking, rotation, theme changes, and language changes. Verify that the cube returns with readable faces, the result still matches the roll, and the button becomes enabled after animation.

   With an API 26+ device attached, run `./gradlew connectedDebugAndroidTest`. `DiceLifecycleTest` samples the GL surface to check that a textured cube renders after repeated stop/start transitions. This debug regression test supplements the optimized release smoke test.

3. Profile on a lower-RAM device and a representative modern device. Compare cold launch, repeated rolls, and repeated background/foreground cycles using Android Studio's Memory Profiler. Look for retained activities, bitmap allocations, and growing native/graphics memory. Capture snapshots while visible and after pressing Home:

   ```text
   adb shell dumpsys meminfo com.sblashkov.yesnorandomizer
   adb shell input keyevent KEYCODE_HOME
   adb shell dumpsys meminfo com.sblashkov.yesnorandomizer
   ```

   Allow the app to settle after each transition. A local snapshot is diagnostic; it does not establish compliance with Play's rolling percentile metrics.

4. Test cloud restore and device transfer with a non-default language selected. Confirm the first launch uses the saved language on Android 12 and earlier and Android 13+.

5. After uploading the signed AAB, inspect its DEX optimization percentages and split delivery in App Bundle Explorer. In Android vitals, review anonymous RSS + swap, bitmap memory, and out-of-memory terminations by RAM tier and app state. Enabling R8 alone does not prove the uploaded artifact meets the thresholds.

## Edge-to-edge

`MainActivity` calls `enableEdgeToEdge()` before composing its UI, including on
Android versions before edge-to-edge enforcement. The background fills the
window; a shared `safeDrawingPadding()` boundary protects the language picker,
question, button, and dice from system bars, display cutouts, and the keyboard.
The manifest uses `adjustResize` to support keyboard inset delivery. The main
content scrolls when the available height is too small, while the language picker
has its own space above it. See [Android's edge-to-edge setup guide](https://developer.android.com/develop/ui/compose/system/setup-e2e).

`EdgeToEdgeTest` checks window coverage, safe content bounds, keyboard access, and
scrolling to the dice in landscape. Before publishing, also check an older
Android version and Android 15+, gesture and three-button navigation, light and
dark themes, and display cutouts. Verify that system bar icons remain readable,
the question and button are reachable with the keyboard open, and the language
picker does not overlap the main content.

Verified on the Pixel 9 Pro XL Android 17 (API 37) emulator: all five device tests
passed with gesture navigation, and the three edge-to-edge tests also passed
with three-button navigation, dark mode, and the tall cutout overlay enabled.
Light and dark screenshots were visually checked. Older Android versions still
need a device smoke test.
