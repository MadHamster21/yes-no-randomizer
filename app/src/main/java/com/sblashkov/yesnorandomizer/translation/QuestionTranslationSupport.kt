package com.sblashkov.yesnorandomizer.translation

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sblashkov.yesnorandomizer.R

@Composable
internal fun GoogleTranslationAttribution(modifier: Modifier = Modifier) {
  Image(
    painterResource(
      if (isSystemInDarkTheme()) R.drawable.google_translate_attribution_dark
      else R.drawable.google_translate_attribution_light
    ),
    contentDescription = stringResource(R.string.translation_attribution),
    modifier = modifier
  )
}

@Composable
internal fun QuestionTranslationSupport(
  state: QuestionUiState,
  onShowOriginal: () -> Unit,
  onRetry: () -> Unit
) {
  var showDetails by rememberSaveable { mutableStateOf(false) }
  val message = when (state.status) {
    QuestionTranslationStatus.Idle -> null
    QuestionTranslationStatus.Translating -> R.string.translation_in_progress
    QuestionTranslationStatus.Downloading -> R.string.translation_downloading
    QuestionTranslationStatus.NeedsWifi -> R.string.translation_needs_wifi
    QuestionTranslationStatus.UnknownLanguage -> R.string.translation_unknown
    QuestionTranslationStatus.Unavailable -> R.string.translation_unavailable
  }
  val busy = state.status == QuestionTranslationStatus.Translating ||
      state.status == QuestionTranslationStatus.Downloading
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    if (message != null) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.testTag("question-translation-status")
      ) {
        if (busy) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
        Text(stringResource(message), modifier = Modifier.weight(1f))
        if (!busy) {
          TextButton(onClick = onRetry, modifier = Modifier.testTag("retry-translation")) {
            Text(stringResource(R.string.translation_retry))
          }
        }
      }
    }
    if (state.question.translated) {
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        TextButton(
          onClick = { showDetails = true },
          modifier = Modifier.testTag("translation-attribution")
        ) {
          GoogleTranslationAttribution(Modifier.height(18.dp))
        }
        TextButton(
          onClick = onShowOriginal,
          modifier = Modifier.testTag("show-original-question")
        ) {
          Text(stringResource(R.string.translation_show_original))
        }
      }
    }
  }
  if (showDetails) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
      onDismissRequest = { showDetails = false },
      title = { Text(stringResource(R.string.translation_details)) },
      text = {
        Column(
          Modifier.verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Text(stringResource(R.string.translation_info))
          Text(
            stringResource(R.string.translation_disclaimer),
            style = MaterialTheme.typography.bodySmall
          )
        }
      },
      confirmButton = {
        TextButton(onClick = { showDetails = false }) { Text(stringResource(android.R.string.ok)) }
      },
      dismissButton = {
        TextButton(onClick = { uriHandler.openUri("https://translate.google.com/") }) {
          Text(stringResource(R.string.translation_service_name))
        }
      }
    )
  }
}
