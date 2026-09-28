package com.sblashkov.yesnorandomizer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Run on the compact phone and tablet viewport matrix, at default font scale. */
@RunWith(AndroidJUnit4::class)
class AdaptiveLayoutTest {
  @get:Rule
  val compose = createAndroidComposeRule<MainActivity>()

  @Test
  fun completeDecisionFitsTheUsableWindowWithoutScrolling() {
    compose.waitForIdle()
    val safe = compose.onNodeWithTag("safe-content").getUnclippedBoundsInRoot()
    for (tag in listOf("decision-form", "decide-button", "answer-dice", "pin-answer")) {
      val node = compose.onNodeWithTag(tag).assertIsDisplayed()
      val bounds = node.getUnclippedBoundsInRoot()
      assertTrue(
        "$tag must fit horizontally: $bounds within $safe",
        bounds.left.value >= safe.left.value - 1 && bounds.right.value <= safe.right.value + 1
      )
      assertTrue(
        "$tag must fit vertically: $bounds within $safe",
        bounds.top.value >= safe.top.value - 1 && bounds.bottom.value <= safe.bottom.value + 1
      )
    }
    val form = compose.onNodeWithTag("decision-form").getUnclippedBoundsInRoot()
    assertTrue(
      "The tablet form must not stretch wider than 480 dp",
      (form.right - form.left).value <= 481
    )
    val width = safe.right - safe.left
    val height = safe.bottom - safe.top
    if (width.value >= 600 && width > height) {
      compose.onNodeWithTag("wide-decision-layout").assertExists()
      val dice = compose.onNodeWithTag("answer-dice").getUnclippedBoundsInRoot()
      assertTrue("Landscape form and answer must sit side by side", form.right <= dice.left)
    }
  }
}
