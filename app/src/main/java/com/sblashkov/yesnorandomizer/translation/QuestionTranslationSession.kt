package com.sblashkov.yesnorandomizer.translation

import java.util.Locale

internal fun translationLanguage(tag: String): String =
  Locale.forLanguageTag(tag).language.lowercase(Locale.ROOT)

internal data class QuestionSnapshot(
  val text: String = "",
  val original: String = text,
  val translated: Boolean = false
)

internal data class QuestionTranslationResult(
  val text: String,
  val sourceLanguage: String,
  val translated: Boolean
)

internal data class QuestionTranslationRequest(
  val generation: Long,
  val original: String,
  val targetLanguage: String
)

/** Keep the user's own text as the source, rather than translating translations. */
internal class QuestionTranslationSession(initial: QuestionSnapshot = QuestionSnapshot()) {
  var snapshot = initial
    private set
  private var generation = 0L
  private val cache = mutableMapOf<String, QuestionTranslationResult>()

  fun edit(text: String) {
    generation++
    cache.clear()
    snapshot = QuestionSnapshot(text)
  }

  fun showOriginal() {
    generation++
    snapshot = QuestionSnapshot(snapshot.original)
  }

  fun begin(target: String): QuestionTranslationRequest? {
    generation++
    if (snapshot.original.isBlank()) return null
    val language = translationLanguage(target)
    cache[language]?.let {
      snapshot = snapshot.copy(text = it.text, translated = it.translated)
      return null
    }
    return QuestionTranslationRequest(generation, snapshot.original, language)
  }

  fun complete(request: QuestionTranslationRequest, result: QuestionTranslationResult): Boolean {
    if (request.generation != generation || request.original != snapshot.original) return false
    cache[request.targetLanguage] = result
    cache[translationLanguage(result.sourceLanguage)] =
      QuestionTranslationResult(snapshot.original, result.sourceLanguage, false)
    snapshot = snapshot.copy(text = result.text, translated = result.translated)
    return true
  }
}
