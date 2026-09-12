package com.sblashkov.yesnorandomizer

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.sblashkov.yesnorandomizer.ui.AnswerDiceState
import com.sblashkov.yesnorandomizer.ui.DiceFace
import com.sblashkov.yesnorandomizer.ui.rememberAnswerDiceState
import com.sblashkov.yesnorandomizer.ui.theme.YesnorandomizerTheme
import com.sblashkov.yesnorandomizer.ui.theme.md_theme_dark_primary
import com.sblashkov.yesnorandomizer.ui.theme.md_theme_dark_tertiary
import com.sblashkov.yesnorandomizer.ui.theme.md_theme_light_primary
import com.sblashkov.yesnorandomizer.ui.theme.md_theme_light_tertiary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 26)
class PinnedAnswerTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun immediateRetriesUpdateAnswerAndLandingWithoutWaitingForAnimation() {
    lateinit var dice: AnswerDiceState
    compose.setContent { dice = rememberAnswerDiceState() }
    compose.waitForIdle()
    compose.mainClock.autoAdvance = false
    for (answer in listOf(R.string.yes_value, R.string.no_value, R.string.yes_value)) {
      compose.runOnIdle { dice.rollTo(answer, animate = false) }
      compose.mainClock.advanceTimeByFrame()
      compose.runOnIdle {
        assertEquals(answer, dice.answer)
        assertFalse(dice.isRolling)
        assertTrue(DiceFace.entries.any { face ->
          face.answer == answer &&
              (face.rotationX + 360f) % 360f == dice.rotationXDegrees &&
              (face.rotationY + 360f) % 360f == dice.rotationYDegrees
        })
      }
    }
  }

  @Test
  fun answerTextUsesDiceColorsInBothThemes() {
    val dark = mutableStateOf(false)
    val answer = mutableStateOf(R.string.yes_value)
    compose.setContent {
      YesnorandomizerTheme(darkTheme = dark.value) {
        PinnedAnswer("", answer.value)
      }
    }
    val cases = listOf(
      Triple(false, R.string.yes_value, md_theme_light_primary),
      Triple(false, R.string.no_value, md_theme_light_tertiary),
      Triple(true, R.string.yes_value, md_theme_dark_primary),
      Triple(true, R.string.no_value, md_theme_dark_tertiary)
    )
    for ((darkTheme, result, color) in cases) {
      compose.runOnIdle {
        dark.value = darkTheme
        answer.value = result
      }
      val pixels = compose.onNodeWithTag("answer-text").captureToImage().toPixelMap()
      assertTrue(
        "Answer glyphs must use the matching dice color (dark=$darkTheme, result=$result)",
        (0 until pixels.width).any { x ->
          (0 until pixels.height).any { y -> pixels[x, y] == color }
        })
    }
  }
}
