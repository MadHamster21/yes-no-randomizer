package com.sblashkov.yesnorandomizer

import android.app.LocaleManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.PictureInPictureUiState
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.util.Rational
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.sblashkov.yesnorandomizer.ui.AnswerDice
import com.sblashkov.yesnorandomizer.ui.rememberAnswerDiceState
import com.sblashkov.yesnorandomizer.ui.theme.YesnorandomizerTheme
import java.util.Locale
import kotlin.random.Random

class MainActivity : ComponentActivity() {
  private var showPictureInPicture by mutableStateOf(false)
  private var isEnteringPictureInPicture by mutableStateOf(false)
  private var answerBounds: Rect? = null
  private var refreshActionBitmap: Bitmap? = null

  private fun supportsPictureInPicture(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

  private fun pinAnswer() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !supportsPictureInPicture()) return

    val params = PictureInPictureParams.Builder().setAspectRatio(Rational(1, 1))
    val newAnswerIntent = PendingIntent.getBroadcast(
      this, 0, Intent(ACTION_NEW_ANSWER).setPackage(packageName),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val newAnswerLabel = getString(R.string.pip_new_answer)
    refreshActionBitmap?.let { bitmap ->
      params.setActions(
        listOf(
          RemoteAction(
            Icon.createWithBitmap(bitmap), newAnswerLabel, newAnswerLabel, newAnswerIntent
          )
        )
      )
    }
    answerBounds?.takeUnless { it.isEmpty }?.let { params.setSourceRectHint(it) }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      // A static answer is pinned only on request; use a crossfade when resizing text.
      params.setAutoEnterEnabled(false).setSeamlessResizeEnabled(false)
    }
    // Hide the answer during the system's crop/resize animation, including on
    // versions without the early onPictureInPictureUiStateChanged callback.
    isEnteringPictureInPicture = true
    val entered = try {
      enterPictureInPictureMode(params.build())
    } catch (_: IllegalStateException) {
      false
    }
    if (!entered) {
      isEnteringPictureInPicture = false
      Toast.makeText(this, R.string.pip_unavailable, Toast.LENGTH_LONG).show()
    }
  }

  override fun onPictureInPictureModeChanged(
    isInPictureInPictureMode: Boolean,
    newConfig: Configuration
  ) {
    super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    showPictureInPicture = isInPictureInPictureMode
    // This callback marks the end of the entry animation. Compose can now lay
    // out the answer in the final PiP bounds instead of the full-screen crop.
    isEnteringPictureInPicture = false
  }

  override fun onPictureInPictureUiStateChanged(pipState: PictureInPictureUiState) {
    super.onPictureInPictureUiStateChanged(pipState)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
      pipState.isTransitioningToPip
    ) {
      isEnteringPictureInPicture = true
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    // Set the locale before the activity is created.
    setLocale(this.resources)
    super.onCreate(savedInstanceState)
    showPictureInPicture = isInPictureInPictureMode
    enableEdgeToEdge()
    setContent {
      YesnorandomizerTheme {
        Surface(
          modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
        ) {
          YesNoScreen()
        }
      }
    }
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    // Also apply the locale on configuration changes.
    setLocale(this.resources)
  }

  @Suppress("DEPRECATION")
  private fun setLocale(resources: Resources) {
    val languageCode = getSavedLocale()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val appLocale = LocaleList.forLanguageTags(languageCode)
      val localeManager = getSystemService(LOCALE_SERVICE) as LocaleManager
      localeManager.applicationLocales = appLocale
    } else {
      val config = resources.configuration
      val locale = getLocale(getSavedLocale())
      Locale.setDefault(locale)
      config.setLocale(locale)

      createConfigurationContext(config)
      resources.updateConfiguration(config, resources.displayMetrics)
    }
  }

  @Composable
  fun YesNoScreen() {
    val isInPreview = LocalInspectionMode.current
    var question by rememberSaveable { mutableStateOf("") }
    val diceState = rememberAnswerDiceState()
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    var currentLanguage by remember {
      mutableStateOf(
        if (isInPreview) "en" else getSavedLocale()
      )
    }
    var expanded by remember { mutableStateOf(false) }

    // Use Google's Material Refresh icon for the native PiP RemoteAction. The
    // painter is rasterized once because RemoteAction accepts a native Bitmap.
    val refreshPainter = rememberVectorPainter(Icons.Default.Refresh)
    val refreshBitmap = remember(refreshPainter) { renderActionIcon(refreshPainter) }
    SideEffect { refreshActionBitmap = refreshBitmap }

    // Keep the receiver alive while the activity is paused in PiP, and release
    // it with this screen. The PendingIntent lets System UI invoke this private action.
    if (!isInPreview) {
      DisposableEffect(diceState) {
        val receiver = object : BroadcastReceiver() {
          override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_NEW_ANSWER && isInPictureInPictureMode &&
              !isEnteringPictureInPicture
            ) {
              val selectedAnswer =
                if (Random.nextBoolean()) R.string.yes_value else R.string.no_value
              // Update the shared result and landing immediately; the cube is hidden.
              diceState.rollTo(selectedAnswer, animate = false)
            }
          }
        }
        ContextCompat.registerReceiver(
          this@MainActivity, receiver, IntentFilter(ACTION_NEW_ANSWER),
          ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { unregisterReceiver(receiver) }
      }
    }

    // Keep the screen state above this branch so entering/exiting PiP retains the roll.
    if (isEnteringPictureInPicture && !isInPreview) {
      Box(
        Modifier
          .fillMaxSize()
          .testTag("pip-transition")
      )
      return
    }
    if (showPictureInPicture && !isInPreview) {
      PinnedAnswer(
        question = question,
        answer = diceState.answer,
        animationKey = diceState.answerGeneration,
        modifier = Modifier.fillMaxSize()
      )
      return
    }

    Column(
      modifier = Modifier
        .fillMaxSize()
        .safeDrawingPadding()
        .testTag("safe-content")
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp)
      ) {
        Box(
          modifier = Modifier
            .align(Alignment.TopEnd)
            .clickable { expanded = !expanded }
        ) {
          val languageName = languageNames[currentLanguage] ?: ""
          Text(text = languageName)

          DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
          ) {
            languageNames.forEach { (languageCode, language) ->
              DropdownMenuItem(text = { Text(text = language) }, onClick = {
                currentLanguage = languageCode
                expanded = false

                if (!isInPreview) {
                  // Save the selected language to SharedPreferences.
                  val prefs =
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                  prefs.edit { putString(LANGUAGE_PREF_KEY, currentLanguage) }

                  setLocale(resources)
                }
              })
            }
          }
        }
      }

      Column(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .verticalScroll(scrollState)
          .padding(horizontal = 48.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        Text(
          text = stringResource(R.string.app_name),
          style = MaterialTheme.typography.displaySmall,
          color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        TextField(
          value = question,
          onValueChange = { question = it },
          label = { Text(stringResource(R.string.question_text_hint)) },
          modifier = Modifier.fillMaxWidth(),
          textStyle = MaterialTheme.typography.bodyLarge,
          singleLine = true
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
          enabled = !diceState.isRolling,
          onClick = {
            val selectedAnswer =
              if (Random.nextBoolean()) R.string.yes_value else R.string.no_value

            focusManager.clearFocus()
            diceState.rollTo(selectedAnswer)
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag("decide-button")
        ) {
          Text(
            text = stringResource(R.string.decide_button_text),
            style = MaterialTheme.typography.labelLarge
          )
        }

        Spacer(modifier = Modifier.height(64.dp))

        AnswerDice(
          state = diceState,
          modifier = Modifier
            .testTag("answer-dice")
            .onGloballyPositioned { coordinates ->
              val bounds = coordinates.boundsInWindow()
              answerBounds = Rect(
                bounds.left.toInt(), bounds.top.toInt(),
                bounds.right.toInt(), bounds.bottom.toInt()
              )
            }
        )
        // Keep this control in the layout during every roll to avoid recentering.
        if (!isInPreview && supportsPictureInPicture()) {
          TextButton(
            enabled = !diceState.isRolling && diceState.answer != R.string.answer_no_decision,
            onClick = {
              focusManager.clearFocus()
              expanded = false
              pinAnswer()
            },
            modifier = Modifier.testTag("pin-answer")
          ) {
            Text(stringResource(R.string.pip_button_text))
          }
        }
      }
    }
  }

  private fun getLocale(language: String): Locale {
    // Locale.forLanguageTag is the modern and robust way to create a Locale
    // from a language string (available since API 21). It correctly handles
    // complex tags like "es-419" and "zh-CN", resolving deprecation warnings.
    return Locale.forLanguageTag(language)
  }

  private fun renderActionIcon(painter: Painter): Bitmap {
    val size = 48
    val image = ImageBitmap(size, size)
    CanvasDrawScope().draw(
      density = androidx.compose.ui.unit.Density(1f),
      layoutDirection = androidx.compose.ui.unit.LayoutDirection.Ltr,
      canvas = Canvas(image),
      size = Size(size.toFloat(), size.toFloat())
    ) {
      with(painter) {
        draw(
          size = Size(size.toFloat(), size.toFloat()),
          colorFilter = ColorFilter.tint(Color.White)
        )
      }
    }
    return image.asAndroidBitmap()
  }

  private fun getSavedLocale(): String {
    var savedLocale = getSharedPreferences(
      PREFS_NAME, MODE_PRIVATE
    ).getString(LANGUAGE_PREF_KEY, "")!!
    if (savedLocale.isEmpty()) {
      savedLocale = Locale.getDefault().language
    }
    return if (languageNames.keys.contains(savedLocale)) savedLocale else "en"
  }

  @Preview(showBackground = true)
  @Composable
  fun DefaultPreview() {
    YesnorandomizerTheme {
      YesNoScreen()
    }
  }

  private companion object {
    const val ACTION_NEW_ANSWER = "com.sblashkov.yesnorandomizer.NEW_ANSWER"
    const val LANGUAGE_PREF_KEY = "language_code_pref"
    const val PREFS_NAME = "yesnorandomizer_prefs"

    val languageNames = mapOf(
      "en" to "🇺🇸 English (US)",
      "es" to "🇪🇸 Español",
      "fr" to "🇫🇷 Français",
      "ar" to "🇸🇦العربية ",
      "es-419" to "🇲🇽 Español (Latinoamérica)",
      "de" to "🇩🇪 Deutsch",
      "hi" to "🇮🇳 हिन्दी",
      "id" to "🇮🇩 Indonesia",
      "it" to "🇮🇹 Italiano",
      "ja" to "🇯🇵 日本語",
      "ko" to "🇰🇷 한국어",
      "pl" to "🇵🇱 Polski",
      "pt" to "🇵🇹 Português",
      "ru" to "🇷🇺 Русский",
      "th" to "🇹🇭 ไทย",
      "tr" to "🇹🇷 Türkçe",
      "vi" to "🇻🇳 Tiếng Việt",
      "zh-CN" to "🇨🇳 简体中文"
    )
  }
}

