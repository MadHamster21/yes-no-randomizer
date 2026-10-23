package com.sblashkov.yesnorandomizer.translation

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal data class QuestionUiState(
  val question: QuestionSnapshot,
  val status: QuestionTranslationStatus = QuestionTranslationStatus.Idle,
  val translationEnabled: Boolean = true,
  val canTranslate: Boolean = false
)

internal class QuestionViewModel @JvmOverloads constructor(
  application: Application,
  private val savedState: SavedStateHandle,
  private val translator: QuestionTranslator = MlKitQuestionTranslator(application)
) : AndroidViewModel(application) {
  private val preferences =
    application.getSharedPreferences("question_translation", Context.MODE_PRIVATE)
  private val session = QuestionTranslationSession(
    QuestionSnapshot(
      text = savedState[TEXT] ?: "",
      original = savedState[ORIGINAL] ?: savedState[TEXT] ?: "",
      translated = savedState[TRANSLATED] ?: false
    ),
    initialSourceLanguage = savedState[SOURCE_LANGUAGE]
  )
  private var job: Job? = null
  private var detectionJob: Job? = null
  private var selectedLanguage = savedState.get<String>(SELECTED_LANGUAGE)
    ?: application.resources.configuration.locales[0].toLanguageTag()
  private var requestSerial = 0L
  var state by mutableStateOf(
    QuestionUiState(
      session.snapshot,
      translationEnabled = preferences.getBoolean(ENABLED, true)
    )
  )
    private set

  init {
    if (!state.translationEnabled) {
      session.showOriginal()
      publish()
    }
    // A retained ViewModel keeps requests through locale/theme recreation.
    // Resume detection after process restoration if a request was interrupted.
    savedState.get<String>(PENDING_LANGUAGE)?.let(::changeLanguage)
    if (job == null) identifySourceSilently()
  }

  fun edit(text: String) {
    cancel()
    session.edit(text)
    publish()
    identifySourceSilently()
  }

  fun showOriginal() {
    cancel()
    session.showOriginal()
    publish()
  }

  fun changeLanguage(language: String) {
    cancel()
    selectedLanguage = language
    savedState[SELECTED_LANGUAGE] = language
    if (!state.translationEnabled) {
      publish()
      identifySourceSilently()
      return
    }
    val request = session.begin(language)
    if (request == null) {
      publish()
      return
    }
    val serial = requestSerial
    savedState[PENDING_LANGUAGE] = language
    publish(QuestionTranslationStatus.Translating)
    job = viewModelScope.launch {
      try {
        val result = withTimeout(120_000) {
          translator.translate(
            request.original, request.targetLanguage,
            allowModelDownload = preferences.getBoolean(DOWNLOAD_ALLOWED, false),
            onSourceIdentified = {
              if (serial == requestSerial && session.identifySource(request, it)) {
                savedState[SOURCE_LANGUAGE] = session.sourceLanguage
              }
            }) {
            if (serial == requestSerial) publish(QuestionTranslationStatus.Downloading)
          }
        }
        if (serial == requestSerial && session.complete(request, result)) {
          savedState.remove<String>(PENDING_LANGUAGE)
          publish()
        }
      } catch (_: TimeoutCancellationException) {
        finishFailure(serial, QuestionTranslationStatus.Unavailable)
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (failure: QuestionTranslationException) {
        finishFailure(serial, failure.status)
      } catch (failure: Exception) {
        // Record SDK failures for diagnostics, without logging the question.
        Log.w("QuestionTranslation", "On-device translation failed", failure)
        finishFailure(serial, QuestionTranslationStatus.Unavailable)
      }
    }
  }

  fun updateSelectedLanguage(language: String) {
    if (selectedLanguage == language) return
    cancel()
    selectedLanguage = language
    savedState[SELECTED_LANGUAGE] = language
    publish()
    identifySourceSilently()
  }

  fun translateQuestion() {
    setTranslationEnabled(true)
    changeLanguage(selectedLanguage)
  }

  fun setTranslationEnabled(enabled: Boolean) {
    cancel()
    preferences.edit().putBoolean(ENABLED, enabled).apply()
    if (!enabled) session.showOriginal()
    publish()
    identifySourceSilently()
  }

  fun approveDownload() {
    preferences.edit().putBoolean(DOWNLOAD_ALLOWED, true).apply()
    savedState.get<String>(SELECTED_LANGUAGE)?.let(::changeLanguage)
  }

  fun declineDownload() = setTranslationEnabled(false)

  private fun identifySourceSilently() {
    if (session.snapshot.original.isBlank() || session.sourceLanguage != null ||
      session.snapshot.translated
    ) return
    val request = session.begin(selectedLanguage) ?: return
    val serial = requestSerial
    detectionJob = viewModelScope.launch {
      try {
        delay(500)
        val source = withTimeout(30_000) { translator.identifyLanguage(request.original) }
        if (source != null && serial == requestSerial && session.identifySource(request, source)) {
          publish(state.status)
        }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (failure: Exception) {
        // Ambiguous text or unavailable detection must not add an error while typing.
        Log.w("QuestionTranslation", "Question language detection failed", failure)
      }
    }
  }

  private fun finishFailure(serial: Long, status: QuestionTranslationStatus) {
    if (serial == requestSerial) {
      savedState.remove<String>(PENDING_LANGUAGE)
      publish(status)
    }
  }

  private fun cancel() {
    requestSerial++
    job?.cancel()
    detectionJob?.cancel()
    savedState.remove<String>(PENDING_LANGUAGE)
  }

  private fun publish(status: QuestionTranslationStatus = QuestionTranslationStatus.Idle) {
    val question = session.snapshot
    savedState[TEXT] = question.text
    savedState[ORIGINAL] = question.original
    savedState[TRANSLATED] = question.translated
    savedState[SOURCE_LANGUAGE] = session.sourceLanguage
    val canTranslate = status == QuestionTranslationStatus.Idle && !question.translated &&
        question.original.isNotBlank() && session.sourceLanguage != null &&
        session.sourceLanguage != translationLanguage(selectedLanguage)
    state = QuestionUiState(question, status, preferences.getBoolean(ENABLED, true), canTranslate)
  }

  private companion object {
    const val TEXT = "question.text"
    const val ORIGINAL = "question.original"
    const val TRANSLATED = "question.translated"
    const val SOURCE_LANGUAGE = "question.sourceLanguage"
    const val PENDING_LANGUAGE = "question.pendingLanguage"
    const val SELECTED_LANGUAGE = "question.selectedLanguage"
    const val ENABLED = "translation.enabled"
    const val DOWNLOAD_ALLOWED = "translation.downloadAllowed"
  }
}
