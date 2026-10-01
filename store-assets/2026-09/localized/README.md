# Localized Play Store upload kit

Open [the upload workspace](index.html) directly in Chrome or Edge. No server is
needed. Select a language to see its app name, short description, full description,
feature graphic, four phone images and four landscape tablet images on one page.
Every text field and image alt text has its own Copy button.

Use **Copy folder path**, then paste the path into the Windows file picker's
address bar in Play Console. Select the four numbered locale-specific files together. The
**Open folder** links show the folder in your browser; they do not launch Explorer.
Each image also has Open, Save and Copy file path actions.

Each language has a direct page at `locales/<locale>/index.html` and this layout:

```text
locales/en-US/
  index.html
  title.txt
  short-description.txt
  full-description.txt
  alt-text.json
  00-en-US-feature-1024x500.png                    1024 x 500
  phone/01-en-US-phone-1080x1920.png ... 04-...   1080 x 1920
  tablet-10-landscape/
    01-en-US-tablet-10in-landscape-1920x1080.png ... 04-...  1920 x 1080
```

Screenshot filenames follow `<order>-<locale>-<device>-<width>x<height>.png`.
The feature graphic uses `00-<locale>-feature-1024x500.png`. Each file is
therefore identifiable even when separated from its language folder.

[Download everything](all-locales.zip), or use the language ZIP links in the
workspace. **Extract the entire ZIP before opening its root `index.html`.** Both
the full ZIP and individual language ZIPs include a working offline page, text,
and images. Paths adapt to wherever you extract them. Individual ZIPs show only
their included language. Download buttons are hidden inside extracted packages.
ZIPs are reproducible local exports; the sources and PNGs are tracked in Git.

## Upload to each language listing

1. Copy App name, Short description and Full description into the matching fields.
2. Upload the `00-<locale>-feature-1024x500.png` file to the feature graphic field.
3. Select all four images in `phone/` for the phone screenshot field.
4. Select all four images in `tablet-10-landscape/` for the **10-inch tablet** field.
5. Copy each image's alt text where Play offers that field.
6. Preview before publishing. These tools make no Play Console changes.

The order is decision, themes, floating answer, and languages. The tablet cards
show real landscape tablet UI; they are not rotated phone screenshots.

The locale codes are en-US, es-ES, fr-FR, ar, es-419, de-DE, hi-IN, id, it-IT,
ja-JP, ko-KR, pl-PL, pt-PT, ru-RU, th, tr-TR, vi, and zh-CN. These match the
owner's stated 18 listings and the app's supported languages. Country-specific
custom store listings are a separate Play Console feature.

## Artwork and translations

Generated background masters preserve the approved blue/red style. All headline,
caption, feature-graphic and dice-face translations are editable HTML text layers
in [templates/artwork.html](templates/artwork.html), driven by [i18n.json](i18n.json).
Arabic text uses RTL layout; its feature illustration is mirrored to place the
headline on the right. The templates choose fonts for the required scripts and
fit longer translations without clipping.

Screens inside the artwork are real emulator captures of the current app, with
localized questions, controls and answers. Android system bars are cropped out;
app controls and answers are not painted over. The floating-answer panel comes
from an actual picture-in-picture window. Language-picker screens intentionally
show language names in their own scripts, as the app does.

Listing text is newly drafted for each language, with region-appropriate Spanish
and Portuguese wording. App button names are inserted from Android string
resources to match the captures. These are assistant-authored translations;
native-speaker editorial review has not been performed. Title translations are
suggestions and can be kept consistent with any existing localized brand name.

The app icon remains shared. The 10-inch tablet set is captured at 2560 x 1600
with density 320 (1280 x 800 dp before system insets), then composed into
1920 x 1080 landscape cards. System bars are cropped while preserving app content.
Portrait captures are rejected by both the capture fixture and artwork exporter.

## Regenerate

From the repository root, with Node 22+, Python 3, Chrome and the Android SDK:

```powershell
node store-assets/2026-09/localized/prepare.mjs
# Build debug + androidTest APKs first if app code or capture code changed.
node store-assets/2026-09/localized/capture.mjs
node store-assets/2026-09/localized/capture.mjs --tablet
node store-assets/2026-09/localized/render.mjs
node store-assets/2026-09/localized/render.mjs --tablet
python store-assets/2026-09/localized/package.py
```

`capture.mjs` and `render.mjs` accept locale codes to process selected languages.
Use `ANDROID_SERIAL`, `ADB` and `CHROME` environment variables to override local
tool defaults. Capturing uses the opt-in `StoreListingCapture` instrumentation
utility, real taps and random rolls. Each locale has an isolated test run; emulator
display, orientation, theme and language preferences are restored afterward.
The utility is skipped by normal test runs unless `storeLocales` is supplied.

Run the renderer without locale arguments for final packaging: this writes the
phone/feature report (90 images) and, with `--tablet`, the tablet report (72 images). Upload assets are opaque RGB PNGs below
15 MB each. [copy-validation.json](copy-validation.json),
[render-validation.json](render-validation.json) and [validation.json](validation.json)
record the text limits, browser layout checks and file integrity checks.
[gallery-validation.json](gallery-validation.json) records successful loading of
all nine graphics for each of the 18 language selections, direct language pages,
copy-button payloads, successful clipboard write requests, and narrow-screen layout.
Headless Chrome on this workstation returns empty clipboard reads, so browser
checks inspect the exact write requests instead of claiming clipboard read-back.
`render.mjs --check` runs the workspace checks without rerendering images.

Generated bitmap masters were made with the built-in imagegen tool, not the API
fallback. Exact prompts and reference roles are recorded in [generation-prompts.md](generation-prompts.md).

Android Studio's formatter is run on edited source files using the saved user
scheme. This installation formats Kotlin, JSON and HTML; it reports JavaScript
modules, CSS and Python files as unsupported rather than substituting another formatter.

Repository-only review sheets: [phone 1](review/1.png), [2](review/2.png),
[3](review/3.png); [tablet 1](review/tablet-1.png), [2](review/tablet-2.png),
[3](review/tablet-3.png). Developer templates and validation reports are in the
repository; they are not required by the extracted upload workspace.

## References

- [Google Play localization and fallback graphics](https://support.google.com/googleplay/android-developer/answer/9844778?hl=en)
- [Preview assets, localization and alt text](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en)
