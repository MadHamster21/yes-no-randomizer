package com.sblashkov.yesnorandomizer

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sblashkov.yesnorandomizer.translation.QuestionTranslationException
import com.sblashkov.yesnorandomizer.translation.QuestionTranslationResult
import com.sblashkov.yesnorandomizer.translation.QuestionTranslationStatus
import com.sblashkov.yesnorandomizer.translation.QuestionTranslationSupport
import com.sblashkov.yesnorandomizer.translation.QuestionTranslator
import com.sblashkov.yesnorandomizer.translation.QuestionViewModel
import com.sblashkov.yesnorandomizer.ui.theme.YesnorandomizerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuestionTranslationFailureTest {
  @get:Rule
  val compose = createComposeRule()
  private val store = ViewModelStore()
  private lateinit var model: QuestionViewModel
  private val original = "Should I drink that herbal tea?"
  private var attempts = 0
  private lateinit var errorText: String

  @Before
  fun showFailedTranslation() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    application.getSharedPreferences("question_translation", 0).edit().clear().commit()
    errorText = application.getString(R.string.translation_unavailable)
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      model = QuestionViewModel(application, SavedStateHandle(), object : QuestionTranslator {
        override suspend fun translate(
          text: String,
          targetLanguage: String,
          allowModelDownload: Boolean,
          onSourceIdentified: (String) -> Unit,
          downloading: () -> Unit
        ): QuestionTranslationResult {
          attempts++
          onSourceIdentified("en")
          if (attempts == 1) {
            throw QuestionTranslationException(QuestionTranslationStatus.Unavailable)
          }
          return QuestionTranslationResult("Стоит ли мне выпить этот травяной чай?", "en", true)
        }
      })
      store.put("question", model)
      model.edit(original)
      model.changeLanguage("ru")
    }
    compose.setContent {
      YesnorandomizerTheme {
        QuestionTranslationSupport(model.state, model::showOriginal, { model.changeLanguage("ru") })
      }
    }
    compose.waitUntil { model.state.status == QuestionTranslationStatus.Unavailable }
    compose.onNodeWithText(errorText).assertIsDisplayed()
  }

  @After
  fun clearModel() {
    InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
  }

  @Test
  fun returningToTheDetectedSourceClearsTheErrorWithoutAnotherAttempt() {
    compose.runOnIdle { model.changeLanguage("en-US") }
    compose.onNodeWithText(errorText).assertDoesNotExist()
    compose.onNodeWithTag("retry-translation").assertDoesNotExist()
    compose.runOnIdle {
      assertEquals(QuestionTranslationStatus.Idle, model.state.status)
      assertEquals(original, model.state.question.text)
      assertEquals(1, attempts)
    }
  }

  @Test
  fun retryStartsANewAttemptAndReplacesTheErrorWithAttribution() {
    compose.onNodeWithTag("retry-translation").performClick()
    compose.waitUntil { model.state.question.translated }
    compose.onNodeWithText(errorText).assertDoesNotExist()
    compose.onNodeWithTag("translation-attribution").assertIsDisplayed()
    compose.runOnIdle { assertEquals(2, attempts) }
  }
}
