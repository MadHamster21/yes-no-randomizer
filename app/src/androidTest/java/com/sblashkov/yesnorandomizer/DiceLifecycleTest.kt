package com.sblashkov.yesnorandomizer

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.sblashkov.yesnorandomizer.ui.DiceColors
import com.sblashkov.yesnorandomizer.ui.DiceFace
import com.sblashkov.yesnorandomizer.ui.DiceGLSurfaceView
import com.sblashkov.yesnorandomizer.ui.theme.md_theme_dark_background
import com.sblashkov.yesnorandomizer.ui.theme.md_theme_dark_onPrimary
import com.sblashkov.yesnorandomizer.ui.theme.md_theme_dark_onTertiary
import com.sblashkov.yesnorandomizer.ui.theme.md_theme_dark_primary
import com.sblashkov.yesnorandomizer.ui.theme.md_theme_dark_tertiary
import com.sblashkov.yesnorandomizer.ui.theme.md_theme_light_background
import com.sblashkov.yesnorandomizer.ui.theme.md_theme_light_onPrimary
import com.sblashkov.yesnorandomizer.ui.theme.md_theme_light_onTertiary
import com.sblashkov.yesnorandomizer.ui.theme.md_theme_light_primary
import com.sblashkov.yesnorandomizer.ui.theme.md_theme_light_tertiary
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 26)
class DiceLifecycleTest {
  @Test
  fun landedFacesAndTextMatchBothThemePalettes() {
    ActivityScenario.launch(MainActivity::class.java).use {
      lateinit var diceView: DiceGLSurfaceView
      onView(isAssignableFrom(DiceGLSurfaceView::class.java)).check { view, exception ->
        if (exception != null) throw exception
        diceView = view as DiceGLSurfaceView
      }
      val palettes = listOf(
        DiceColors(
          md_theme_light_background.toArgb(),
          md_theme_light_primary.toArgb(), md_theme_light_onPrimary.toArgb(),
          md_theme_light_tertiary.toArgb(), md_theme_light_onTertiary.toArgb(),
          isInitialState = false
        ),
        DiceColors(
          md_theme_dark_background.toArgb(),
          md_theme_dark_primary.toArgb(), md_theme_dark_onPrimary.toArgb(),
          md_theme_dark_tertiary.toArgb(), md_theme_dark_onTertiary.toArgb(),
          isInitialState = false
        )
      )
      for ((theme, colors) in palettes.withIndex()) {
        diceView.updateColors(colors)
        for (face in DiceFace.entries) {
          diceView.updateRotation(face.rotationX, face.rotationY)
          val isYes = face.answer == R.string.yes_value
          val fill = if (isYes) colors.yesColor else colors.noColor
          val text = if (isYes) colors.onYesColor else colors.onNoColor
          val deadline = SystemClock.uptimeMillis() + 5_000
          var matches: Boolean
          do {
            matches = hasThemeColors(diceView, fill, text)
            if (!matches) SystemClock.sleep(50)
          } while (!matches && SystemClock.uptimeMillis() < deadline)
          assertTrue("${face.name} fill and text must match theme $theme", matches)
        }
      }
    }
  }

  @Test
  fun everyLandingFaceRendersTheSelectedAnswer() {
    ActivityScenario.launch(MainActivity::class.java).use {
      lateinit var diceView: DiceGLSurfaceView
      onView(isAssignableFrom(DiceGLSurfaceView::class.java)).check { view, exception ->
        if (exception != null) throw exception
        diceView = view as DiceGLSurfaceView
      }
      // Distinct colors let PixelCopy check the real textured face, independent
      // of the answer stored in Compose. Visit every landing used by rollTo.
      diceView.updateColors(
        DiceColors(
          Color.WHITE, Color.BLUE, Color.WHITE, Color.RED, Color.WHITE,
          isInitialState = false
        )
      )
      for (face in DiceFace.entries) {
        diceView.updateRotation(face.rotationX, face.rotationY)
        val deadline = SystemClock.uptimeMillis() + 5_000
        var matches: Boolean
        do {
          matches = hasRenderedDice(diceView, face.answer)
          if (!matches) SystemClock.sleep(50)
        } while (!matches && SystemClock.uptimeMillis() < deadline)
        assertTrue("${face.name} must render its selected Yes/No color", matches)
      }
    }
  }

