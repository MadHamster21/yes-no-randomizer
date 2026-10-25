package com.sblashkov.yesnorandomizer

import android.app.LocaleManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Handler
import android.os.LocaleList
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.sblashkov.yesnorandomizer.ui.DiceColors
import com.sblashkov.yesnorandomizer.ui.DiceLabels
import com.sblashkov.yesnorandomizer.ui.DiceTextureView
import com.sblashkov.yesnorandomizer.ui.theme.md_theme_dark_background
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Real configuration dispatch and GL resources, not just the saved answer. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class DiceConfigurationTest {
  @get:Rule
  val compose = createAndroidComposeRule<MainActivity>()
  private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
  private val languages = linkedMapOf(
    "en" to "English (US)", "es" to "Español", "fr" to "Français",
    "ar" to "العربية", "es-419" to "Español (Latinoamérica)",
    "de" to "Deutsch", "hi" to "हिन्दी", "id" to "Indonesia", "it" to "Italiano",
    "ja" to "日本語", "ko" to "한국어", "pl" to "Polski", "pt" to "Português",
    "ru" to "Русский", "th" to "ไทย", "tr" to "Türkçe", "vi" to "Tiếng Việt",
    "zh-CN" to "简体中文"
  )

  private data class RenderState(
    val context: Any,
    val cube: Any,
    val vertices: Any,
    val program: Int,
    val texture: Int,
    val labels: DiceLabels,
    val colors: DiceColors,
    val orientation: FloatArray
  )

  private fun Any.field(name: String): Any =
    javaClass.getDeclaredField(name).apply { isAccessible = true }.get(this)!!

  private fun view(): DiceTextureView {
    val activity = compose.activity
    var found: DiceTextureView? = null
    fun visit(view: View) {
      if (view is DiceTextureView) found = view
      if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
    }
    instrumentation.runOnMainSync { visit(activity.window.decorView) }
    return checkNotNull(found)
  }

  private fun snapshot(view: DiceTextureView): RenderState {
    val result = AtomicReference<RenderState>()
    val failure = AtomicReference<Throwable>()
    val done = CountDownLatch(1)
    assertTrue((view.field("handler") as Handler).post {
      try {
        val renderer = view.field("renderer")
        val cube = renderer.field("cube")
        result.set(
          RenderState(
            view.field("eglContext"), cube, cube.field("vertexBuffer"),
            cube.field("program") as Int, cube.field("textureId") as Int,
            cube.field("labels") as DiceLabels, cube.field("diceColors") as DiceColors,
            (renderer.field("orientationMatrix") as FloatArray).copyOf()
          )
        )
      } catch (error: Throwable) {
        failure.set(error)
      } finally {
        done.countDown()
      }
    })
    assertTrue("GL inspection must complete", done.await(5, TimeUnit.SECONDS))
    failure.get()?.let { throw it }
    return checkNotNull(result.get())
  }

  private fun assertSameResources(before: RenderState, after: RenderState) {
    assertSame("EGL context must stay alive", before.context, after.context)
    assertSame("Cube must not be rebuilt", before.cube, after.cube)
    assertSame("Geometry must not be reallocated", before.vertices, after.vertices)
    assertEquals("Shader must be retained", before.program, after.program)
    assertEquals("Atlas allocation must be reused", before.texture, after.texture)
  }

  private fun originalLanguage(): String = compose.activity
    .getSharedPreferences("yesnorandomizer_prefs", 0).getString("language_code_pref", "en")
    ?.takeIf { it in languages } ?: "en"

  private fun awaitLanguage(tag: String) {
    compose.waitUntil(10000) {
      if (!compose.mainClock.autoAdvance) compose.mainClock.advanceTimeBy(32)
      compose.activity.resources.configuration.locales[0].toLanguageTag() ==
          Locale.forLanguageTag(tag).toLanguageTag()
    }
    compose.mainClock.advanceTimeBy(32)
    compose.waitForIdle()
    val expected = DiceLabels.from(compose.activity)
    compose.waitUntil(5000) {
      if (!compose.mainClock.autoAdvance) compose.mainClock.advanceTimeBy(16)
      snapshot(view()).labels == expected
    }
  }

  private fun selectLanguage(tag: String) {
    compose.onNodeWithTag("language-picker").performClick()
    val text = if (tag == "es") hasText("🇪🇸 Español")
    else hasText(languages.getValue(tag), substring = true)
    compose.onNodeWithTag("language-list").performScrollToNode(text)
    // Match the exact language name: Español also prefixes the Latin America entry.
    compose.onNode(text and hasAnyAncestor(hasTestTag("language-list")))
      .performClick()
    awaitLanguage(tag)
  }

  private fun changeLanguageDuringMotion(tag: String) {
    val activity = compose.activity
    instrumentation.runOnMainSync {
      activity.getSharedPreferences("yesnorandomizer_prefs", 0).edit()
        .putString("language_code_pref", tag).commit()
      activity.getSystemService(LocaleManager::class.java).applicationLocales =
        LocaleList.forLanguageTags(tag)
    }
    awaitLanguage(tag)
  }

  private fun bitmap(view: DiceTextureView): Bitmap {
    var frame: Bitmap? = null
    instrumentation.runOnMainSync { frame = view.bitmap }
    return checkNotNull(frame)
  }

  @Test
  fun allLanguagePickerEntriesKeepTheExactPoseAndGpuResourcesWhileUpdatingText() {
    val original = originalLanguage()
    try {
      selectLanguage("en")
      val activity = compose.activity
      val die = view()
      val before = snapshot(die)
      val english = bitmap(die)
      try {
        // German changes both Yes and No, so the visible glyphs must change too.
        selectLanguage("de")
        var changed = false
        compose.waitUntil(5000) {
          val german = bitmap(view())
          changed = !english.sameAs(german)
          german.recycle()
          changed
        }
        assertTrue("The actual GL lettering must refresh", changed)
        for (tag in languages.keys) {
          selectLanguage(tag)
          assertSame("Locale must not recreate the activity", activity, compose.activity)
          assertSame("Locale must retain the TextureView", die, view())
          val after = snapshot(die)
          assertSameResources(before, after)
          assertArrayEquals(
            "Settled pose must remain exact", before.orientation,
            after.orientation, 0f
          )
          assertEquals(DiceLabels.from(activity), after.labels)
        }
      } finally {
        english.recycle()
      }
    } finally {
      selectLanguage(original)
    }
  }

  @Test
  fun languageAndRtlChangesDuringARollKeepTheSameRendererAndAnimation() {
    val original = originalLanguage()
    try {
      selectLanguage("en")
      val activity = compose.activity
      val die = view()
      val before = snapshot(die)
      compose.mainClock.autoAdvance = false
      compose.onNodeWithTag("decide-button").performClick()
      compose.mainClock.advanceTimeBy(150)
      for (tag in listOf("de", "ar", "en")) {
        changeLanguageDuringMotion(tag)
        assertSame(activity, compose.activity)
        assertSame(die, view())
        assertSameResources(before, snapshot(die))
        compose.onNodeWithTag("decide-button").assertIsNotEnabled()
      }
      compose.mainClock.autoAdvance = true
      compose.waitForIdle()
      compose.onNodeWithTag("decide-button").assertIsEnabled()
      compose.onNodeWithTag("pin-answer").assertIsEnabled()
    } finally {
      compose.mainClock.autoAdvance = true
      changeLanguageDuringMotion(original)
    }
  }

  @Test
  fun themeChangesDuringARollKeepMotionAndOnlyUpdateTheExistingAtlas() {
    fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
      instrumentation.uiAutomation.executeShellCommand(command)
    ).use { String(it.readBytes()).trim() }

    val original = shell("cmd uimode night").substringAfterLast(' ')
    try {
      shell("cmd uimode night no")
      compose.waitUntil(5000) {
        compose.activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_NO
      }
      compose.waitForIdle()
      val activity = compose.activity
      val die = view()
      val before = snapshot(die)
      compose.mainClock.autoAdvance = false
      compose.onNodeWithTag("decide-button").performClick()
      compose.mainClock.advanceTimeBy(150)
      shell("cmd uimode night yes")
      compose.waitUntil(10000) {
        compose.mainClock.advanceTimeBy(16)
        snapshot(view()).colors.background == md_theme_dark_background.toArgb()
      }
      assertSame(activity, compose.activity)
      assertSame(die, view())
      assertSameResources(before, snapshot(die))
      compose.onNodeWithTag("decide-button").assertIsNotEnabled()
      compose.mainClock.autoAdvance = true
      compose.waitForIdle()
      compose.onNodeWithTag("pin-answer").assertIsEnabled()
    } finally {
      compose.mainClock.autoAdvance = true
      shell("cmd uimode night $original")
      compose.waitForIdle()
    }
  }
}
