package com.sblashkov.yesnorandomizer

import android.content.pm.ActivityInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DecisionContinuityTest {
  @get:Rule val compose = createAndroidComposeRule<MainActivity>()
  private val question = "Should I take a walk?"

  private fun roll(): Boolean {
    compose.onNode(hasSetTextAction()).performTextInput(question)
    compose.onNodeWithTag("decide-button").performScrollTo().performClick()
    compose.waitForIdle()
    return answer() == compose.activity.getString(R.string.yes_value)
  }

  private fun answer() = compose.onNodeWithTag("answer-dice").fetchSemanticsNode()
    .config[SemanticsProperties.StateDescription]

  private fun assertDecision(isYes: Boolean) {
    compose.onNode(hasSetTextAction()).assertTextEquals(
      compose.activity.getString(R.string.question_text_hint), question
    )
    assertEquals(compose.activity.getString(if (isYes) R.string.yes_value else R.string.no_value), answer())
    compose.onNodeWithTag("decide-button").assertIsEnabled()
    compose.onNodeWithTag("pin-answer").assertIsEnabled()
  }

  @Test fun questionAndAnswerSurviveActivityRecreation() {
    val isYes = roll()
    compose.activityRule.scenario.recreate()
    compose.waitForIdle()
    assertDecision(isYes)
  }

  @Test fun recreationDuringRollSettlesTheSelectedAnswer() {
    compose.onNode(hasSetTextAction()).performTextInput(question)
    compose.mainClock.autoAdvance = false
    compose.onNodeWithTag("decide-button").performScrollTo().performClick()
    compose.mainClock.advanceTimeBy(100)
    compose.onNodeWithTag("decide-button").assertIsNotEnabled()
    val isYes = answer() == compose.activity.getString(R.string.yes_value)
    // Recreate the actual Android Activity; a frozen clock intentionally keeps
    // the original animation in flight until the Activity is destroyed.
    compose.activityRule.scenario.recreate()
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
    assertDecision(isYes)
    compose.onNodeWithTag("decide-button").performScrollTo().performClick()
    compose.waitForIdle()
    compose.onNodeWithTag("decide-button").assertIsEnabled()
  }

  @Test fun questionAndAnswerSurviveRotationInBothDirections() {
    val isYes = roll()
    val original = compose.activity.requestedOrientation
    try {
      for (orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)) {
        compose.runOnIdle { compose.activity.requestedOrientation = orientation }
        compose.waitUntil(5_000) {
          val view = compose.activity.window.decorView
          (view.width > view.height) == (orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        }
        compose.waitForIdle()
        assertDecision(isYes)
      }
    } finally {
      compose.runOnIdle { compose.activity.requestedOrientation = original }
    }
  }

  @Test fun languageChangeTranslatesTheSameAnswerAndKeepsTheQuestion() {
    val isYes = roll()
    try {
      compose.onNodeWithText("English (US)", substring = true).performClick()
      compose.onNodeWithText("🇪🇸 Español").performClick()
      compose.waitUntil(5_000) { compose.activity.getString(R.string.decide_button_text) == "¡Decidir!" }
      compose.waitForIdle()
      assertDecision(isYes)
    } finally {
      compose.onNodeWithText("🇪🇸 Español").performClick()
      compose.onNodeWithText("English (US)", substring = true).performClick()
      compose.waitUntil(5_000) { compose.activity.getString(R.string.decide_button_text) == "Decide!" }
    }
    assertDecision(isYes)
  }
}
