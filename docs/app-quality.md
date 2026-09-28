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
- The dice uses render-on-demand through a `TextureView`, so its OpenGL output scales with the app window. A dedicated GL thread resumes at lifecycle start and releases its EGL context and surface at stop. Surface recreation rebuilds the cube and texture; composition disposal also stops the worker after texture cleanup. See [TextureView](https://developer.android.com/reference/android/view/TextureView) and [EGL14](https://developer.android.com/reference/android/opengl/EGL14).
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

### Play Console recommendations for version 3.0 (21)

The app already calls `enableEdgeToEdge()` and handles safe drawing insets as
described above. The general edge-to-edge recommendation does not by itself
identify a layout failure. Keep the device checks above when releasing changes.

The deprecated API call sites reported for this release were traced using its
exact `app/release/mapping.txt` and the DEX inside its AAB:

| Play call site | Original implementation |
| --- | --- |
| `zt.b` | `androidx.activity.EdgeToEdgeApi23.setUp` |
| `au.b` | `androidx.activity.EdgeToEdgeApi26.setUp` |
| `cu.b` | `androidx.activity.EdgeToEdgeApi29.setUp` |
| `eu.b` | `androidx.activity.EdgeToEdgeApi35.setUp` |
| `a1.l` | R8-generated helper that sets `layoutInDisplayCutoutMode` to `SHORT_EDGES` |

Inspection of the installed Activity 1.13.0 source confirms that these calls
belong to AndroidX's supported edge-to-edge implementation. The older paths set
system bar colors for compatibility; even the API 35 path explicitly sets both
bar colors to transparent. `SHORT_EDGES` is used on API 28-29; API 30+ uses
`ALWAYS` instead. The helper's generated class name is not evidence of an
accessibility bug. Obfuscated names can change with every build.

Activity 1.13.0 is the latest stable version listed in the
[AndroidX release notes](https://developer.android.com/jetpack/androidx/releases/activity)
at this review. No app runtime change is required for these reported call sites.
Keep the recommended AndroidX helper and revisit its implementation when updating
dependencies; do not remove compatibility behavior merely to hide the warning.
See [Android 15 window behavior changes](https://developer.android.com/about/versions/15/behavior-changes-15#edge-to-edge).

## Picture-in-picture

After a roll finishes, **Float answer** pins the answer and optional
question in a compact, square window. Use the system PiP controls to return to
the app or close the window. Entry is explicit: pressing Home does not pin an
answer automatically. The button is hidden on devices without PiP support
(including Android 7.x), and unsuccessful entry shows a localized explanation.
The new strings cover all bundled languages.

Tap the PiP window to reveal Android's controls, then tap the circular-arrow
**New Answer** action to generate another answer without leaving PiP. Android
controls the action's position and visibility. New Answer updates the shared
answer and cube landing immediately, preserving the question and the result
when returning to the app. A new answer can randomly be the same as the previous
one. The
receiver is private to the app and is unregistered when the screen is disposed.
Its action icon comes from Google's Compose Material `Icons.Default.Refresh`,
rendered to the native bitmap required by Android's PiP API; there is no custom
SVG drawable to maintain.
See [Android's remote action guidance](https://developer.android.com/develop/ui/compose/system/pip-remote-actions).

On supported devices, Float answer stays in the layout and is disabled until a roll
finishes, so completing or starting another roll does not shift the controls.
The cube is the only visible answer in the full app; the separate answer text
appears only in PiP, using the same primary (blue Yes) and tertiary (red No)
colors as the cube in both themes. The cube supplies the PiP transition bounds,
and the screen retains its scroll position when returning from PiP.

Landing rotations and texture labels share the `DiceFace` definitions. The
camera looks from negative Z, so the front lands at Y=180 degrees, the back at
Y=0, the left at Y=-90, and the right at Y=90. Top/bottom land at X=-90/+90.
This corrects the previous front/back and left/right answer reversals.

The activity declares PiP support and handles its size/configuration changes,
preserving the current question and dice state during entry and exit. Editing
controls and the GL dice are removed from composition in PiP; the compact view
shows a completed result and needs no background rendering. During entry, only
the theme background is composed; the answer appears centered after the system's
`onPictureInPictureModeChanged` callback signals that entry has finished. This
prevents the text from jumping between the full-screen crop and final PiP bounds.
Failed entry restores the normal screen. Android 12+ uses non-seamless resizing for
text. See [Android's PiP guidance](https://developer.android.com/develop/ui/views/picture-in-picture).

`PictureInPictureTest` checks that pinning is available only after a completed
roll, enters actual system PiP, verifies the compact content, and returns to the
same question and answer. Run it with `./gradlew connectedDebugAndroidTest` on a
PiP-capable device with PiP allowed for this app. Before publishing, also check
Android 8–11, Android 12+, and a device without PiP support; test empty and long
questions, resizing, closing/reopening, and denying PiP in system settings.

Regression coverage also checks stable control bounds over two rolls and no
duplicate result text, samples the actual GL surface at all six landing
rotations, and checks rendered PiP text colors for Yes/No in light/dark themes.

Verified on the Pixel 9 Pro XL Android 17 (API 37) emulator: all nine device tests
passed, including all six rendered landing faces, stable layout across rolls,
PiP colors in both themes, and PiP entry/content/return. Compact content is
checked through the system accessibility window because Compose's test API
excludes paused PiP windows. The return test waits for the system entry animation
to settle. Unit tests, debug APK compilation, and debug lint also passed (lint
retains the 10 existing resource/locale/icon warnings).

## Decision continuity

The question and selected Yes/No outcome now use saved instance state. Rotation,
theme changes, language changes and Activity recreation keep both values.
The saved outcome is a small stable value, not a resource ID or animation object;
on restoration the die lands on a matching face using the current language and
theme. If recreation interrupts a roll, it settles that roll's selected answer
and enables the controls. This does not save an answer history or persist a
decision after the user explicitly ends the task.

`DecisionContinuityTest` exercises real Activity recreation, recreation during a
roll, rotation in both directions, and English/Spanish language changes.
`DiceStateRestorationTest` verifies saved Yes and No outcomes restore with matching
landing rotations. All 16 device tests passed on the API 37 emulator after this
change. See [Compose state saving](https://developer.android.com/develop/ui/compose/state-saving).

## Adaptive decision layout

The form is capped at 480 dp on large windows. Wide windows place the form and
die side by side; compact windows use a smaller title and scale the die to the
available height. Both arrangements retain scrolling for the keyboard and large
accessibility text. The question and selected outcome stay above the layout
branches, so changing arrangements preserves the decision.

`AdaptiveLayoutTest` checks that the form, die, and floating-answer control fit
inside the safe area and that wide layouts separate the form from the die.
`python scripts/verify-layouts.py` runs it on five emulator viewports and restores
the original display settings. All five passed on API 37: 360×640, 640×360,
432×768, 720×1280, and 1280×720 dp. Screenshots were also visually reviewed.

## Language and floating-answer controls

The language control has a standard button-sized touch target and opens a
bounded, scrollable dialog. Radio-button semantics identify the current choice;
the list opens at that choice, and Cancel/back dismiss it without changing the
language. The title and the clearer **Float answer** label are translated in all
18 bundled locales. This control still uses Android picture-in-picture.

The continuity tests now also switch the actual system light/dark setting and
restore it afterward. Language selection is exercised through the dialog,
including scrolling back to English while preserving the question and outcome.

Final UI verification: all 18 instrumentation tests passed on the API 37 emulator
through AndroidJUnitRunner, including actual PiP entry/action/return and rendered
die faces. Debug APKs, unit tests, and lint passed; lint has 0 errors and 12 existing
warnings. The Gradle connected-test runner stalled during device connection, so
the same installed instrumentation suite was run directly with `adb shell am
instrument -w com.sblashkov.yesnorandomizer.test/androidx.test.runner.AndroidJUnitRunner`.

## Dice perimeter during task resizing

The separate `GLSurfaceView` backing layer showed a faint rectangular edge while
Android shrank the app into the recent-apps overview. Matching its surface
format alone did not remove the edge. `DiceTextureView` now presents the existing
OpenGL renderer inside the app window so both scale together. Initial theme
colors and the saved rotation are supplied before the first GL frame, including
when returning from Float answer. The rendering still pauses while hidden.

[Before/after emulator captures](verification/dice-resize/README.md) show light
and dark mode during the actual overview animation. `DiceSurfaceRestoreTest`
also samples the composed display during stop/start, Home/restore, and PiP
return, then checks all four backing-region edges at three fractional scales.
The test waits for PiP entry to settle before restoring: Android can ignore a
restart while its entry animation is still running.

Verification on the API 37 emulator: all 19 instrumentation tests passed via
AndroidJUnitRunner, including rotation/language/theme continuity, the rendered
faces, and actual PiP entry/action/return. Debug APKs built successfully;
`lintDebug` reported 0 errors and 11 warnings. The deleted example tests account
for the lower baseline test count; the two surface regression tests are new.
