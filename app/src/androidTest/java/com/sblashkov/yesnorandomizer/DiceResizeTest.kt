package com.sblashkov.yesnorandomizer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sblashkov.yesnorandomizer.ui.DiceColors
import com.sblashkov.yesnorandomizer.ui.DiceFace
import com.sblashkov.yesnorandomizer.ui.DiceTextureView
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DiceResizeTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun demandRenderedDieStaysCenteredAndUnclippedAfterRepeatedResizes() {
    val size = mutableStateOf(160)
    lateinit var view: DiceTextureView
    compose.setContent {
      val context = LocalContext.current
      view = remember(context) {
        DiceTextureView(
          context,
          DiceColors(Color.WHITE, Color.BLUE, Color.WHITE, Color.RED, Color.WHITE, false),
          DiceFace.FRONT.rotationX, DiceFace.FRONT.rotationY
        )
      }
      DisposableEffect(view) {
        view.onResume()
        onDispose { view.close() }
      }
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(factory = { view }, modifier = Modifier.size(size.value.dp))
      }
    }
    // No roll/color update can repair a stale frame: exercise the same idle
    // rendering path used when the IME changes the available window height.
    for ((step, nextSize) in listOf(
      160,
      300,
      160,
      300,
      220,
      128,
      300,
      180,
      260,
      160,
      300
    ).withIndex()) {
      compose.runOnIdle { size.value = nextSize }
      compose.waitForIdle()
      // GL presentation is outside Compose's idle tracking. Inspect the resized
      // frame rather than accepting a scaled copy of the previous valid buffer.
      SystemClock.sleep(250)
      val deadline = SystemClock.uptimeMillis() + 3_000
      var bounds = Rect()
      var fits = false
      do {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        try {
          var copied = false
          compose.runOnIdle {
            if (view.isAvailable) {
              view.getBitmap(bitmap)
              copied = true
            }
          }
          if (copied) {
            bounds = coloredBounds(bitmap)
            fits = !bounds.isEmpty && bounds.left > 8 && bounds.top > 8 &&
                bounds.right < 248 && bounds.bottom < 248 &&
                bounds.centerX() in 96..160 && bounds.centerY() in 96..160
            if (!fits) {
              val context = InstrumentationRegistry.getInstrumentation().targetContext
              File(context.getExternalFilesDir(null), "dice-resize-$step-$nextSize.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
          }
        } finally {
          bitmap.recycle()
        }
        if (!fits) SystemClock.sleep(50)
      } while (!fits && SystemClock.uptimeMillis() < deadline)
      assertTrue("Die is cropped/off-center after resize $step to $nextSize dp: $bounds", fits)
    }
  }

  private fun coloredBounds(bitmap: Bitmap): Rect {
    val bounds = Rect()
    for (y in 0 until bitmap.height) {
      for (x in 0 until bitmap.width) {
        val pixel = bitmap.getPixel(x, y)
        if (kotlin.math.abs(Color.blue(pixel) - Color.red(pixel)) > 60) {
          bounds.union(x, y, x + 1, y + 1)
        }
      }
    }
    return bounds
  }
}
