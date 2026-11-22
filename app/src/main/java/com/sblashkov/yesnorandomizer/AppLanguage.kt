package com.sblashkov.yesnorandomizer

import androidx.core.os.LocaleListCompat
import java.util.Locale

internal fun resolveAppLanguage(
  savedLanguage: String?,
  systemLocales: LocaleListCompat,
  supportedLanguages: Set<String>
): String {
  if (savedLanguage in supportedLanguages) return checkNotNull(savedLanguage)

  val supportedLocales = supportedLanguages.associateWith(Locale::forLanguageTag)
  for (index in 0 until systemLocales.size()) {
    val preferred = systemLocales[index] ?: continue
    // Preserve an exact variant, such as es-419, before trying its language.
    val exact = preferred.toLanguageTag()
    if (exact in supportedLanguages) return exact
    supportedLocales.entries.firstOrNull { (_, supported) ->
      LocaleListCompat.matchesLanguageAndScript(supported, preferred)
    }?.let { return it.key }
  }
  return "en"
}
