package com.sblashkov.yesnorandomizer

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sblashkov.yesnorandomizer.translation.QuestionTranslationStatus
import com.sblashkov.yesnorandomizer.translation.QuestionViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuestionTranslationUiTest {
  @get:Rule
  val compose = createAndroidComposeRule<MainActivity>()
  private val question = "Нужно ли подключить USB и настроить API?"

  private fun selectLanguage(name: String, language: String) {
    compose.onNodeWithTag("language-picker").performClick()
    val label = hasText(name, substring = true)
    compose.onNodeWithTag("language-list").performScrollToNode(label)
    compose.onNode(label and hasAnyAncestor(hasTestTag("language-list"))).performClick()
    compose.waitUntil(10_000) {
      compose.activity.resources.configuration.locales[0].language == language
    }
    compose.waitForIdle()
  }

  private fun model() = ViewModelProvider(compose.activity)[QuestionViewModel::class.java]

  private fun waitForTranslation(timeout: Long = 30_000) {
    compose.waitUntil(timeout) {
      model().state.status != QuestionTranslationStatus.Translating &&
          model().state.status != QuestionTranslationStatus.Downloading
    }
    compose.waitForIdle()
  }

  @Test
  fun mixedQuestionAlreadyInSelectedLanguageIsKeptAndSurvivesRecreation() {
    try {
      compose.onNode(hasSetTextAction()).performTextInput(question)
      val isYes = compose.onNodeWithTag("answer-dice").fetchSemanticsNode()
        .config[SemanticsProperties.StateDescription] == compose.activity.getString(R.string.yes_value)
      selectLanguage("Русский", "ru")
      waitForTranslation()
      assertEquals(QuestionTranslationStatus.Idle, model().state.status)
      assertEquals(question, model().state.question.text)
      compose.onNodeWithTag("translation-attribution").assertDoesNotExist()
      compose.activityRule.scenario.recreate()
      compose.waitForIdle()
      compose.onNode(hasSetTextAction()).assertTextEquals(
        compose.activity.getString(R.string.question_text_hint), question
      )
      assertEquals(
        compose.activity.getString(if (isYes) R.string.yes_value else R.string.no_value),
        compose.onNodeWithTag("answer-dice").fetchSemanticsNode()
          .config[SemanticsProperties.StateDescription]
      )
    } finally {
      compose.onNode(hasSetTextAction()).performTextClearance()
      selectLanguage("English (US)", "en")
    }
  }

  @Test
  fun translatedQuestionKeepsOriginalAndGoogleAttributionAfterRecreation() {
    assumeTrue(
      "Opt in to model downloads", InstrumentationRegistry.getArguments()
        .getString("translationModels") == "true"
    )
    try {
      compose.onNode(hasSetTextAction()).performTextInput(question)
      selectLanguage("Русский", "ru")
      waitForTranslation()
      selectLanguage("English (US)", "en")
      waitForTranslation(180_000)
      assertEquals(QuestionTranslationStatus.Idle, model().state.status)
      assertTrue(model().state.question.translated)
      val translated = model().state.question.text
      assertTrue(
        translated,
        translated.contains("USB", ignoreCase = true) && translated.contains(
          "API",
          ignoreCase = true
        )
      )
      compose.onNodeWithTag("translation-attribution").assertExists()
      compose.activityRule.scenario.recreate()
      compose.waitForIdle()
      assertEquals(translated, model().state.question.text)
      compose.onNodeWithTag("show-original-question").performScrollTo().performClick()
      assertEquals(question, model().state.question.text)
      compose.onNodeWithTag("translation-attribution").assertDoesNotExist()
    } finally {
      compose.onNode(hasSetTextAction()).performTextClearance()
      selectLanguage("English (US)", "en")
    }
  }
}
