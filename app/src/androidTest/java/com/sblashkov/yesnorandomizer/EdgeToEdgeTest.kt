package com.sblashkov.yesnorandomizer

import android.content.pm.ActivityInfo
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EdgeToEdgeTest {
  @get:Rule
  val compose = createAndroidComposeRule<MainActivity>()

  @Test
  fun backgroundFillsWindowAndContentAvoidsSystemUi() {
    compose.waitForIdle()
    val background = compose.onRoot().fetchSemanticsNode().boundsInWindow
    compose.runOnIdle {
      val decor = compose.activity.window.decorView
      assertEquals(0f, background.left, 1f)
      assertEquals(0f, background.top, 1f)
      assertEquals(decor.width.toFloat(), background.right, 1f)
      assertEquals(decor.height.toFloat(), background.bottom, 1f)
    }
    assertContentAvoidsSystemUi()
  }

  @Test
  fun questionAndButtonRemainReachableWithKeyboardOpen() {
    compose.onNode(hasSetTextAction()).performClick()
    compose.runOnIdle {
      val window = compose.activity.window
      WindowCompat.getInsetsController(window, window.decorView)
        .show(WindowInsetsCompat.Type.ime())
    }
    try {
      compose.waitUntil(timeoutMillis = 5_000) {
        ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
          ?.isVisible(WindowInsetsCompat.Type.ime()) == true
      }
      compose.onNode(hasSetTextAction()).performScrollTo().assertIsDisplayed()
      assertContentAvoidsSystemUi()
      compose.onNodeWithText(compose.activity.getString(R.string.decide_button_text))
        .performScrollTo()
        .assertIsDisplayed()
    } finally {
      compose.runOnIdle {
        val window = compose.activity.window
        WindowCompat.getInsetsController(window, window.decorView)
          .hide(WindowInsetsCompat.Type.ime())
      }
    }
  }

  @Test
  fun diceRemainsReachableInLandscape() {
    val originalOrientation = compose.activity.requestedOrientation
    try {
      compose.runOnIdle {
        compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
      }
      compose.waitUntil(timeoutMillis = 5_000) {
        val decor = compose.activity.window.decorView
        decor.width > decor.height
      }
      compose.onNodeWithTag("answer-dice").performScrollTo().assertIsDisplayed()
      assertContentAvoidsSystemUi()
    } finally {
      compose.runOnIdle {
        compose.activity.requestedOrientation = originalOrientation
      }
    }
  }

  private fun assertContentAvoidsSystemUi() {
    // Insets animate independently of Compose's test clock. Wait for the layout
    // to catch up, then check actual window coordinates rather than fixed dp values.
    compose.waitUntil(timeoutMillis = 5_000) {
      val content = compose.onNodeWithTag("safe-content").fetchSemanticsNode().boundsInWindow
      val safeBounds = safeWindowBounds()
      content.left >= safeBounds.left - 1f &&
          content.top >= safeBounds.top - 1f &&
          content.right <= safeBounds.right + 1f &&
          content.bottom <= safeBounds.bottom + 1f &&
          content.width > 0f && content.height > 0f
    }
    assertTrue("System bar insets must be available", safeWindowBounds().height > 0f)
  }

  private fun safeWindowBounds(): Rect {
    val decor = compose.activity.window.decorView
    val insets = checkNotNull(ViewCompat.getRootWindowInsets(decor)).getInsets(
      WindowInsetsCompat.Type.systemBars() or
          WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime()
    )
    return Rect(
      insets.left.toFloat(),
      insets.top.toFloat(),
      (decor.width - insets.right).toFloat(),
      (decor.height - insets.bottom).toFloat()
    )
  }
}
