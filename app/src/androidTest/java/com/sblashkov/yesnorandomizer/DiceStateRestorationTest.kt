package com.sblashkov.yesnorandomizer

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sblashkov.yesnorandomizer.ui.AnswerDiceState
import com.sblashkov.yesnorandomizer.ui.rememberAnswerDiceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiceStateRestorationTest {
  @get:Rule val compose = createComposeRule()

  @Test fun completedYesAndNoRestoreWithMatchingLandingFaces() {
    val restoration = StateRestorationTester(compose)
    lateinit var state: AnswerDiceState
    restoration.setContent { state = rememberAnswerDiceState() }
    for (answer in listOf(R.string.yes_value, R.string.no_value)) {
      compose.runOnIdle { state.rollTo(answer, animate = false) }
      compose.waitForIdle()
      restoration.emulateSavedInstanceStateRestore()
      compose.runOnIdle {
        assertEquals(answer, state.answer)
        assertFalse(state.isRolling)
        assertEquals(0f, state.rotationXDegrees, 0f)
        assertEquals(if (answer == R.string.yes_value) 180f else 0f, state.rotationYDegrees, 0f)
      }
    }
  }

}
