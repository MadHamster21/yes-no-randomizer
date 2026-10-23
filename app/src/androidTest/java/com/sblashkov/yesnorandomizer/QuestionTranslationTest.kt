package com.sblashkov.yesnorandomizer

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.sblashkov.yesnorandomizer.translation.MlKitQuestionTranslator
import com.sblashkov.yesnorandomizer.translation.translationLanguage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class QuestionTranslationTest {
  private val mixedQuestion = "Нужно ли подключить USB и настроить API?"

  @Test
  fun detectsRussianWithEnglishAbbreviationsOnDevice() {
    val identifier = LanguageIdentification.getClient()
    try {
      assertEquals(
        "ru",
        Tasks.await(identifier.identifyLanguage(mixedQuestion), 30, TimeUnit.SECONDS)
      )
    } finally {
      identifier.close()
    }
  }

  @Test
  fun sameLanguageSkipsTranslationAndModelDownloads() = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val result = withTimeout(30_000) {
      MlKitQuestionTranslator(context).translate(mixedQuestion, "ru") {
        fail("A matching detected language must not download translation models")
      }
    }
    assertEquals(mixedQuestion, result.text)
    assertFalse(result.translated)
  }

  @Test
  fun allBundledPickerLocalesHaveTranslationSupport() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val parser = context.resources.getXml(R.xml.locale_config)
    val locales = mutableListOf<String>()
    parser.use {
      while (it.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
        if (it.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && it.name == "locale") {
          locales += it.getAttributeValue("http://schemas.android.com/apk/res/android", "name")
        }
      }
    }
    assertEquals(18, locales.size)
    for (locale in locales) assertNotNull(
      locale,
      TranslateLanguage.fromLanguageTag(translationLanguage(locale))
    )
  }

  @Test
  fun realTranslationHandlesMixedRussianWithAbbreviations() = runBlocking {
    assumeTrue(
      "Opt in to model downloads", InstrumentationRegistry.getArguments()
        .getString("translationModels") == "true"
    )
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val result = withTimeout(180_000) {
      MlKitQuestionTranslator(context).translate(mixedQuestion, "en") {}
    }
    assertTrue(result.translated)
    assertEquals("ru", result.sourceLanguage)
    // Google's model can normalize capitalization; do not rewrite its result.
    assertTrue(result.text, result.text.contains("USB", ignoreCase = true))
    assertTrue(result.text, result.text.contains("API", ignoreCase = true))
    assertNotEquals(mixedQuestion, result.text)
  }

  @Test
  fun realTranslationNaturallyPreservesCommonAddressesAndUrls() = runBlocking {
    assumeTrue(
      "Opt in to model downloads", InstrumentationRegistry.getArguments()
        .getString("translationModels") == "true"
    )
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val translator = MlKitQuestionTranslator(context)
    val samples = listOf(
      "Нужно ли отправить PDF на test@example.com через API?" to listOf(
        "PDF",
        "test@example.com",
        "API"
      ),
      "Мне нужно открыть сайт https://example.com для настройки API?" to listOf(
        "https://example.com",
        "API"
      )
    )
    for ((question, terms) in samples) {
      val result = withTimeout(180_000) { translator.translate(question, "en") {} }
      assertTrue(result.translated)
      for (term in terms) assertTrue(result.text, result.text.contains(term))
    }
  }
}
