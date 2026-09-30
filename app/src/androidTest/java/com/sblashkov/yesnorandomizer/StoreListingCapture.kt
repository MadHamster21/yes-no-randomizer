package com.sblashkov.yesnorandomizer

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.LocaleManager
import android.app.UiAutomation
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.LocaleList
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Base64
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in store artwork capture. Uses real UI interactions and random rolls. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class StoreListingCapture {
  @get:Rule
  val compose = createAndroidComposeRule<MainActivity>()

  @Test
  fun captureLocalizedStoreInputs() {
    val args = InstrumentationRegistry.getArguments()
    assumeTrue("Only run explicitly for store artwork", args.containsKey("storeLocales"))
    val locales = JSONArray(
      String(
        Base64.decode(args.getString("storeLocales"), Base64.DEFAULT),
        Charsets.UTF_8
      )
    )
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val automation = instrumentation.uiAutomation
    val context = instrumentation.targetContext
    val preferences = context.getSharedPreferences("yesnorandomizer_prefs", Context.MODE_PRIVATE)
    val originalLanguage = preferences.getString("language_code_pref", null)
    val localeManager = context.getSystemService(LocaleManager::class.java)
    val originalLocales = localeManager.applicationLocales
    val originalServiceInfo = automation.serviceInfo
    automation.serviceInfo = automation.serviceInfo.apply {
      flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
    }
    fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
      automation.executeShellCommand(command)
    ).use { String(it.readBytes()) }

    val originalNight = shell("cmd uimode night").trim().substringAfterLast(' ')
    val tablet = args.getString("storeTablet") == "true"
    val output = File(context.getExternalFilesDir(null), "store-localized").apply { mkdirs() }

    fun setTheme(dark: Boolean) {
      shell("cmd uimode night ${if (dark) "yes" else "no"}")
      val expected = if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
      compose.waitUntil(15_000) {
        compose.activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == expected
      }
      compose.waitForIdle()
    }

    fun rollUntil(yes: Boolean) {
      repeat(40) {
        compose.onNodeWithTag("decide-button").performScrollTo().performClick()
        compose.waitForIdle()
        val answer = compose.onNodeWithTag("answer-dice").fetchSemanticsNode()
          .config[SemanticsProperties.StateDescription]
        if (answer == compose.activity.getString(if (yes) R.string.yes_value else R.string.no_value)) {
          // TextureView draws on its own thread, independently of Compose's clock.
          SystemClock.sleep(700)
          return
        }
      }
      error("Did not encounter the requested random outcome")
    }

    fun save(file: File, floating: Boolean = false) {
      SystemClock.sleep(500)
      val screenshot = checkNotNull(automation.takeScreenshot())
      if (tablet && !floating) {
        check(screenshot.width > screenshot.height) { "Tablet capture must be landscape" }
      }
      val rect = if (floating) {
        automation.windows.filter { it.root?.packageName == context.packageName }
          .map { window -> Rect().also { window.getBoundsInScreen(it) } }
          .filter { it.width() > 50 && it.height() > 50 }
          .minBy { it.width() * it.height() }
      } else {
        val insets = checkNotNull(ViewCompat.getRootWindowInsets(compose.activity.window.decorView))
          .getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
        Rect(
          insets.left,
          insets.top,
          screenshot.width - insets.right,
          screenshot.height - insets.bottom
        )
      }
      val crop = Bitmap.createBitmap(screenshot, rect.left, rect.top, rect.width(), rect.height())
      file.outputStream().use { crop.compress(Bitmap.CompressFormat.PNG, 100, it) }
      crop.recycle()
      screenshot.recycle()
    }

    try {
      if (tablet) {
        automation.setRotation(UiAutomation.ROTATION_FREEZE_0)
        compose.waitUntil(15_000) {
          compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
      }
      for (i in 0 until locales.length()) {
        val item = locales.getJSONObject(i)
        val locale = item.getString("locale")
        val folder = File(output, locale).apply { mkdirs() }
        setTheme(false)
        compose.onNodeWithTag("language-picker").performClick()
        val label = hasText(item.getString("label"))
        compose.onNodeWithTag("language-list").performScrollToNode(label)
        compose.onNode(label and hasAnyAncestor(hasTestTag("language-list"))).performClick()
        compose.waitUntil(15_000) {
          preferences.getString("language_code_pref", "") == item.getString("appLocale") &&
              compose.activity.resources.configuration.locales[0].language ==
              java.util.Locale.forLanguageTag(item.getString("appLocale")).language
        }
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).performTextReplacement(item.getString("question"))
        rollUntil(true)
        save(File(folder, "light.png"))
        compose.onNodeWithTag("language-picker").performClick()
        compose.onNodeWithTag("language-list")
          .performScrollToNode(hasText("English (US)", substring = true))
        save(File(folder, "languages.png"))
        pressBack()
        setTheme(true)
        rollUntil(false)
        save(File(folder, "dark.png"))
        compose.onNodeWithTag("pin-answer").performClick()
        SystemClock.sleep(3_500)
        instrumentation.runOnMainSync { assertTrue(compose.activity.isInPictureInPictureMode) }
        // The system transition runs in real time; Compose's test clock must
        // also advance before photographing the final floating content.
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        fun hasText(node: AccessibilityNodeInfo, text: String): Boolean {
          if (node.text?.toString() == text) return true
          return (0 until node.childCount).any { child ->
            node.getChild(child)?.let { hasText(it, text) } == true
          }
        }
        // PiP pauses the Activity, so its Compose root is filtered from tests.
        // Confirm the actual system window contains the question and answer.
        compose.waitUntil(10_000) {
          automation.windows.mapNotNull { it.root }.any {
            it.packageName == context.packageName && hasText(it, item.getString("question")) &&
                hasText(it, compose.activity.getString(R.string.no_value))
          }
        }
        save(File(folder, "floating.png"), floating = true)
        shell("am start -W -n ${context.packageName}/.MainActivity")
        SystemClock.sleep(2_000)
        compose.waitForIdle()
        instrumentation.sendStatus(0, Bundle().apply { putString("stream", "Captured $locale\n") })
      }
    } finally {
      // Close before restoring locale/theme so cleanup cannot recreate the
      // Activity while Compose's test rule is disposing its owner.
      compose.activityRule.scenario.close()
      shell("cmd uimode night $originalNight")
      preferences.edit().apply {
        if (originalLanguage == null) remove("language_code_pref")
        else putString("language_code_pref", originalLanguage)
      }.commit()
      instrumentation.runOnMainSync { localeManager.applicationLocales = originalLocales }
      automation.serviceInfo = originalServiceInfo
    }
  }
}
