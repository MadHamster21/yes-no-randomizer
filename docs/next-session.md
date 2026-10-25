# Next session

Originally saved October 23, 2026; updated October 25 for release preparation.

## Current release preparation

The published baseline is 3.4 (25). The user authorized the version increase on
October 25; the current version is **3.5 (26)**. The release includes the completed
translation fixes, launcher icon, button typography, swipe controls, live
language/theme updates, and dark-mode edge visibility. See
[release-3.5.md](release-3.5.md) for the Play Console notes and build handoff.

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
