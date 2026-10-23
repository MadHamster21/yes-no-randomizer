package com.sblashkov.yesnorandomizer.translation

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal data class QuestionUiState(
  val question: QuestionSnapshot,
  val status: QuestionTranslationStatus = QuestionTranslationStatus.Idle
)

internal class QuestionViewModel(
  application: Application,
  private val savedState: SavedStateHandle
) : AndroidViewModel(application) {
  private val session = QuestionTranslationSession(
    QuestionSnapshot(
      text = savedState[TEXT] ?: "",
      original = savedState[ORIGINAL] ?: savedState[TEXT] ?: "",
      translated = savedState[TRANSLATED] ?: false
    )
  )
  private val translator: QuestionTranslator = MlKitQuestionTranslator(application)
  private var job: Job? = null
  private var requestSerial = 0L
  var state by mutableStateOf(QuestionUiState(session.snapshot))
    private set

  init {
    // A retained ViewModel keeps requests through locale/theme recreation.
    // Resume detection after process restoration if a request was interrupted.
    savedState.get<String>(PENDING_LANGUAGE)?.let(::changeLanguage)
  }

  fun edit(text: String) {
    cancel()
    session.edit(text)
    publish()
  }

  fun showOriginal() {
    cancel()
    session.showOriginal()
    publish()
  }

  fun changeLanguage(language: String) {
    cancel()
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
          translator.translate(request.original, request.targetLanguage) {
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
      } catch (_: Exception) {
        finishFailure(serial, QuestionTranslationStatus.Unavailable)
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
    savedState.remove<String>(PENDING_LANGUAGE)
  }

  private fun publish(status: QuestionTranslationStatus = QuestionTranslationStatus.Idle) {
    val question = session.snapshot
    savedState[TEXT] = question.text
    savedState[ORIGINAL] = question.original
    savedState[TRANSLATED] = question.translated
    state = QuestionUiState(question, status)
  }

  private companion object {
    const val TEXT = "question.text"
    const val ORIGINAL = "question.original"
    const val TRANSLATED = "question.translated"
    const val PENDING_LANGUAGE = "question.pendingLanguage"
  }
}