@Composable
internal fun PinnedAnswer(
  question: String,
  @StringRes answer: Int,
  modifier: Modifier = Modifier,
  animationKey: Int = 0
) {
  Column(
    modifier = modifier
      .padding(12.dp)
      .testTag("pinned-answer"),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    if (question.isNotBlank()) {
      Text(
        text = question,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
      )
      Spacer(Modifier.height(4.dp))
    }
    AnimatedContent(
      targetState = answer to animationKey,
      transitionSpec = {
        (fadeIn(animationSpec = tween(260)) +
            scaleIn(initialScale = 0.65f, animationSpec = tween(260)) +
            slideInVertically(animationSpec = tween(260)) { height -> height / 2 }) togetherWith
            (fadeOut(animationSpec = tween(180)) +
                scaleOut(targetScale = 1.25f, animationSpec = tween(180)) +
                slideOutVertically(animationSpec = tween(180)) { height -> -height / 3 })
      },
      label = "pinned-answer-change"
    ) { (answerResource, _) ->
      Text(
        text = stringResource(answerResource),
        style = MaterialTheme.typography.headlineLarge,
        color = if (answerResource == R.string.yes_value) {
          MaterialTheme.colorScheme.primary
        } else {
          MaterialTheme.colorScheme.tertiary
        },
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.testTag("answer-text")
      )
    }
  }
}
