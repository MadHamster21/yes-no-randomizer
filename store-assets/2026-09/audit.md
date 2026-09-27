# Listing and screen review — September 23, 2026

## Listing findings

The [current Play listing](https://play.google.com/store/apps/details?id=com.sblashkov.yesnorandomizer) describes a generic random answer and repeats a download invitation. Its final sentence is awkward. It does not explain the language picker, light/dark appearance, optional question, or picture-in-picture behavior found in the repository build.

The first public screenshot was downloaded and visually inspected: [existing screenshot](review/current-play-01.png). It shows the older brown interface and a plain answer rather than the current blue/red die. The public page exposes six screenshot links; only the first was downloaded for visual comparison. Do not assume the installed production binary is identical to the repository build just because the version name matches.

Use the proposed copy in [listing-en-US.md](listing-en-US.md). Title stays recognizable; short description is 78 characters and the full description is 1,105 characters. Those fit Google's [30/80/4,000-character field limits](https://support.google.com/googleplay/android-developer/answer/9859152).

## What was actually run

- Built the current repository with `gradlew.bat assembleDebug`: passed.
- Source commit: `73116d7`. Captured debug APK SHA-256: `A311A622DCA2518694C2B2A1B2B360CFF373CE8BD38183E1EE5AFB36C5433CAF`.
- Installed that APK on the Pixel 9 Pro XL AVD, Android 17/API 37 system image, hardware GPU rendering.
- Used one running emulator with real Android display overrides, not resized screenshots, to inspect phone and tablet layouts. These checks do not certify separate tablet hardware or older Android releases.
- Native XL: 1344 × 2992 at 480 dpi, approximately 448 × 997 dp.
- Standard capture phone: 1080 × 1920 at 400 dpi, 432 × 768 dp.
- Compact phone: 720 × 1280 at 320 dpi, 360 × 640 dp; rotated to 640 × 360 dp landscape.
- Tablet-sized viewport: 1080 × 1920 at 240 dpi, 720 × 1280 dp; rotated to 1280 × 720 dp landscape.
- Exercised optional questions, repeated actual random rolls, English/Spanish selection, the language menu, theme changes, Minimize, and the real Android PiP window. Opened its system controls and observed the refresh action. Other bundled translations were inspected in the menu/resources, not all exercised independently.
- No app source changes or new automated regression tests were needed for this asset/design task. No production release was installed or tested.

## Screen differences and suggested app improvements

| Area | Observation and evidence | Suggested next change |
|---|---|---|
| Compact portrait | [360 × 640 dp](raw/compact-portrait.png): two-line app title; part of the die and Minimize lie below the first viewport. Scrolling is available. | Use a smaller heading, tighter spacing and a smaller die when vertical space is limited. |
| Compact landscape | [Initial view](raw/compact-landscape-top.png) shows question and button; [scrolled view](raw/compact-landscape-scrolled.png) reaches the die. | Place the form and die side by side in landscape. |
| Standard phone | [432 × 768 dp](raw/phone-light-roll-2.png): main flow fits, including Minimize. | Use this as the current listing's main phone representation. |
| XL phone | [Native XL](raw/phone-xl-result.png): more whitespace, controls fit. | Retain centered composition but tune vertical spacing to keep the question and outcome visually connected. |
| Tablet | [Portrait](raw/tablet-portrait-light.png) and [landscape](raw/tablet-landscape-light-clean.png): form controls grow very wide, while the die stays 300 dp. Landscape Minimize is below the initial viewport. | Cap content width around 480–560 dp and use two columns on wide screens. Tune with live captures rather than scaling a phone image. |
| Theme changes | [After switching theme](raw/phone-dark-result.png), the question persists but the completed result resets to the initial ellipsis. | Preserve the last completed answer across recreation; keep animation state transient. |
| Language picker | The menu is usable, but long and visually covers most of the phone screen. | Consider a clearly labeled, searchable language sheet if the language list grows. |
| Floating answer | [Real system PiP](raw/phone-pip-home.png) shows the question and matching answer; [system controls](raw/phone-pip-controls.png) expose refresh. | Consider renaming “Minimize” to “Float answer” in a later app update so the feature is easier to discover. |

The compact and wide-screen behavior follows the current fixed 48 dp side margins, 64 dp gap above the 300 dp die, full-width input/button, and single scrolling column in `MainActivity.kt` / `AnswerDice.kt`. These are recommendations, not changes included in this task.

## September 26 implementation update

The findings above are historical evidence. The app now saves the question and
outcome across rotation, theme/language changes and recreation during a roll;
adapts the form/die to compact and wide windows; caps large-screen form width;
uses a bounded language dialog; and labels PiP **Float answer** in all 18 locales.
All 18 instrumentation tests and five layout configurations passed. See the
[updated captures and generated artwork](updated/index.html). The generated
direction was selected by the user; the real-capture option remains available.

## Screenshot direction

Lead with one large real result so the core idea is legible at thumbnail size. Use paired images where a comparison has a purpose: themes, full-screen versus floating answer, and language picker versus translated screen. Keep the visual system consistent: blue, coral, navy, bold short headings, a few simple shapes.

Avoid dense emoji, giant decorative dice, fake device hardware, repeated generic slogans, and ranking/award claims. The app already provides a distinctive answer die and flag icons. The generated option adds atmosphere but redraws the UI; for example, card 02 introduces dropdown chevrons absent in the app and changes die proportions. The real option retains original captured UI, with only cropping, uniform scaling, framing and external text.

[Google's screenshot guidance](https://support.google.com/googleplay/android-developer/answer/9866151) supports actual captured UI with limited promotional typography. It recommends at least four 1080p images in 9:16/16:9 and brief taglines. It also recommends plain large-screen captures without added promotional text. The four tablet exports therefore contain the original in-app view only. Opaque RGB format and dimensions are checked locally; Google Play acceptance is not asserted.

## Publish sequence

1. Match these assets to the production build containing the photographed features.
2. Use `phone/real/01.png` through `04.png` in that order; supply the matching alt text in README.
3. Use the four plain images from `tablet/` for the relevant large-screen listing. These captures use a simulated 720 dp-wide viewport; verify on the tablet devices you support before claiming tablet optimization.
4. Paste the proposed English short/full descriptions. Localize the external promotional headlines along with the listing for other markets.
5. Compare acquisition results after enough traffic accumulates; no conversion improvement has been measured here.

This work produces reviewable local assets only; it does not publish or edit Play Console.
