package com.sblashkov.yesnorandomizer

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sblashkov.yesnorandomizer.translation.QuestionSnapshot
import com.sblashkov.yesnorandomizer.translation.QuestionTranslationStatus
import com.sblashkov.yesnorandomizer.translation.QuestionTranslationSupport
import com.sblashkov.yesnorandomizer.translation.QuestionUiState
import com.sblashkov.yesnorandomizer.ui.DecisionLayout
import com.sblashkov.yesnorandomizer.ui.rememberAnswerDiceState
import com.sblashkov.yesnorandomizer.ui.theme.YesnorandomizerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class QuestionTranslationLayoutTest {
  @get:Rule
  val compose = createComposeRule()

  @Test
  fun translationStatesKeepTheQuestionButtonAndDieInPlaceInEveryLocale() {
    val language = mutableStateOf("en")
    val fontScale = mutableStateOf(1f)
    val question = QuestionSnapshot("Should I connect USB?")
    val translated = QuestionSnapshot("Подключить USB?", question.original, true)
    val state = mutableStateOf(QuestionUiState(question))
    compose.setContent {
      val base = LocalContext.current
      val density = LocalDensity.current
      val baseConfiguration = LocalConfiguration.current
      val configuration = remember(baseConfiguration, language.value, fontScale.value) {
        Configuration(baseConfiguration).apply {
          setLocale(Locale.forLanguageTag(language.value))
          this.fontScale = fontScale.value
        }
      }
      val context = remember(base, configuration) {
        base.createConfigurationContext(configuration)
      }
      CompositionLocalProvider(
        LocalContext provides context,
        LocalConfiguration provides configuration,
        LocalDensity provides Density(density.density, fontScale.value),
        LocalLayoutDirection provides if (language.value == "ar") LayoutDirection.Rtl
        else LayoutDirection.Ltr
      ) {
        YesnorandomizerTheme {
          DecisionLayout(
            question = state.value.question.text,
            onQuestionChange = {},
            diceState = rememberAnswerDiceState(),
            onDecide = {},
            scrollState = rememberScrollState(),
            modifier = Modifier.fillMaxSize(),
            diceModifier = Modifier.testTag("answer-dice"),
            questionSupport = { QuestionTranslationSupport(state.value, {}, {}) }
          ) {
            TextButton(onClick = {}, modifier = Modifier.testTag("pin-answer")) { Text("Float") }
          }
        }
      }
    }
    val tags = listOf("app-title", "question-field", "decide-button", "answer-dice", "pin-answer")
    // Start with a typed question: an empty field moves its floating label as
    // text is entered, independently of the translation controls under test.
    val states = QuestionTranslationStatus.entries.map { QuestionUiState(question, it) } +
        listOf(
          QuestionUiState(translated),
          QuestionUiState(translated, QuestionTranslationStatus.Downloading),
          QuestionUiState(translated, QuestionTranslationStatus.Unavailable)
        )
    val languages = mutableListOf<String>()
    InstrumentationRegistry.getInstrumentation().targetContext.resources
      .getXml(R.xml.locale_config).use { parser ->
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
          if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
            languages += parser.getAttributeValue(
              "http://schemas.android.com/apk/res/android",
              "name"
            )
          }
        }
      }
    assertEquals(18, languages.size)
    val configurations = languages.map { it to 1f } +
        listOf("ar" to 1.5f, "de" to 1.5f, "ru" to 1.5f)
    for ((locale, scale) in configurations) {
      compose.runOnIdle {
        language.value = locale
        fontScale.value = scale
        state.value = states.first()
      }
      val baseline = tags.associateWith { compose.onNodeWithTag(it).getUnclippedBoundsInRoot() }
      for (next in states.drop(1)) {
        compose.runOnIdle { state.value = next }
        for (tag in tags) {
          assertEquals(
            "$locale/$scale ${next.status}: $tag must not move or resize",
            baseline.getValue(tag), compose.onNodeWithTag(tag).getUnclippedBoundsInRoot()
          )
        }
        val toolbar =
          compose.onNodeWithTag("question-translation-support").getUnclippedBoundsInRoot()
        assertEquals(
          48f,
          (toolbar.bottom - toolbar.top).value,
          0.1f
        )
      }
    }
  }

  @Test
  fun compactControlsKeepFullStatusDetailsRetryAndOriginalActionsAccessible() {
    val state = mutableStateOf(
      QuestionUiState(QuestionSnapshot("Question?"), QuestionTranslationStatus.NeedsWifi)
    )
    var retries = 0
    var restores = 0
    lateinit var fullStatus: String
    lateinit var closeLabel: String
    compose.setContent {
      fullStatus = stringResource(R.string.translation_needs_wifi)
      closeLabel = stringResource(android.R.string.ok)
      YesnorandomizerTheme {
        QuestionTranslationSupport(state.value, { restores++ }, { retries++ })
      }
    }
    compose.onNodeWithTag("question-translation-status").performClick()
    compose.onNodeWithTag("translation-status-details").assertTextEquals(fullStatus)
    compose.onNodeWithText(closeLabel).performClick()
    compose.onNodeWithTag("retry-translation").performClick()
    compose.runOnIdle {
      assertEquals(1, retries)
      state.value = QuestionUiState(QuestionSnapshot("Translated?", "Question?", true))
    }
    compose.onNodeWithTag("translation-attribution").performClick()
    compose.onNodeWithText("Google Translate").assertIsDisplayed()
    compose.onNodeWithText(
      "THIS SERVICE MAY CONTAIN TRANSLATIONS POWERED BY GOOGLE.",
      substring = true
    )
      .assertExists()
    compose.onNodeWithText(closeLabel).performClick()
    compose.onNodeWithTag("show-original-question").performClick()
    compose.runOnIdle { assertEquals(1, restores) }
    val target = compose.onNodeWithTag("show-original-question").getUnclippedBoundsInRoot()
    assertEquals(48f, (target.right - target.left).value, 0.1f)
    assertEquals(48f, (target.bottom - target.top).value, 0.1f)
  }
}
