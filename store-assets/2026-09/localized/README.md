# Localized Play Store upload kit

Open [the gallery](index.html) to review any of the 18 language sets. Download
[all locales](all-locales.zip), or use the individual ZIP links in the gallery.
ZIP files are reproducible local exports; the source files and PNGs are in Git.
The combined ZIP contains one folder per locale at its top level. Gallery,
template and validation links in this guide refer to the repository copy;
the ZIP contains the upload files and this guide.
For a quick visual comparison, see the three contact sheets:
[languages 1–6](review/1.png), [7–12](review/2.png), and [13–18](review/3.png).

## Upload to each language listing

Choose the matching language in Play Console and upload the contents of its
`locales/<locale>/` folder (or extract `bundles/<locale>.zip`):

1. Paste `title.txt`, `short-description.txt`, and `full-description.txt` into
   their corresponding listing fields.
2. Upload `feature-graphic.png` to the feature graphic field: 1024 x 500 px.
3. Replace the phone screenshots with `phone/01.png` through `04.png`, in that
   order: decision, themes, floating answer, and languages. Each is 1080 x 1920 px.
4. Use `alt-text.json` for the image descriptions where Play offers an alt-text field.
5. Preview the language listing before publishing. No Play Console changes have
   been made by these tools.

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

The app icon remains shared. This kit contains phone graphics; the earlier tablet
screenshots are not presented as newly localized tablet assets.

## Regenerate

From the repository root, with Node 22+, Python 3, Chrome and the Android SDK:

```powershell
node store-assets/2026-09/localized/prepare.mjs
# Build debug + androidTest APKs first if app code or capture code changed.
node store-assets/2026-09/localized/capture.mjs
node store-assets/2026-09/localized/render.mjs
python store-assets/2026-09/localized/package.py
```

`capture.mjs` and `render.mjs` accept locale codes to process selected languages.
Use `ANDROID_SERIAL`, `ADB` and `CHROME` environment variables to override local
tool defaults. Capturing uses the opt-in `StoreListingCapture` instrumentation
utility, real taps and random rolls. Each locale has an isolated test run; emulator
display, orientation, theme and language preferences are restored afterward.
The utility is skipped by normal test runs unless `storeLocales` is supplied.

Run the renderer without locale arguments for final packaging: this writes the
complete 90-image text-fitting report. Upload assets are opaque RGB PNGs below
15 MB each. [copy-validation.json](copy-validation.json),
[render-validation.json](render-validation.json) and [validation.json](validation.json)
record the text limits, browser layout checks and file integrity checks.
[gallery-validation.json](gallery-validation.json) records successful loading of
all five graphics for each of the gallery's 18 language selections.

Generated bitmap masters were made with the built-in imagegen tool, not the API
fallback. Exact prompts and reference roles are recorded in [generation-prompts.md](generation-prompts.md).

Android Studio's formatter is run on edited source files using the saved user
scheme. This installation formats Kotlin, JSON and HTML; it reports JavaScript
modules and Python files as unsupported rather than substituting another formatter.

## References

- [Google Play localization and fallback graphics](https://support.google.com/googleplay/android-developer/answer/9844778?hl=en)
- [Preview assets, localization and alt text](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en)
