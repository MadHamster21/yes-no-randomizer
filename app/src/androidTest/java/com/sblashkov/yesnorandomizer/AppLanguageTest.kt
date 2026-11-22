package com.sblashkov.yesnorandomizer

import androidx.core.os.LocaleListCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLanguageTest {
  private val supportedLanguages = linkedSetOf(
    "en", "es", "fr", "ar", "es-419", "de", "hi", "id", "it",
    "ja", "ko", "pl", "pt", "ru", "th", "tr", "vi", "zh-CN"
  )

  private fun language(preferences: String, saved: String? = null): String =
    resolveAppLanguage(saved, LocaleListCompat.forLanguageTags(preferences), supportedLanguages)

  @Test
  fun unsupportedPrimaryLanguageUsesTheNextSupportedPreference() {
    assertEquals("fr", language("nl-NL,fr-FR,en-US"))
    assertEquals("de", language("nl-NL,sv-SE,de-DE,fr-FR"))
  }

  @Test
  fun preferenceOrderTakesPriorityOverTheOrderOfSupportedLanguages() {
    assertEquals("fr", language("fr-FR,en-US"))
    assertEquals("en", language("en-GB,fr-FR"))
  }

  @Test
  fun regionalVariantsMatchAllSupportedLanguages() {
    val regionalPreferences = listOf(
      "en-GB", "es-ES", "fr-CA", "ar-EG", "es-419", "de-AT", "hi-IN", "id-ID", "it-CH",
      "ja-JP", "ko-KR", "pl-PL", "pt-BR", "ru-RU", "th-TH", "tr-TR", "vi-VN", "zh-Hans-SG"
    )
    supportedLanguages.zip(regionalPreferences).forEach { (expected, preference) ->
      assertEquals(preference, expected, language(preference))
    }
  }

  @Test
  fun exactLatinAmericanSpanishDoesNotResolveToGenericSpanish() {
    assertEquals("es-419", language("es-419,es-ES,en-US"))
  }

  @Test
  fun unsupportedScriptsDoNotHideALaterSupportedLanguage() {
    assertEquals("fr", language("zh-Hant-TW,fr-FR,en-US"))
    assertEquals("en", language("zh-Hant-TW"))
  }

  @Test
  fun savedLanguageWinsOverSystemPreferencesIncludingRegionalChoices() {
    for (saved in supportedLanguages) {
      assertEquals(saved, language("nl-NL,fr-FR,en-US", saved))
    }
  }

  @Test
  fun missingOrInvalidSavedLanguageUsesSystemPreferences() {
    assertEquals("fr", language("nl-NL,fr-CA,en-US", ""))
    assertEquals("fr", language("nl-NL,fr-CA,en-US", "obsolete"))
  }

  @Test
  fun noSupportedPreferenceOrAnEmptyListFallsBackToEnglish() {
    assertEquals("en", language("nl-NL,sv-SE"))
    assertEquals("en", language(""))
  }
}
