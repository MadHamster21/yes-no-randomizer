package com.sblashkov.yesnorandomizer

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sblashkov.yesnorandomizer.translation.QuestionTranslationDownloadDialog
import com.sblashkov.yesnorandomizer.translation.QuestionTranslationException
import com.sblashkov.yesnorandomizer.translation.QuestionTranslationResult
import com.sblashkov.yesnorandomizer.translation.QuestionTranslationStatus
import com.sblashkov.yesnorandomizer.translation.QuestionTranslationSupport
import com.sblashkov.yesnorandomizer.translation.QuestionTranslator
import com.sblashkov.yesnorandomizer.translation.QuestionViewModel
import com.sblashkov.yesnorandomizer.ui.theme.YesnorandomizerTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuestionDownloadConsentTest {
  @get:Rule
  val compose = createComposeRule()
  private val store = ViewModelStore()
  private val original = "Should I go?"
  private lateinit var model: QuestionViewModel
  private var attempts = 0
  private var downloads = 0
  private val translator = object : QuestionTranslator {
    override suspend fun translate(
      text: String,
      targetLanguage: String,
      allowModelDownload: Boolean,
      onSourceIdentified: (String) -> Unit,
      downloading: () -> Unit
    ): QuestionTranslationResult {
      attempts++
      onSourceIdentified("en")
      if (!allowModelDownload) throw QuestionTranslationException(QuestionTranslationStatus.DownloadRequired)
      downloads++
      downloading()
      return QuestionTranslationResult("Dois-je y aller ?", "en", true)
    }
  }

  @Before
  fun requestDownload() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    application.getSharedPreferences("question_translation", 0).edit().clear().commit()
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      model = QuestionViewModel(application, SavedStateHandle(), translator)
      store.put("question", model)
      model.edit(original)
      model.changeLanguage("fr")
    }
    compose.setContent {
      YesnorandomizerTheme {
        QuestionTranslationSupport(
          model.state,
          model::showOriginal,
          { model.changeLanguage("fr") },
          model::translateQuestion
        )
        if (model.state.status == QuestionTranslationStatus.DownloadRequired) {
          QuestionTranslationDownloadDialog(model::approveDownload, model::declineDownload)
        }
      }
    }
    compose.onNodeWithTag("download-translation-models").assertIsDisplayed()
    assertEquals(0, downloads)
  }

  @After
  fun clearModelAndPreferences() {
    InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
    ApplicationProvider.getApplicationContext<Application>()
      .getSharedPreferences("question_translation", 0).edit().clear().commit()
  }

  @Test
  fun decliningKeepsOriginalAndSilencesTranslationAcrossLanguagesAndRestarts() {
    compose.onNodeWithTag("decline-translation-models").performClick()
    compose.onNodeWithTag("translation-attribution").assertDoesNotExist()
    compose.onNodeWithTag("question-translation-status").assertDoesNotExist()
    compose.onNodeWithTag("retry-translation").assertDoesNotExist()
    compose.onNodeWithTag("translate-question").assertIsDisplayed()
    compose.runOnIdle {
      model.changeLanguage("ru")
      assertEquals(original, model.state.question.text)
      assertEquals(QuestionTranslationStatus.Idle, model.state.status)
      assertFalse(model.state.translationEnabled)
      val restored = QuestionViewModel(
        ApplicationProvider.getApplicationContext(),
        SavedStateHandle(),
        translator
      )
      store.put("restored", restored)
      restored.edit(original)
      restored.changeLanguage("de")
      assertFalse(restored.state.translationEnabled)
      assertEquals(original, restored.state.question.text)
      assertEquals(1, attempts)
      assertEquals(0, downloads)
    }
  }

  @Test
  fun approvingDownloadsAndShowsTheTranslatedQuestion() {
    compose.onNodeWithTag("download-translation-models").performClick()
    compose.waitUntil { model.state.question.translated }
    compose.onNodeWithTag("translation-attribution").assertIsDisplayed()
    compose.runOnIdle {
      assertEquals(QuestionTranslationStatus.Idle, model.state.status)
      assertEquals(1, downloads)
      assertEquals(2, attempts)
    }
  }

  @Test
  fun translationCanBeReenabledAfterDeclining() {
    compose.onNodeWithTag("decline-translation-models").performClick()
    compose.onNodeWithTag("translate-question").performClick()
    compose.runOnIdle { assertTrue(model.state.translationEnabled) }
    compose.onNodeWithTag("download-translation-models").assertIsDisplayed()
    assertEquals(0, downloads)
  }
}
