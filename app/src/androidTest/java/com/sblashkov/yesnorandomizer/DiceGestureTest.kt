package com.sblashkov.yesnorandomizer

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sblashkov.yesnorandomizer.ui.AnswerDice
import com.sblashkov.yesnorandomizer.ui.AnswerDiceState
import com.sblashkov.yesnorandomizer.ui.DiceFace
import com.sblashkov.yesnorandomizer.ui.DiceOrientation
import com.sblashkov.yesnorandomizer.ui.DiceTextureView
import com.sblashkov.yesnorandomizer.ui.rememberAnswerDiceState
import com.sblashkov.yesnorandomizer.ui.theme.YesnorandomizerTheme
import com.sblashkov.yesnorandomizer.ui.theme.md_theme_light_primary
import com.sblashkov.yesnorandomizer.ui.theme.md_theme_light_tertiary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class DiceGestureTest {
  @get:Rule
  val compose = createComposeRule()
  private lateinit var dice: AnswerDiceState
  private lateinit var scroll: ScrollState

  private fun showDice(restoration: StateRestorationTester? = null) {
    val content: @Composable () -> Unit = {
      dice = rememberAnswerDiceState(R.string.no_value)
      scroll = rememberScrollState()
      YesnorandomizerTheme(darkTheme = false) {
        Surface(Modifier.fillMaxSize()) {
          Column(
            Modifier.fillMaxSize().verticalScroll(scroll),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Spacer(Modifier.height(80.dp))
            AnswerDice(dice, Modifier.size(300.dp).testTag("gesture-dice"))
            PinnedAnswer("Question?", dice.answer, animationKey = dice.answerGeneration)
            Spacer(Modifier.height(800.dp))
          }
        }
      }
    }
    if (restoration == null) compose.setContent(content) else restoration.setContent(content)
  }

  private fun assertLanding() {
    compose.runOnIdle {
      assertFalse(dice.isRolling)
      val face = DiceFace.entries.single {
        abs(it.orientation.dot(dice.orientation)) > 0.99999f
      }
      assertEquals(face.answer, dice.answer)
    }
    val text = InstrumentationRegistry.getInstrumentation().targetContext.getString(dice.answer)
    compose.onNodeWithTag("answer-text").assertTextEquals(text)
  }

  @Test
  fun touchingOrMovingLessThanTouchSlopDoesNotChangeTheAnswer() {
    showDice()
    compose.onNodeWithTag("gesture-dice").performTouchInput {
      down(center)
      moveBy(Offset(1f, 1f))
      up()
    }
    compose.runOnIdle {
      assertEquals(R.string.no_value, dice.answer)
      assertEquals(0, dice.answerGeneration)
      assertFalse(dice.isRolling)
    }
  }

  @Test
  fun slowDragChoosesYesAndCommitsTheMatchingRenderedAndFloatingAnswerOnlyOnLanding() {
    showDice()
    compose.onNodeWithTag("gesture-dice").performTouchInput {
      down(Offset(width * 0.85f, centerY))
      moveTo(Offset(width * 0.32f, centerY), delayMillis = 500)
      advanceEventTime(300) // A held face has no release momentum.
    }
    compose.runOnIdle {
      assertTrue(dice.isRolling)
      assertEquals(DiceFace.LEFT, dice.orientation.nearestFace())
      assertEquals(R.string.no_value, dice.answer)
    }
    compose.mainClock.autoAdvance = false
    compose.onNodeWithTag("gesture-dice").performTouchInput { up() }
    compose.runOnIdle { assertEquals(R.string.no_value, dice.answer) }
    compose.mainClock.advanceTimeBy(2000)
    compose.mainClock.autoAdvance = true
    assertLanding()
    compose.runOnIdle { assertEquals(R.string.yes_value, dice.answer) }
    assertRenderedAnswer(R.string.yes_value)
  }

  @Test
  fun fastFlingCanBeCaughtHeldAndRedirectedWithoutAnOldAnimationOverwritingIt() {
    showDice()
    compose.mainClock.autoAdvance = false
    compose.onNodeWithTag("gesture-dice").performTouchInput {
      swipe(Offset(width * 0.2f, centerY), Offset(width * 0.8f, centerY), 80)
    }
    compose.mainClock.advanceTimeBy(150)
    compose.runOnIdle { assertTrue(dice.isRolling) }
    compose.onNodeWithTag("gesture-dice").performTouchInput { down(center) }
    lateinit var held: DiceOrientation
    compose.runOnIdle { held = dice.orientation }
    compose.mainClock.advanceTimeBy(500)
    compose.runOnIdle { assertEquals(held, dice.orientation) }
    compose.onNodeWithTag("gesture-dice").performTouchInput {
      moveBy(Offset(-width * 0.4f, height * 0.2f), delayMillis = 500)
      advanceEventTime(300)
    }
    var expected = 0
    compose.runOnIdle { expected = dice.orientation.nearestFace().answer }
    compose.onNodeWithTag("gesture-dice").performTouchInput { up() }
    compose.mainClock.advanceTimeBy(5000)
    compose.mainClock.autoAdvance = true
    assertLanding()
    compose.runOnIdle {
      assertEquals(expected, dice.answer)
      assertEquals(1, dice.answerGeneration)
      assertEquals(1, dice.landingFeedback)
    }
  }

  @Test
  fun circularMotionAndAllEightSwipeDirectionsKeepWorkingAndFinishUpright() {
    showDice()
    val directions = listOf(
      1f to 0f, -1f to 0f, 0f to 1f, 0f to -1f,
      1f to 1f, -1f to 1f, 1f to -1f, -1f to -1f
    )
    for ((x, y) in directions) {
      compose.runOnIdle { dice.rollTo(R.string.no_value, animate = false) }
      compose.onNodeWithTag("gesture-dice").performTouchInput {
        down(center)
        moveBy(Offset(width * 0.3f * x, height * 0.3f * y), delayMillis = 400)
        advanceEventTime(300)
        up()
      }
      assertLanding()
    }
    compose.onNodeWithTag("gesture-dice").performTouchInput {
      down(center)
      moveBy(Offset(width * 0.2f, 0f), delayMillis = 100)
      moveBy(Offset(0f, height * 0.2f), delayMillis = 100)
      moveBy(Offset(-width * 0.2f, 0f), delayMillis = 100)
      moveBy(Offset(0f, -height * 0.2f), delayMillis = 100)
      advanceEventTime(300)
      up()
    }
    assertLanding()
    compose.runOnIdle { assertEquals(0, scroll.value) }
  }

  @Test
  fun cancellationAndBackgroundSettleWithoutLeavingTheDieLocked() {
    showDice()
    compose.onNodeWithTag("gesture-dice").performTouchInput {
      down(center)
      moveBy(Offset(-width * 0.5f, 0f))
      cancel()
    }
    assertLanding()
    compose.onNodeWithTag("gesture-dice").performTouchInput {
      down(center)
      moveBy(Offset(0f, height * 0.4f))
    }
    compose.runOnIdle { dice.settleForBackground() }
    compose.onNodeWithTag("gesture-dice").performTouchInput { cancel() }
    assertLanding()
  }

  @Test
  fun recreationDuringCoastRestoresItsPredictedAnswerAndAnUprightPose() {
    val restoration = StateRestorationTester(compose)
    showDice(restoration)
    compose.mainClock.autoAdvance = false
    compose.onNodeWithTag("gesture-dice").performTouchInput {
      swipe(Offset(width * 0.85f, centerY), Offset(width * 0.15f, centerY), 80)
    }
    compose.mainClock.advanceTimeBy(100)
    var expected = 0
    compose.runOnIdle {
      assertTrue(dice.isRolling)
      expected = dice.answerForRestore
    }
    restoration.emulateSavedInstanceStateRestore()
    compose.mainClock.autoAdvance = true
    assertLanding()
    compose.runOnIdle { assertEquals(expected, dice.answer) }
  }

  @Test
  fun swipingOutsideTheDieScrollsThePageWithoutRolling() {
    showDice()
    compose.onRoot().performTouchInput {
      swipe(Offset(width - 10f, height * 0.8f), Offset(width - 10f, height * 0.3f), 400)
    }
    compose.runOnIdle {
      assertTrue(scroll.value > 0)
      assertEquals(0, dice.answerGeneration)
      assertFalse(dice.isRolling)
    }
  }

  private fun assertRenderedAnswer(answer: Int) {
    lateinit var view: DiceTextureView
    onView(isAssignableFrom(DiceTextureView::class.java)).check { actual, exception ->
      if (exception != null) throw exception
      view = actual as DiceTextureView
    }
    val expected = (if (answer == R.string.yes_value) md_theme_light_primary
    else md_theme_light_tertiary).toArgb()
    val deadline = SystemClock.uptimeMillis() + 5000
    do {
      var bitmap: Bitmap? = null
      compose.runOnIdle { bitmap = view.bitmap }
      val frame = bitmap
      if (frame != null) {
        var count = 0
        for (y in frame.height / 3 until frame.height * 2 / 3) {
          for (x in frame.width / 3 until frame.width * 2 / 3) {
            val pixel = frame.getPixel(x, y)
            if (abs(Color.red(pixel) - Color.red(expected)) < 20 &&
              abs(Color.green(pixel) - Color.green(expected)) < 20 &&
              abs(Color.blue(pixel) - Color.blue(expected)) < 20
            ) count++
          }
        }
        frame.recycle()
        if (count > 100) return
      }
      SystemClock.sleep(50)
    } while (SystemClock.uptimeMillis() < deadline)
    error("The actual GL face must render the committed answer color")
  }
}
