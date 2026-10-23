# Release 3.4 (25)

The published baseline is 3.3 (24). The application is now configured as version
3.4, version code 25. Build and sign the Android App Bundle in Android Studio using
the existing Play upload key, then upload that bundle yourself.

## Play Console release notes

Open [release-notes-3.4.xml](../store-assets/2026-10/release-notes-3.4.xml) and copy
the entire file into Play Console's release notes field. It contains the same 18
locale tags, in the same order, as the accepted 3.3 template. Tags are on their own
lines. There is no XML declaration or outer document element: this is the Play
Console paste format, rather than a single-root XML document. Every locale is below
the [500 Unicode character limit](https://support.google.com/googleplay/android-developer/answer/9859348).

The notes cover the user-visible changes since 3.3:

- Google Translate question translation when changing language, with source-language
  identification and skipping translation when the language already matches.
- Original-question preservation and the Undo action; downloaded models support
  offline translation, and first-use model downloads require Wi-Fi.
- A compact, fixed-height translation toolbar, with localized tooltips and full
  status details, that avoids shifting the decision button and die.
- Correct Android locale tags for Latin American Spanish and Chinese.
- The enlarged/cropped die fix after keyboard or window resizing. Its regression
  was reproduced before the fix and passed afterward.

The Kotlin patch update from 2.4.20 to 2.4.21 and the supplied regression screenshot
are also retained in separate commits. Public notes omit the compiler detail.

## Listing and disclosure handoff

This is the first release with ML Kit translation. Before publishing it in Play
Console, review the actual SDK disclosures described in
[question-translation.md](question-translation.md#before-publishing-a-release-with-ml-kit).
Update the published privacy policy and Play Data safety answers for the SDKs.
The repository's [existing policy](../app/privacy_policy.txt) predates translation;
it does not describe ML Kit or language-model downloads. It has not been rewritten
or published as part of this version/notes preparation.

The localized store descriptions also predate translation. For example, the
[English listing](../store-assets/2026-09/localized/locales/en-US/full-description.txt)
currently says that no internet connection is required without explaining first-use
translation downloads. Keep the offline claim for random decisions; explain that
question translation uses Google Translate, requires initial Wi-Fi model downloads,
and then works offline with downloaded models. Apply the equivalent change in each
published listing. The older screenshot kit remains the previously published artwork.

These are Play Console/listing publication steps; this work does not upload a bundle
or publish a store listing.

## Verification

- The completed resize fix passed its before/after regression and all 17 selected
  emulator tests before this version-only preparation. There are no additional
  application behavior changes in the release preparation.
- All 10 unit tests, debug/release lint, and the minified unsigned release APK build
  passed with Kotlin 2.4.21. Lint reported zero errors and 12 existing warnings.
- The built APK manifest was checked directly: versionCode 25, versionName 3.4.
- All 18 release-note blocks were checked against the accepted locale list, with
  tags on separate lines and each entry below 500 Unicode characters including its
  surrounding line breaks.
