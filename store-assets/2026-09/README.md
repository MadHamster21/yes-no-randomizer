# Yes/No Randomizer — Play Store refresh

**September 26 update:** the app issues are fixed. Open the [refreshed gallery](updated/index.html)
for the selected generated style and real alternatives using the updated UI.
The assets below retain the original comparison.

Open [index.html](index.html) for the interactive side-by-side comparison. Select each feature or switch between generated and real-only views.

Quick downloads: [real screenshots](real-screenshots.zip), [generated alternatives](generated-screenshots.zip), [plain tablet screenshots](tablet-screenshots.zip). The [comparison sheet](review/comparison.png) shows all eight phone designs together.

## Deliverables

| Location | Contents |
|---|---|
| `phone/real/01.png`–`04.png` | Recommended: actual emulator captures with HTML/CSS headlines and framing. |
| `phone/generated/01.png`–`04.png` | AI-generated alternatives for visual comparison. These redraw some UI details. |
| `tablet/01.png`–`04.png` | Plain 1080 × 1920 tablet-sized emulator screenshots, no added headlines. |
| `raw/` | Original emulator evidence, including compact and landscape layouts. |
| `review/generated-originals/` | Original 941 × 1672 imagegen outputs, retained before proportional export to 1080 × 1920. |
| [listing-en-US.md](listing-en-US.md) | Proposed title, short/full description, and rationale. |
| `copy/` | Plain-text listing fields ready to paste into Play Console. |
| [audit.md](audit.md) | Listing review, emulator configurations, observed issues and recommendations. |
| [generation-prompts.md](generation-prompts.md) | Exact prompts used with the built-in imagegen tool. |

All final phone images and tablet exports are opaque, 24-bit RGB PNGs at 1080 × 1920. AI outputs are proportionally enlarged to that export size; the original generated images are retained. Real artwork uses uniformly scaled/cropped screenshots, not redrawn UI. Status/navigation bars are cropped out of promotional phone panels. The PiP panel is a crop of the actual Android floating window, paired with its matching full-screen result.

## Order and alt text

| Order | Headline | Alt text (under 140 characters) |
|---|---|---|
| 01 | Let the dice decide. | A pizza question, Decide button and blue Yes die in Yes/No Randomizer. |
| 02 | Light or dark. Your call. | Light and dark app themes shown side by side with blue Yes and pink No dice. |
| 03 | Keep your answer close. | The full app beside its floating answer window showing the same pizza question and No result. |
| 04 | Say it in your language. | The language picker beside the Spanish interface and its blue Sí result. |

Tablet alt text: 01 “Yes/No Randomizer in light mode on a tablet-sized screen.” 02 “Yes/No Randomizer in dark mode on a tablet-sized screen.” 03 “The tablet language menu with all 18 language options visible.” 04 “The Spanish app interface on a tablet-sized screen.”

## Regenerate

Run from the repository root on this Windows machine:

```powershell
.\store-assets\2026-09\scripts\render.ps1
.\store-assets\2026-09\scripts\export-assets.ps1
python store-assets/2026-09/scripts/validate.py
```

The renderer uses installed Chrome in headless mode and Segoe UI. `scripts/artwork.html?card=01` through `04` contains the editable designs. `scripts/capture.py` uses ADB to inspect controls, enter a question, tap a label or capture actual pixels from emulator-5554. It does not force answers; outcomes in captures came from normal random rolls.

Only one Android 17 emulator was used, with display overrides for six layout configurations. See the audit for limitations. The build passed; this asset task did not change the app source or publish to Play Console.
