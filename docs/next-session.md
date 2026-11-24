# Next session

Originally saved October 23, 2026; updated November 22 for release preparation.

## Current release preparation

The previous release bundle is 3.5 (26). The user authorized the next version on
November 22; the current version is **3.6 (27)**. The release includes automatic
selection from the full system language preference list, regional matching,
preserved manual language choices, and updated build dependencies. The app now
compiles against SDK 37.1 as required by the updated Compose libraries.

Publication status as of November 23: the owner reports that version 27 was
rejected for Data safety, specifically an undeclared Email Address finding. The
older release remains available according to Google's email. Data safety was
set to no collection/sharing, which does not cover ML Kit's documented metrics.
The specific email transmission has not been identified; no network-payload
audit or appeal has been completed. See [data-safety-review.txt](data-safety-review.txt).
The proposed policy at `app/privacy_policy.proposed.txt` is a marked review draft;
the existing policy has not been replaced or published. The current public-policy
URL, declared target age groups, and proposed support-retention practice still
need confirmation. Keep version 3.6 (27) while this review is in progress.

Copy the entire
[18-language release notes file](../store-assets/2026-11/release-notes-3.6.xml)
into Play Console. It uses the accepted locale-tag fragment format, with no outer
root or XML declaration; all blocks fit within 500 characters. Build and sign a
fresh AAB in Android Studio using the existing Play upload key.

Release preparation checks passed: Android Studio formatting, the minified
unsigned release APK build, and release lint (zero errors, ten existing warnings).
The built APK declares version 3.6, code 27. All 18 UTF-8 release-note blocks were
checked for exact locale order, Play Console fragment formatting, and the
500-character limit; the longest is 379 characters including surrounding newlines.

The previous release's changes and handoff remain in
[release-3.5.md](release-3.5.md).

1. **Decide! button typography — implemented October 24.** Replaced the 14 sp
   small-button label with Material medium sizing: 16 sp text, 24 sp line height,
   a 56 dp minimum button height, and 16 dp vertical padding. The button can grow
   with accessibility text instead of clipping it. Details are in
   [app-quality.md](app-quality.md#decide-button-typography).
2. **Touch and swipe dice rotation — implemented October 24.** Agreed on direct
   manipulation, bounded momentum, catching and redirecting, upright settling,
   and matching floating answers. The user explicitly wants to be able to
   "cheat": gesture motion determines the result; Decide! stays random. Details
   and validation are in [dice-gestures.md](dice-gestures.md).

## Earlier session context

- Translation fixes and controls are committed and pushed through `1a564c9`.
- Version was kept at **3.4 (25)** during that work at the user's request.
- The separate **Yes/No (Test)** installation on the connected phone contains
  the working optimized build. The Play-installed app and its data were preserved.
- Verification: 14 unit tests and 23 targeted phone tests passed; optimized
  translation and the final controls were also reviewed directly on the phone.
- Details of the completed work are in [question-translation.md](question-translation.md).
