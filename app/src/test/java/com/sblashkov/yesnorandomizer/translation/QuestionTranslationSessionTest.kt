package com.sblashkov.yesnorandomizer.translation

import org.junit.Assert.*
import org.junit.Test

class QuestionTranslationSessionTest {
  @Test
  fun detectedSourceRemainsAvailableWhenTranslationDoesNotComplete() {
    val original = "Should I drink that herbal tea?"
    val session = QuestionTranslationSession(QuestionSnapshot(original))
    val request = session.begin("ru")!!
    assertTrue(session.identifySource(request, "en"))
    // No successful result follows: the model download or translation failed.
    assertNull(session.begin("en-US"))
    assertEquals(QuestionSnapshot(original), session.snapshot)
  }

  @Test
  fun detectedSourceSurvivesProcessRestorationAfterFailure() {
    val original = "Should I drink that herbal tea?"
    val session = QuestionTranslationSession(QuestionSnapshot(original))
    session.identifySource(session.begin("ru")!!, "en")
    val restored = QuestionTranslationSession(session.snapshot, session.sourceLanguage)
    assertNull(restored.begin("en-US"))
    assertEquals(QuestionSnapshot(original), restored.snapshot)
  }

  @Test
  fun editingInvalidatesThePreviouslyDetectedSourceLanguage() {
    val session = QuestionTranslationSession(QuestionSnapshot("Should I go?"))
    session.identifySource(session.begin("ru")!!, "en")
    session.edit("Стоит ли идти?")
    assertNull(session.sourceLanguage)
    assertNotNull(session.begin("en"))
  }

  @Test
  fun lateSourceDetectionCannotCacheTheLanguageOfAnEarlierQuestion() {
    val session = QuestionTranslationSession(QuestionSnapshot("Should I go?"))
    val request = session.begin("ru")!!
    session.edit("Стоит ли идти?")
    assertFalse(session.identifySource(request, "en"))
    assertNull(session.sourceLanguage)
    assertNotNull(session.begin("en"))
  }

  @Test
  fun regionalPickerVariantsShareTranslationLanguages() {
    assertEquals("es", translationLanguage("es-419"))
    assertEquals("es", translationLanguage("es-ES"))
    assertEquals("zh", translationLanguage("zh-CN"))
    assertEquals("id", translationLanguage("in"))
  }

  @Test
  fun emptyQuestionNeedsNoTranslation() {
    val session = QuestionTranslationSession()
    session.edit("  ")
    assertNull(session.begin("ru"))
    assertEquals("  ", session.snapshot.text)
  }

  @Test
  fun allTranslationsStartFromTheUsersOriginalToAvoidDrift() {
    val session = QuestionTranslationSession()
    session.edit("Should I connect USB?")
    val french = session.begin("fr")!!
    assertTrue(
      session.complete(
        french,
        QuestionTranslationResult("Dois-je connecter USB ?", "en", true)
      )
    )
    val russian = session.begin("ru")!!
    assertEquals("Should I connect USB?", russian.original)
    assertEquals("Dois-je connecter USB ?", session.snapshot.text)
  }

  @Test
  fun switchingBackRestoresTheExactOriginalWithoutRetranslating() {
    val original = "Should I connect USB?"
    val session = QuestionTranslationSession(QuestionSnapshot(original))
    val request = session.begin("fr")!!
    session.complete(request, QuestionTranslationResult("Dois-je connecter USB ?", "en", true))
    assertNull(session.begin("en-US"))
    assertEquals(QuestionSnapshot(original), session.snapshot)
  }

  @Test
  fun lateTranslationCannotOverwriteAnEditedQuestion() {
    val session = QuestionTranslationSession(QuestionSnapshot("First question?"))
    val request = session.begin("fr")!!
    session.edit("My new question?")
    assertFalse(
      session.complete(
        request,
        QuestionTranslationResult("Première question ?", "en", true)
      )
    )
    assertEquals("My new question?", session.snapshot.text)
  }

  @Test
  fun rapidLanguageChangesIgnoreEarlierResults() {
    val session = QuestionTranslationSession(QuestionSnapshot("Should I go?"))
    val french = session.begin("fr")!!
    val russian = session.begin("ru")!!
    assertTrue(session.complete(russian, QuestionTranslationResult("Стоит ли идти?", "en", true)))
    assertFalse(
      session.complete(
        french,
        QuestionTranslationResult("Dois-je y aller ?", "en", true)
      )
    )
    assertEquals("Стоит ли идти?", session.snapshot.text)
  }

  @Test
  fun editingTranslatedTextMakesTheEditTheNewSource() {
    val session = QuestionTranslationSession(QuestionSnapshot("Should I go?"))
    val french = session.begin("fr")!!
    session.complete(french, QuestionTranslationResult("Dois-je y aller ?", "en", true))
    session.edit("Dois-je partir demain ?")
    assertEquals("Dois-je partir demain ?", session.begin("ru")!!.original)
    assertFalse(session.snapshot.translated)
  }

  @Test
  fun showOriginalCancelsPendingTranslationsAndKeepsTheOriginalExact() {
    val original = "Should I go?"
    val session = QuestionTranslationSession(QuestionSnapshot(original))
    val request = session.begin("fr")!!
    session.showOriginal()
    assertFalse(
      session.complete(
        request,
        QuestionTranslationResult("Dois-je y aller ?", "en", true)
      )
    )
    assertEquals(QuestionSnapshot(original), session.snapshot)
  }

  @Test
  fun restoredTranslatedQuestionRetainsTheOriginalSource() {
    val session =
      QuestionTranslationSession(QuestionSnapshot("Стоит ли идти?", "Should I go?", true))
    assertEquals("Should I go?", session.begin("fr")!!.original)
    session.showOriginal()
    assertEquals("Should I go?", session.snapshot.text)
  }

  @Test
  fun alreadySelectedLanguageKeepsMixedQuestionUnchanged() {
    val original = "Нужно ли подключить USB и настроить API?"
    val session = QuestionTranslationSession(QuestionSnapshot(original))
    val request = session.begin("ru")!!
    session.complete(request, QuestionTranslationResult(original, "ru", false))
    assertEquals(QuestionSnapshot(original), session.snapshot)
    assertNull(session.begin("ru-RU"))
  }
}
