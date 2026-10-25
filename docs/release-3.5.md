# Release 3.5 (26)

The published baseline is **3.4 (25)**, as confirmed by the user. The saved signed
bundle at `app/release/app-release.aab` also declares version 3.4, version code 25.
The application is now configured as **3.5 (26)**. Build and sign a fresh Android
App Bundle in Android Studio with the existing Play upload key for this release.
The saved signed bundle was not rebuilt during this preparation.

## Play Console release notes

Copy the entire contents of
[release-notes-3.5.xml](../store-assets/2026-10/release-notes-3.5.xml) into Play
Console's release notes field. The file uses the previously accepted 18 locale
tags, in the same order, with each tag on its own line. It intentionally has no
XML declaration or enclosing root element, matching the Play Console paste format.
Every block is within 500 Unicode characters, including surrounding line breaks.

## Changes since 3.4

- Swipe the die in any direction, catch it while spinning, or steer it toward a
  preferred answer. Release momentum determines the gesture result; Decide! and
  the floating window's New answer action still choose a random result. Strong
  flicks coast and settle within three seconds, with quicker gentle releases.
- The die settles into the familiar upright perspective, gives subtle landing
  feedback, and updates the floating answer to the same result.
- Language, layout-direction, and theme changes preserve the live die and its
  ongoing rotation, updating its labels and colors without rebuilding it.
- The Android launcher icon now matches the approved Play icon, including
  adaptive, legacy, and themed monochrome variants without Latin letters.
- The Decide! label uses 16 sp text in a button with a 56 dp minimum height that
  can grow with accessibility text. Dark-mode die edges use subtle charcoal to
  remain visible against the background.
- Fixed ML Kit initialization in optimized releases, translation retry failures,
  and stale errors when returning to the question's original language.
- Added explicit language-pack download consent, compact retry controls, and
  localized Translate / Show original links. Choosing Translate re-enables
  translation; declining a download does not start translation automatically.

## Verification

- Ran Android Studio's formatter on the changed Gradle source with the user's
  code-style settings.
- `assembleRelease` and `lintRelease` passed. Lint reports zero errors and ten
  existing warnings. Checked the generated unsigned APK directly with `aapt2`:
  production package, versionCode 26, versionName 3.5.
- Checked strict UTF-8, all 18 expected locale tags and their order, separate tag
  lines, nonempty content, and the 500-character limit. Block lengths, including
  surrounding newlines, range from 150 to 471 characters.
- This preparation changes only the version and release documentation. Earlier
  behavior validation is recorded in [dice-gestures.md](dice-gestures.md),
  [app-quality.md](app-quality.md), and
  [question-translation.md](question-translation.md).
