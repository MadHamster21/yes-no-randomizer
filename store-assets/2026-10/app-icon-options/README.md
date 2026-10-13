# App icon options — October 12, 2026

Open [index.html](index.html) for the four concepts, including 48px and 32px samples and circle and
rounded-square previews. Images were generated with the built-in imagegen tool, one original
generation per option. All four original square PNG masters are saved here at 1254×1254. Exact
creation prompts are in [generation-prompts.json](generation-prompts.json).

## What informed the designs

Reviewed the existing launcher icon, the English feature graphic, the current phone promotional
screenshot, current light/dark app captures, the localized listing description, and the
application's theme colors.

The app is a quick, offline, ad-free helper for small everyday choices. Its distinctive interaction
is a softly rounded, slightly angled 3D die that gives a random binary answer. A question is
optional, and the answer can float while the user multitasks. The listing and artwork are clean and
playful: electric blue, coral rose, ivory/icy blue, generous space, big simple forms, and restrained
3D polish.

The existing “y/n” icon is text-dependent and does not communicate that visual identity. The new
concepts translate the two outcomes into checkmark/rejection-cross symbols. Using both a symbol and
a color for each answer preserves differentiation beyond color alone. These are symbols rather than
alphabetic brand lettering; their exact cultural familiarity can still vary.

The icon needs to communicate the core decision function at a glance. Language flags, small UI
screenshots, theme controls, and floating-window controls would add details that are difficult to
read at launcher sizes. Conventional numbered dice could imply a generic dice game; the binary
symbols make the purpose more specific.

## Four directions

| Option                                 | Strength                                                                                | Tradeoff                                                                                    |
|----------------------------------------|-----------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| [1. Decision die](01-decision-die.png) | Recommended. One memorable object, both answers visible, closest to the actual app die. | More dimensional detail than the geometric alternative.                                     |
| [2. Answer pair](02-answer-pair.png)   | Closest to the feature graphic's two staggered blue/coral cubes.                        | Busier silhouette; can read as two buttons.                                                 |
| [3. Bold die](03-bold-die.png)         | Simpler geometric form and strong navy contrast for small launcher sizes.               | Less directly related to the soft 3D advertising style; generation includes subtle shading. |
| [4. Choice token](04-choice-token.png) | Clean, balanced binary-choice symbol with a circular silhouette.                        | Weaker connection to the app's die; may suggest a coin-flip app.                            |

My preference is option 1, followed by option 3. These are review masters. Circle previews are CSS
masks over the originals, not modified output files or a claim that adaptive Android icon layers
have been produced.

## Midjourney

Checked against official documentation on October 12, 2026: the current default is **V8.2**, and the
current Niji model is **Niji 7**. Niji focuses on anime/illustrative styles and has a cleaner,
flatter look. I would start with V8.2 for the satin die; Niji is a useful secondary experiment for
the geometric concept.

### Recommended prompt: option 1

```text
Android app icon for a multilingual random yes-or-no decision helper, one chunky softly rounded die in three-quarter view, equally visible royal blue and coral-red faces, bold white checkmark symbol on the blue face and bold white rejection cross symbol on the red face, slender ivory edges, clean playful premium satin 3D illustration, soft studio highlights, strong simple silhouette, centered with generous breathing room for launcher masks, pale icy-blue full-bleed square background, instantly readable at small size --ar 1:1 --v 8.2 --raw --s 75 --c 5 --no text, letters, words, numbers, typography, watermark, mockup
```

### Option 2

```text
Android app icon for a multilingual random yes-or-no decision helper, two chunky rounded satin 3D cubes staggered diagonally as one compact centered composition, royal blue cube with a bold white checkmark and coral-red cube with a bold white rejection cross, both symbols fully visible, clean playful premium design, soft restrained highlights, generous breathing room for launcher masks, pale icy-blue full-bleed square background, bold simple shapes readable at small size --ar 1:1 --v 8.2 --raw --s 75 --c 5 --no text, letters, words, numbers, typography, watermark, mockup
```

### Option 3 — try Niji here

```text
Minimal geometric Android app icon for a multilingual random yes-or-no decision helper, one isometric rounded die, solid royal blue left face with a bold ivory checkmark, solid coral-red right face with a bold ivory rejection cross, small ivory top plane, crisp flat shapes, consistent geometry, wide symbol strokes, dark navy full-bleed square background, centered with generous breathing room for launcher masks, strong silhouette readable at 32 pixels --ar 1:1 --niji 7 --raw --s 50 --c 5 --no text, letters, words, numbers, typography, watermark, mockup
```

For a general-model comparison, replace `--niji 7` with `--v 8.2`; use only one model selector per
prompt.

### Option 4

```text
Android app icon for a multilingual binary decision helper, one chunky circular satin-resin token viewed almost front-on, equal royal blue and coral-red halves divided by a thin ivory seam, bold white checkmark in blue half and bold white rejection cross in red half, clean playful low-depth 3D illustration, precise balanced composition, pale icy-blue full-bleed square background, generous breathing room for launcher masks, readable at small size --ar 1:1 --v 8.2 --raw --s 75 --c 5 --no text, letters, words, numbers, typography, watermark, mockup
```

### Parameter choices

- `--ar 1:1`: square canvas.
- `--raw`: reduces default styling so the specific design has more influence.
- `--s 75` (or 50 for Niji): restrained stylization; this value is an art-direction choice, not an
  icon-specific requirement.
- `--c 5`: slight exploratory variation. Set `--c 0` when refining a preferred composition.
- `--no ...`: discourage generated lettering and mockup scenery.
- V8.2 does not support the quality parameter; avoid adding `--q 2` or `--q 4`. HD is optional for
  higher-resolution V8.2 generations.
- Turn off Personalization/Moodboards for the first controlled comparison so your saved preferences
  do not steer the brand direction unexpectedly.
- Generate one icon per image. Evaluate the results at 48px and under a circle mask, rather than
  judging only the enlarged render.

For stronger continuity, upload the existing feature graphic as a Style Reference. State explicitly
that the faces use symbols. The exact built-in results shown here are independent generations;
Midjourney will make its own interpretations.

## Production export after choosing

Google Play listing icons and Android adaptive launcher icons are separate assets. The current files
are original high-resolution concept masters, not Play-upload-size exports or installed launcher
resources.

The Play listing export should be a 512×512 sRGB 32-bit PNG below 1024KB, with a full square
background. Google Play adds the outside mask and drop shadow. Lighting and shadows within the
illustration are allowed. The chosen launcher icon should also be prepared as adaptive
foreground/background layers with an appropriate monochrome variant, and checked in launcher masks.

## Sources

- [Midjourney versions and compatibility](https://docs.midjourney.com/hc/en-us/articles/32199405667853-Version)
- [Midjourney Raw](https://docs.midjourney.com/hc/en-us/articles/32634113811853-Raw)
- [Midjourney Stylize](https://docs.midjourney.com/hc/en-us/articles/32196176868109-Stylize)
- [Midjourney No parameter](https://docs.midjourney.com/hc/en-us/articles/32173351982093-No)
- [Niji 7 prompting guide](https://nijijourney.com/blog/niji-7-prompting)
- [Google Play icon specifications](https://developer.android.com/distribute/google-play/resources/icon-design-specifications)
