# Touch and swipe dice rotation

Agreed October 24, 2026: gestures behave like an inertial toy. A user may deliberately
turn the cube onto Yes or No. The Decide! button and the floating window's New answer
action continue to choose randomly.

## Interaction

- A drag starting in the die's view follows the finger horizontally, vertically,
  diagonally, and through direction changes or circles. Incremental turns use
  screen axes, so turning the cube over does not reverse its controls.
- Touch slop filters accidental movements. Tapping an idle cube leaves the answer
  alone. Dragging elsewhere still scrolls the page.
- Touching a moving cube catches it immediately. Hold it, redirect it, or release
  it gently to settle on the currently visible face. Additional fingers do not
  pinch, twist, or take over the original finger's gesture.
- Release velocity is measured from recent pointer events. Holding before release
  removes momentum, even after a long drag. There is no random outcome selection
  in the gesture path.
- Momentum follows a damped rotation with a maximum initial angular speed of
  2160 degrees/second. Damping decreases for stronger flicks: gentle releases stay
  quick, while strong flicks coast for up to 2760 ms. Upright alignment takes
  another 240 ms, for a three-second maximum at normal animation speed. Faster
  swipes cannot extend that cap. This replaces the initial, much shorter slowdown
  following the user's physical-phone feedback. An intermediate five-second
  build felt too long; the three-second setting sits between the two trials.
- The face whose outward normal is closest to the camera at the end of the coast
  determines the answer. A short correction aligns that same face to one of the
  existing canonical landings. The renderer retains the 15-degree resting tilt.
- Text can tilt or turn upside down while the cube tumbles; the settled answer is
  upright. Finger motion does not directly add a separate screen-plane twist.
- A completed gesture commits the answer and increments its generation, including
  repeated Yes/No results. The floating answer uses this shared state. Decide! and
  Float answer are disabled while the die is held or moving.
- Landing requests a subtle platform haptic tick without bypassing the user's
  feedback preference. PiP's immediate New answer action does not vibrate.

## Lifecycle and accessibility

The existing Decide! button remains the accessible alternative to gestures.
Animations use Compose's duration-scale support, including Remove animations.
Physics determines the same landing regardless of the animation-duration setting.

Canceling a gesture settles it without adding momentum. Backgrounding commits its
predicted landing, or the nearest visible face if still held. Saving state uses
the same result, so recreation during a gesture restores an upright answer instead
of a partially turned cube. Existing question, language, and theme preservation
continues to use the same saved-state paths.

Button rolls still select and save their result before animation, preserving
their existing interruption behavior. Gesture results remain unchanged while held
or coasting and are committed when they land. An animation-generation guard prevents
a canceled roll from overwriting a subsequent catch and drag.

The renderer receives an immutable quaternion-derived matrix. Its original Euler
rotation API remains available for the existing renderer checks. EGL lifecycle,
buffer resizing, colors, geometry, and translated face textures are unchanged.

## Live language and theme changes

The activity handles `locale`, `layoutDirection`, and `uiMode` configuration
changes in place, following [Android's configuration guidance](https://developer.android.com/guide/topics/resources/runtime-changes).
Language and theme changes therefore retain the current quaternion, animation,
TextureView, EGL context, geometry, and shader. The existing saved-state behavior
still applies when Android actually destroys the activity or process.

Face lettering is resolved on the UI thread for the app's current locale and
passed to the renderer as an immutable pair. Changing those labels or the theme
repaints the atlas with `texSubImage2D`, reusing the texture allocation. Identical
labels/colors do not trigger an upload. Older Android versions explicitly notify
the existing view tree after the legacy Resources locale update.

`DiceConfigurationTest` checks every language-picker entry, exact settled matrix
preservation, actual rendered glyph changes, language/RTL changes during a roll,
and a theme change during a roll. It verifies identity of the activity, GL view,
EGL context, cube, vertex buffer, shader, and texture allocation. All three tests,
the 23 JVM tests, app/test builds and lint passed on the API 37 emulator.

## Verification

- Pure motion tests cover canonical normals and matrices, controlled face choice,
  movement after turning over, circular/reversed drags, momentum direction and
  outcome, speed/time caps, holding before release, and upright correction.
- Gesture instrumentation covers tiny touches, deliberately choosing Yes, actual
  rendered face colors, floating answer content, catching/redirecting, all eight
  swipe directions, circles, cancellation/backgrounding, state restoration during
  coasting, and scrolling outside the die.
- The PiP test checks a swipe result in the actual system floating window and
  verifies the same result after returning. Its existing button test now commits
  the TextField edit before freezing test frames; this matches the continuity
  test's existing synchronization. PiP checks advance Compose's clock after the
  activity pauses and open the system menu with an injected touch on the floating
  window, rather than an accessibility click on the app's content root.
  Like StoreListingCapture, they wait for the native transition, then flush the
  Compose clock and layout queue. All three PiP tests passed, including the
  system New answer action and return to the same gesture result.

Debug app/test builds, the optimized diagnostic build, and lint passed (0 errors,
10 existing warnings). All 23 JVM tests passed, including nine motion tests.
The seven gesture tests passed on both the phone and 1280 x 800 dp landscape
tablet viewport; the tablet adaptive-layout check passed too. Existing continuity,
saved-state, palette, background/resume, buffer-resize, and adaptive-layout checks
also passed. The optimized APK was visually checked during a drag and after landing;
with animator duration scale zero it settled immediately with both buttons enabled.
Temporary emulator display, rotation, and animator settings were restored.

The final three-second retuning passed all 23 JVM tests and the seven phone-viewport
gesture checks, plus debug/optimized builds and lint. The updated optimized
Yes/No (Test) APK was installed on the connected Pixel 9 Pro XL for hands-on review.

Version remains 3.4 (25). Local captures and runner output are saved in the ignored
`build/dice-gestures/` folder.
