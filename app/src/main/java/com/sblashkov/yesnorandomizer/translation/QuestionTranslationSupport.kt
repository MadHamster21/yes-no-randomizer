package com.sblashkov.yesnorandomizer.translation

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sblashkov.yesnorandomizer.R

@Composable
internal fun GoogleTranslationAttribution(modifier: Modifier = Modifier) {
  Image(
    painterResource(
      if (isSystemInDarkTheme()) R.drawable.google_translate_attribution_dark
      else R.drawable.google_translate_attribution_light
    ),
    contentDescription = stringResource(R.string.translation_credit),
    modifier = modifier
  )
}

@Composable
internal fun QuestionTranslationSupport(
  state: QuestionUiState,
  onShowOriginal: () -> Unit,
  onRetry: () -> Unit,
  onTranslate: () -> Unit = {}
) {
  var showDetails by rememberSaveable { mutableStateOf(false) }
  val message = when (state.status) {
    QuestionTranslationStatus.Idle, QuestionTranslationStatus.DownloadRequired -> null
    QuestionTranslationStatus.Translating -> R.string.translation_in_progress
    QuestionTranslationStatus.Downloading -> R.string.translation_downloading
    QuestionTranslationStatus.NeedsWifi -> R.string.translation_needs_wifi
    QuestionTranslationStatus.UnknownLanguage -> R.string.translation_unknown
    QuestionTranslationStatus.Unavailable -> R.string.translation_unavailable
  }
  val busy = state.status == QuestionTranslationStatus.Translating ||
      state.status == QuestionTranslationStatus.Downloading
  val messageText = message?.let { stringResource(it) }
  // Keep one touch-target-height slot even when idle. Neither translated labels
  // nor long localized status messages can add rows and move the decision/die.
  Row(
    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("question-translation-support"),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(4.dp)
  ) {
    Box(Modifier.weight(1f)) {
      if (state.question.translated) {
        TextButton(
          onClick = { showDetails = true },
          contentPadding = PaddingValues(horizontal = 4.dp),
          modifier = Modifier.height(48.dp).testTag("translation-attribution")
        ) {
          GoogleTranslationAttribution(Modifier.height(12.dp))
        }
      } else if (messageText != null) {
        TextButton(
          onClick = { showDetails = true },
          contentPadding = PaddingValues(horizontal = 4.dp),
          modifier = Modifier.fillMaxWidth().height(48.dp)
            .testTag("question-translation-status")
            .semantics { liveRegion = LiveRegionMode.Polite }
        ) {
          Text(
            messageText,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = if (busy) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Start,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
      }
    }
    if (busy) {
      Box(
        Modifier.size(48.dp).semantics {
          contentDescription = messageText.orEmpty()
          liveRegion = LiveRegionMode.Polite
        },
        contentAlignment = Alignment.Center
      ) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
      }
    } else if (messageText != null) {
      TranslationAction(
        stringResource(R.string.translation_retry), "retry-translation", onRetry
      ) {
        Icon(
          painterResource(R.drawable.ic_retry_translation),
          contentDescription = stringResource(R.string.translation_retry),
          modifier = Modifier.size(20.dp)
        )
      }
    }
    if (state.question.translated || state.canTranslate) {
      TextButton(
        onClick = if (state.question.translated) onShowOriginal else onTranslate,
        contentPadding = PaddingValues(horizontal = 4.dp),
        modifier = Modifier.height(48.dp).testTag(
          if (state.question.translated) "show-original-question" else "translate-question"
        )
      ) {
        Text(
          stringResource(if (state.question.translated) R.string.translation_show_original else R.string.translation_translate),
          style = MaterialTheme.typography.labelMedium,
          textDecoration = TextDecoration.Underline,
          maxLines = 1
        )
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
          // The compact row may ellipsize; details always expose the full status.
          if (messageText != null) {
            Text(messageText, modifier = Modifier.testTag("translation-status-details"))
          }
          Text(stringResource(R.string.translation_info))
          Text(
            GoogleTranslationBranding.DISCLAIMER,
            style = MaterialTheme.typography.bodySmall
          )
        }
      },
      confirmButton = {
        TextButton(onClick = { showDetails = false }) { Text(stringResource(android.R.string.ok)) }
      },
      dismissButton = {
        TextButton(onClick = { uriHandler.openUri("https://translate.google.com/") }) {
          Text(GoogleTranslationBranding.SERVICE_NAME)
        }
      }
    )
  }
}

@Composable
internal fun QuestionTranslationDownloadDialog(onDownload: () -> Unit, onDecline: () -> Unit) {
  AlertDialog(
    onDismissRequest = onDecline,
    title = { Text(stringResource(R.string.translation_download_title)) },
    text = { Text(stringResource(R.string.translation_download_consent)) },
    confirmButton = {
      TextButton(onClick = onDownload, modifier = Modifier.testTag("download-translation-models")) {
        Text(stringResource(R.string.translation_download_accept))
      }
    },
    dismissButton = {
      TextButton(onClick = onDecline, modifier = Modifier.testTag("decline-translation-models")) {
        Text(stringResource(R.string.translation_download_decline))
      }
    }
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TranslationAction(
  label: String,
  tag: String,
  onClick: () -> Unit,
  icon: @Composable () -> Unit
) {
  TooltipBox(
    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
    tooltip = { PlainTooltip { Text(label) } },
    state = rememberTooltipState()
  ) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp).testTag(tag), content = icon)
  }
}
