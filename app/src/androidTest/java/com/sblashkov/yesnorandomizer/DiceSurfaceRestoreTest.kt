package com.sblashkov.yesnorandomizer

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.sblashkov.yesnorandomizer.ui.DiceTextureView
import com.sblashkov.yesnorandomizer.ui.theme.md_theme_dark_background
import com.sblashkov.yesnorandomizer.ui.theme.md_theme_light_background
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Sample the composed display to catch backing-layer flashes and resize seams. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class DiceSurfaceRestoreTest {
  @Test
  fun lightSurfaceBlendsIntoWindowDuringRestore() = checkRestore(false)
  @Test
  fun darkSurfaceBlendsIntoWindowDuringRestore() = checkRestore(true)

  private fun checkRestore(dark: Boolean) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val automation = instrumentation.uiAutomation
    val context = instrumentation.targetContext
    assumeTrue(context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE))
    val originalServiceInfo = automation.serviceInfo
    automation.serviceInfo = automation.serviceInfo.apply {
      flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
    }
    val manager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    val original = when (manager.nightMode) {
      UiModeManager.MODE_NIGHT_YES -> "yes"
      UiModeManager.MODE_NIGHT_NO -> "no"
      UiModeManager.MODE_NIGHT_CUSTOM -> "custom"
      else -> "auto"
    }

    fun shell(command: String) {
      ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
        .use { it.readBytes() }
    }

    val expected = (if (dark) md_theme_dark_background else md_theme_light_background).toArgb()
    val theme = if (dark) "dark" else "light"
    val executor = Executors.newSingleThreadExecutor()
    try {
      shell("cmd uimode night ${if (dark) "yes" else "no"}")
      ActivityScenario.launch(MainActivity::class.java).use { scenario ->
        fun tapText(resource: Int) {
          var label = ""
          scenario.onActivity { label = it.getString(resource) }
          val deadline = SystemClock.uptimeMillis() + 5_000
          do {
            val node = automation.windows.mapNotNull { it.root }
              .filter { it.packageName == context.packageName }
              .flatMap { it.descendants() }.firstOrNull { it.text?.toString() == label }
            if (node != null) {
              val target = Rect()
              node.getBoundsInScreen(target)
              shell("input tap ${target.centerX()} ${target.centerY()}")
              return
            }
            SystemClock.sleep(50)
          } while (SystemClock.uptimeMillis() < deadline)
          throw AssertionError("Missing control: $label")
        }
        tapText(R.string.decide_button_text)
        SystemClock.sleep(3_500)
        lateinit var bounds: Rect
        onView(isAssignableFrom(DiceTextureView::class.java)).check { view, error ->
          if (error != null) throw error
          val location = IntArray(2)
          view.getLocationOnScreen(location)
          bounds =
            Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
        }
        repeat(3) { cycle ->
          when (cycle) {
            0 -> scenario.moveToState(Lifecycle.State.CREATED)
            1 -> shell("input keyevent KEYCODE_HOME")
            2 -> tapText(R.string.pip_button_text)
          }
          // Launcher accessibility events can continue indefinitely. Wait for
          // the app's UI queue and transition rather than global device silence.
          instrumentation.waitForIdleSync()
          // isInPictureInPictureMode becomes true before the system's entry
          // animation ends. A restart during that animation can be ignored.
          SystemClock.sleep(if (cycle == 2) 3_500 else 1_000)
          if (cycle == 2) scenario.onActivity {
            assertTrue(
              "The floating-answer transition must enter actual PiP",
              it.isInPictureInPictureMode
            )
          }
          val ready = CountDownLatch(1)
          val sampling = executor.submit<Int> {
            var visibleFrames = 0
            var mismatchedFrames = 0
            var sampledFrames = 0
            val minimumSamplingEnd = SystemClock.uptimeMillis() + 3_000
            val deadline = minimumSamplingEnd + 7_000
            ready.countDown()
            while (SystemClock.uptimeMillis() < deadline &&
              (SystemClock.uptimeMillis() < minimumSamplingEnd || visibleFrames < 3)
            ) {
              val bitmap = automation.takeScreenshot() ?: continue
              try {
                sampledFrames++
                if (visibleFrames == 0) {
                  File(context.getExternalFilesDir(null), "$theme-restore-$cycle-unseen.png")
                    .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
                // Reject launcher/transition frames until the surrounding app
                // background is visible. Inspect all four corners of the GL
                // region, away from the die itself and the surrounding controls.
                val outside = listOf(
                  bounds.left - 8 to bounds.top + 8, bounds.right + 8 to bounds.top + 8,
                  bounds.left - 8 to bounds.bottom - 8, bounds.right + 8 to bounds.bottom - 8
                )
                val inside = listOf(
                  bounds.left + 8 to bounds.top + 8, bounds.right - 8 to bounds.top + 8,
                  bounds.left + 8 to bounds.bottom - 8, bounds.right - 8 to bounds.bottom - 8
                )
                if (outside.all { (x, y) -> matches(bitmap.getPixel(x, y), expected) }) {
                  visibleFrames++
                  if (inside.any { (x, y) -> !matches(bitmap.getPixel(x, y), expected) }) {
                    mismatchedFrames++
                    val file = File(context.getExternalFilesDir(null), "$theme-restore-$cycle.png")
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                  }
                }
              } finally {
                bitmap.recycle()
              }
              // Give the simultaneous shell restore a turn on UiAutomation's
              // connection instead of monopolizing it with screenshot requests.
              SystemClock.sleep(50)
            }
            assertTrue(
              "Only $visibleFrames/$sampledFrames visible $theme frames on restore $cycle at $bounds",
              visibleFrames >= 3
            )
            mismatchedFrames
          }
          assertTrue(ready.await(2, TimeUnit.SECONDS))
          if (cycle == 0) scenario.moveToState(Lifecycle.State.RESUMED)
          else shell("am start -W --activity-reorder-to-front -n com.sblashkov.yesnorandomizer/.MainActivity")
          assertTrue(
            "$theme surface flashed against the window on restore $cycle",
            sampling.get(15, TimeUnit.SECONDS) == 0
          )
          scenario.onActivity {
            assertTrue(
              "The app must return from PiP",
              !it.isInPictureInPictureMode
            )
          }
        }
        // Fractional scaling puts the backing-layer edge between display pixels,
        // as happens while Android shrinks the task into the recent-apps overview.
        lateinit var diceView: DiceTextureView
        onView(isAssignableFrom(DiceTextureView::class.java)).check { view, error ->
          if (error != null) throw error
          diceView = view as DiceTextureView
        }
        try {
          for (scale in listOf(.7333f, .8111f, .66667f)) {
            val scaledBounds = RectF()
            scenario.onActivity {
              diceView.scaleX = scale
              diceView.scaleY = scale
              diceView.translationX = .37f
              diceView.translationY = .43f
              val matrix = Matrix()
              diceView.transformMatrixToGlobal(matrix)
              scaledBounds.set(0f, 0f, diceView.width.toFloat(), diceView.height.toFloat())
              matrix.mapRect(scaledBounds)
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(250)
            val bitmap = checkNotNull(automation.takeScreenshot())
            try {
              val left = scaledBounds.left.toInt()
              val right = scaledBounds.right.toInt()
              val top = scaledBounds.top.toInt()
              val bottom = scaledBounds.bottom.toInt()
              val edge = buildList {
                for (offset in -2..2) {
                  for (x in left - 2..right + 2) {
                    add(x to top + offset)
                    add(x to bottom + offset)
                  }
                  for (y in top - 2..bottom + 2) {
                    add(left + offset to y)
                    add(right + offset to y)
                  }
                }
              }
              assertTrue(
                "$theme dice perimeter is visible at scale $scale",
                edge.all { (x, y) -> matches(bitmap.getPixel(x, y), expected) })
            } finally {
              bitmap.recycle()
            }
          }
        } finally {
          scenario.onActivity {
            diceView.scaleX = 1f
            diceView.scaleY = 1f
            diceView.translationX = 0f
            diceView.translationY = 0f
          }
        }
      }
    } finally {
      executor.shutdownNow()
      automation.serviceInfo = originalServiceInfo
      shell("cmd uimode night $original")
    }
  }

  private fun AccessibilityNodeInfo.descendants(): List<AccessibilityNodeInfo> =
    listOf(this) + (0 until childCount).mapNotNull { getChild(it) }.flatMap { it.descendants() }

  private fun matches(actual: Int, expected: Int) =
    abs(Color.red(actual) - Color.red(expected)) <= 2 &&
        abs(Color.green(actual) - Color.green(expected)) <= 2 &&
        abs(Color.blue(actual) - Color.blue(expected)) <= 2
}