  @Test
  fun diceRendersAfterRepeatedBackgroundTransitions() {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      assertDiceRenders()
      repeat(3) {
        // CREATED delivers onStop without destroying the Activity or its GL view.
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.moveToState(Lifecycle.State.RESUMED)
        assertDiceRenders()
      }
    }
  }

  private fun assertDiceRenders() {
    lateinit var diceView: DiceGLSurfaceView
    onView(isAssignableFrom(DiceGLSurfaceView::class.java)).check { view, exception ->
      if (exception != null) throw exception
      diceView = view as DiceGLSurfaceView
    }

    // Espresso cannot observe the GL thread. PixelCopy samples the actual Surface,
    // so a missing texture or a blank frame after EGL recreation fails this check.
    val deadline = SystemClock.uptimeMillis() + 5_000
    do {
      if (hasRenderedDice(diceView)) return
      SystemClock.sleep(50)
    } while (SystemClock.uptimeMillis() < deadline)
    throw AssertionError("Dice surface remained blank after becoming visible")
  }

  private fun hasRenderedDice(view: DiceGLSurfaceView, expectedAnswer: Int? = null): Boolean {
    val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
    try {
      if (!copySurface(view, bitmap)) return false
      val colors = mutableSetOf<Int>()
      var bluePixels = 0
      var redPixels = 0
      for (x in 8 until 24) {
        for (y in 8 until 24) {
          val pixel = bitmap.getPixel(x, y)
          colors.add(pixel)
          if (Color.blue(pixel) - Color.red(pixel) > 60) bluePixels++
          if (Color.red(pixel) - Color.blue(pixel) > 60) redPixels++
        }
      }
      return when (expectedAnswer) {
        R.string.yes_value -> bluePixels > 32 && redPixels == 0
        R.string.no_value -> redPixels > 32 && bluePixels == 0
        else -> colors.size > 2
      }
    } finally {
      bitmap.recycle()
    }
  }

  private fun hasThemeColors(view: DiceGLSurfaceView, fill: Int, text: Int): Boolean {
    val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
    try {
      if (!copySurface(view, bitmap)) return false
      var fillPixels = 0
      var textPixels = 0
      // Stay inside the face, away from the border and surrounding background.
      // Sample enough pixels to distinguish actual glyphs from antialiasing.
      for (x in 96 until 160) {
        for (y in 96 until 160) {
          val pixel = bitmap.getPixel(x, y)
          if (matchesColor(pixel, fill)) fillPixels++
          if (matchesColor(pixel, text)) textPixels++
        }
      }
      return fillPixels > 1_000 && textPixels > 20
    } finally {
      bitmap.recycle()
    }
  }

  private fun matchesColor(actual: Int, expected: Int): Boolean {
    // Allow small GPU rounding differences, but reject shading/gradient changes.
    return abs(Color.red(actual) - Color.red(expected)) <= 2 &&
        abs(Color.green(actual) - Color.green(expected)) <= 2 &&
        abs(Color.blue(actual) - Color.blue(expected)) <= 2
  }

  private fun copySurface(view: DiceGLSurfaceView, bitmap: Bitmap): Boolean {
    val copied = CountDownLatch(1)
    var result = PixelCopy.ERROR_SOURCE_NO_DATA
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      if (view.holder.surface.isValid) {
        PixelCopy.request(view, bitmap, { copyResult ->
          result = copyResult
          copied.countDown()
        }, Handler(Looper.getMainLooper()))
      } else {
        copied.countDown()
      }
    }
    assertTrue("PixelCopy callback timed out", copied.await(2, TimeUnit.SECONDS))
    return result == PixelCopy.SUCCESS
  }
}
