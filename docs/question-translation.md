# Question translation

Selecting a language detects the language of the user's original question and
translates it when it differs from the selected language. Google ML Kit performs
both steps on the device. The UI language is never used as a guess for the question's
source language. The 18 picker entries correspond to 17 translation languages:
the two Spanish entries share the Spanish model; Chinese uses the Chinese model.

The app uses bundled ML Kit Language Identification 17.0.6 and on-device Translation
17.0.3. These support the existing minimum Android API level 24; the platform
TranslationManager API starts at API 31. The bundled detector is available without
a model download and adds about 900 KB according to Google's documentation.

## Text and state behavior

- Empty questions and already-matching detected languages do not request translation models.
- Detection uses the complete sentence, including mixed text such as Russian with
  English USB/API abbreviations. ML Kit translates the original naturally; the app
  does not mask words, split sentences, or implement a custom glossary. Detection
  identifies a likely language for the whole sentence, not separate languages for
  individual words.
- No translation engine guarantees exact handling of every abbreviation, URL,
  address, proper noun, or ambiguous mixed-language sentence. Uncertain or unsupported
  detection leaves the visible question unchanged.
- In the emulator's Russian-to-English samples, the model preserved `API`, email
  addresses, and URLs, but changed `USB` to lowercase `usb`. The app keeps Google's
  natural output; it does not enforce capitalization or infer protected terms.
- Language switches translate the original input, not previous machine translations.
  Switching back to a detected original language restores its exact text. Translations
  are cached for the session by normalized target language.
- Show original restores the original. Editing makes the edit the new source.
- Generation checks and coroutine cancellation prevent delayed results replacing
  edits or the result of a more recent language selection.
- A ViewModel retains work across locale/theme Activity recreation. SavedStateHandle
  retains the visible question, original, attribution state, and pending language
  across process restoration. These remain task UI state, not permanent question history.
- Translation never changes or rerolls the answer. Rotation, theme changes, and PiP
  do not trigger another translation.

## Models and failure handling

Only needed source/target models are requested. Google describes translation models
as approximately 30 MB each; English does not need a separate model. First-use downloads
require Wi-Fi and display download status. Cached models can translate offline.
Missing Wi-Fi, failed downloads, uncertain/unsupported languages, and translation errors
retain the visible question and offer Retry. Requests time out after two minutes.
Input, rolling, and language selection remain usable while a request is pending.
Non-English pairs may use English as an intermediate language; translation quality,
particularly for short or mixed text, can vary.

## Attribution

The picker explains that question translations use Google Translate. Translated questions
have Google's official attribution badge adjacent to them, including in PiP. The light
and dark resources are unchanged `png/color-regular@3x.png` and `png/white-regular@3x.png`
from Google's official attribution ZIP:

https://docs.cloud.google.com/static/translate/images/google-translate-attribution.zip

Tapping the badge opens localized help, the required Google translation disclaimer,
and a link to Google Translate. Attribution disappears when the user edits the text
or restores the original. The badge's aspect ratio is preserved.

The question has a reserved 48 dp toolbar below its field. A small attribution badge
and a mirrored Undo icon share one row; the icon restores the original and has a
localized tooltip/accessibility label. Idle, progress, download, error, and translated
states use the same height, keeping the decision button and die in place. Long status
messages stay on one line and open their full text in the details dialog. Retry keeps
a full 48 dp touch target without adding another row.

## Before publishing a release with ML Kit

This feature changes network/privacy disclosures. Question text is processed on the
device, but that does not mean the Google SDK sends no data. Google's disclosure lists
diagnostic/device/app data, per-installation identifiers, performance, and detected or
source/destination language information. Translation also uses Firebase remote
configuration/installations for diagnostics. Account for these SDK disclosures in
the Play Data safety form and the app's privacy policy.

Update localized listing descriptions before publishing: retain the claim that core
decisions work offline, explain first-use Wi-Fi language-model downloads, and add the
required Google Translate attribution/link. Store copy that unconditionally says no
Internet is required should not describe first-use translation. This implementation
does not bump the version or publish a new bundle or listing.

## Verification

Unit tests cover original preservation, edited questions, late results, rapid language
switches, returning to original text, restored snapshots, empty questions, and regional
language normalization. Instrumented tests check all bundled language mappings, the
real bundled detector with mixed Russian/English input, UI skip behavior, retained
answers, and Activity recreation.

Model-download tests are opt-in so the normal suite does not need network access or
download large files on arbitrary devices. Run on the emulator:

```powershell
$env:JAVA_HOME='C:/Program Files/Android/Android Studio/jbr'
$env:ANDROID_SERIAL='emulator-5554'
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.notClass=com.sblashkov.yesnorandomizer.StoreListingCapture' '-Pandroid.testInstrumentationRunnerArguments.translationModels=true'
```

Verified on October 22, 2026:

- Debug and minified release builds, debug/release lint, and all 10 unit tests passed.
- All 29 non-artwork emulator checks passed across the full suite and a targeted
  continuity rerun on Android 37.2 with 16 KB memory pages. The continuity test now
  waits for typed text before freezing its animation clock.
- The actual ML Kit tests downloaded a model, detected mixed Russian/English input,
  skipped matching-language translation, translated mixed questions, and checked
  all 18 picker locale mappings. Email and URL samples were preserved naturally.
- Light/dark translated-question UI and the attribution details dialog were inspected.
- Existing dice image checks were updated separately to account for the intentional
  neighboring faces and slight shading in the settled 15-degree view; rendering
  itself was unchanged.

The compact-toolbar redesign was also verified with debug builds/lint, fixed-position
checks across all 18 locales and Arabic/German/Russian at 150% font size, details/retry/
original actions, real translation and recreation, keyboard handling, and usable-window
checks at 432 x 768 dp, 360 x 640 dp, and 1280 x 800 dp landscape. All 11 relevant
emulator checks passed across the focused runs and an actions-test rerun. Light and
dark previews were inspected. The actions test closes the Compose dialog using its
OK control, avoiding an Espresso root-focus issue with simulated Back on this emulator.

## Primary references

- [Language identification](https://developers.google.com/ml-kit/language/identification/android)
- [Translation on Android](https://developers.google.com/ml-kit/language/translation/android)
- [Supported languages](https://developers.google.com/ml-kit/language/translation/translation-language-support)
- [ML Kit usage requirements](https://developers.google.com/ml-kit/language/translation/translation-terms)
- [Google attribution requirements](https://docs.cloud.google.com/translate/attribution)
- [ML Kit data disclosure](https://developers.google.com/ml-kit/android-data-disclosure)
