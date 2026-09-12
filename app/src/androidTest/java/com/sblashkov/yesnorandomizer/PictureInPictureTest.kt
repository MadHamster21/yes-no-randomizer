package com.sblashkov.yesnorandomizer

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
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
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 26)
class PictureInPictureTest {
  @get:Rule
  val compose = createAndroidComposeRule<MainActivity>()

  @Test
  fun pinningRequiresCompletedAnswerAndPreservesItOnReturn() {
    assumeTrue(
      compose.activity.packageManager.hasSystemFeature(
        PackageManager.FEATURE_PICTURE_IN_PICTURE
      )
    )
    compose.onNodeWithTag("pin-answer").assertIsNotEnabled()
    val question = "Should I take a walk?"
    compose.onNode(hasSetTextAction()).performTextInput(question)
    compose.mainClock.autoAdvance = false
    compose.onNodeWithText(compose.activity.getString(R.string.decide_button_text))
      .performScrollTo().performClick()
    compose.mainClock.advanceTimeBy(100)
    compose.onNodeWithTag("pin-answer").assertIsNotEnabled()
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()

    compose.onNodeWithTag("answer-text").assertDoesNotExist()
    val initialAnswer = compose.onNodeWithTag("answer-dice").fetchSemanticsNode()
      .config[SemanticsProperties.StateDescription]
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    val originalServiceInfo = automation.serviceInfo
    automation.serviceInfo = automation.serviceInfo.apply {
      flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
    }
    try {
      compose.onNodeWithTag("pin-answer").performScrollTo().performClick()
      compose.waitUntil(5_000) { compose.activity.isInPictureInPictureMode }
      // Compose test roots are filtered out when the activity is paused in PiP.
      // Inspect the actual system accessibility window instead.
      fun pipNodes(): List<AccessibilityNodeInfo> = automation.windows
        .mapNotNull { it.root }
        .filter { it.packageName == compose.activity.packageName }
        .flatMap { it.descendants() }

      compose.waitUntil(5_000) {
        val texts = pipNodes().map { it.text?.toString() }
        question in texts && initialAnswer in texts
      }
      assertFalse(pipNodes().any { it.isEditable })
      assertFalse(pipNodes().any {
        it.text?.toString() == compose.activity.getString(R.string.pip_button_text)
      })
      // Content is available before the system finishes its PiP animation.
      // Let the window transition settle before requesting the reverse transition.
      automation.waitForIdle(750, 5_000)

      // System UI owns PiP controls. Open its menu and invoke the actual remote
      // action, rather than sending a test-only broadcast directly to the app.
      val pipRoot = automation.windows.mapNotNull { it.root }
        .first { it.packageName == compose.activity.packageName }
      assertTrue(pipRoot.performAction(AccessibilityNodeInfo.ACTION_CLICK))
      val newAnswerLabel = compose.activity.getString(R.string.pip_new_answer)
      fun newAnswerButton(): AccessibilityNodeInfo? = automation.windows.mapNotNull { it.root }
        .flatMap { it.descendants() }
        .firstOrNull {
          it.isClickable && (it.contentDescription?.toString() == newAnswerLabel ||
              it.text?.toString() == newAnswerLabel)
        }
      try {
        compose.waitUntil(5_000) { newAnswerButton() != null }
      } catch (failure: Exception) {
        throw AssertionError(automation.windows.mapNotNull { it.root }
          .flatMap { it.descendants() }.joinToString("\n") {
            "${it.viewIdResourceName}: text=${it.text}, description=${it.contentDescription}, clickable=${it.isClickable}"
          }, failure)
      }
      assertTrue(newAnswerButton()!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
      automation.waitForIdle(750, 5_000)
      // The broadcast is delivered by Android, but recomposition is driven by
      // the test clock. Flush the updated answer before reading accessibility.
      compose.mainClock.advanceTimeBy(32)
      automation.waitForIdle(750, 5_000)
      assertTrue("New Answer must keep the app in PiP", compose.activity.isInPictureInPictureMode)
      val validAnswers = setOf(
        compose.activity.getString(R.string.yes_value),
        compose.activity.getString(R.string.no_value)
      )
      compose.waitUntil(5_000) {
        val texts = pipNodes().map { it.text?.toString() }
        question in texts && texts.any { it in validAnswers }
      }
      // A new answer is allowed to equal the previous one; verify the action's
      // resulting PiP content without assuming a different random value.
    } finally {
      automation.serviceInfo = originalServiceInfo
      // Return from outside the paused app, as a launcher does. Starting an
      // activity from the PiP app itself is subject to background launch limits.
      ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(
          "am start -W --activity-reorder-to-front -n com.sblashkov.yesnorandomizer/.MainActivity"
        )
      ).use { it.readBytes() }
    }
    compose.waitUntil(5_000) { !compose.activity.isInPictureInPictureMode }
    compose.onNode(hasSetTextAction()).assertTextEquals(
      compose.activity.getString(R.string.question_text_hint), question
    )
    compose.onNodeWithTag("answer-text").assertDoesNotExist()
    assertTrue(
      compose.onNodeWithTag("answer-dice").fetchSemanticsNode()
        .config[SemanticsProperties.StateDescription] in setOf(
        compose.activity.getString(R.string.yes_value),
        compose.activity.getString(R.string.no_value)
      )
    )
    compose.onNodeWithTag("answer-dice").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun rollsKeepLayoutStableWithoutADuplicateAnswer() {
    assumeTrue(
      compose.activity.packageManager.hasSystemFeature(
        PackageManager.FEATURE_PICTURE_IN_PICTURE
      )
    )
    val diceBounds = compose.onNodeWithTag("answer-dice").fetchSemanticsNode().boundsInRoot
    val buttonBounds = compose.onNodeWithTag("decide-button").fetchSemanticsNode().boundsInRoot
    val minimizeBounds = compose.onNodeWithTag("pin-answer").fetchSemanticsNode().boundsInRoot
    fun assertStableLayout() {
      assertEquals(
        diceBounds,
        compose.onNodeWithTag("answer-dice").fetchSemanticsNode().boundsInRoot
      )
      assertEquals(
        buttonBounds,
        compose.onNodeWithTag("decide-button").fetchSemanticsNode().boundsInRoot
      )
      assertEquals(
        minimizeBounds,
        compose.onNodeWithTag("pin-answer").fetchSemanticsNode().boundsInRoot
      )
      compose.onNodeWithTag("answer-text").assertDoesNotExist()
    }
    repeat(2) {
      compose.mainClock.autoAdvance = false
      compose.onNodeWithTag("decide-button").performClick()
      compose.mainClock.advanceTimeBy(100)
      compose.onNodeWithTag("pin-answer").assertIsNotEnabled()
      assertStableLayout()
      compose.mainClock.autoAdvance = true
      compose.waitForIdle()
      compose.onNodeWithTag("pin-answer").assertIsEnabled()
      assertStableLayout()
    }
  }

  private fun AccessibilityNodeInfo.descendants(): List<AccessibilityNodeInfo> =
    listOf(this) + (0 until childCount).mapNotNull { getChild(it) }.flatMap { it.descendants() }
}
