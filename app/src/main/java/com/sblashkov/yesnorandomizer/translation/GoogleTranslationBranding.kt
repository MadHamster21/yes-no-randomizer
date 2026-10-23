package com.sblashkov.yesnorandomizer.translation

// Fixed branding and Google's required disclaimer, not app copy for translation.
// Keep these out of Android's string table: Play Console can translate bundled
// strings even when their source XML has translatable="false".
// https://docs.cloud.google.com/translate/attribution
internal object GoogleTranslationBranding {
  const val SERVICE_NAME = "Google Translate"
  const val ATTRIBUTION = "Powered by Google Translate"
  const val DISCLAIMER = "THIS SERVICE MAY CONTAIN TRANSLATIONS POWERED BY GOOGLE. " +
      "GOOGLE DISCLAIMS ALL WARRANTIES RELATED TO THE TRANSLATIONS, EXPRESS OR IMPLIED, " +
      "INCLUDING ANY WARRANTIES OF ACCURACY, RELIABILITY, AND ANY IMPLIED WARRANTIES OF " +
      "MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT."
}
