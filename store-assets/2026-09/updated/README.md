# Updated Play Store screenshots

Open [index.html](index.html) to switch between the refreshed generated direction
and the real-capture alternative. Earlier artwork remains one directory above.

- `phone/generated/01.png` through `04.png`: selected generated style, updated
  for the current app. Built-in imagegen was used; [exact prompts](generation-prompts.md)
  and original outputs are retained. Generated UI still has small differences in
  typography, spacing and die shading.
- `phone/real/01.png` through `04.png`: current real emulator captures with the
  existing HTML/CSS composition. No app pixels are redrawn.
- `raw/`: current light/dark, PiP, language-dialog and Spanish captures. The
  results came from normal random rolls, selected for a blue Yes/pink No pair.
- `layout-checks/`: current compact phone, regular phone and tablet captures,
  plus the five passing instrumentation results. These are real display overrides
  on one API 37 emulator, not five separate devices.
- `validation.json`: image dimensions, opaque RGB format, copy limits and links.

All eight phone exports are 1080 × 1920 opaque RGB PNGs. Generated originals are
941 × 1672 and are proportionally enlarged. Order: decision, themes, floating
answer, languages. Use the corresponding headline as short alt text; the language
image now shows a scrollable dialog rather than the previous long dropdown.

The listing text in [../copy/](../copy/) now says **Float answer**. No Play Console
changes were made. App fixes passed all 18 device tests, all five layout checks,
unit tests and debug lint (0 errors, 12 existing warnings).

Regenerate real exports from the repository root:

```powershell
.\store-assets\2026-09\scripts\render.ps1 -AssetRoot D:/github/yes-no-randomizer/store-assets/2026-09/updated -PipY 1104
.\store-assets\2026-09\scripts\export-assets.ps1 -AssetRoot D:/github/yes-no-randomizer/store-assets/2026-09/updated
python store-assets/2026-09/scripts/validate.py --updated
```

`capture-updated.py` collects new phone inputs. It restores display/theme settings
and returns the app to English. The floating-window crop in this capture is
`x=568, y=1104, width=476, height=476`; inspect its position after recapture.
