package com.sblashkov.yesnorandomizer.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sblashkov.yesnorandomizer.R

/** Size from the usable window, including keyboard insets, instead of device type. */
@Composable
internal fun DecisionLayout(
  question: String,
  onQuestionChange: (String) -> Unit,
  diceState: AnswerDiceState,
  onDecide: () -> Unit,
  scrollState: ScrollState,
  modifier: Modifier = Modifier,
  diceModifier: Modifier = Modifier,
  floatingAction: @Composable () -> Unit
) {
  BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    val wide = maxWidth >= 600.dp && maxWidth > maxHeight
    val compact = maxWidth < 400.dp || maxHeight < 560.dp
    val titleStyle = if (compact) MaterialTheme.typography.headlineMedium
      else MaterialTheme.typography.displaySmall
    val diceSize = if (wide) {
      minOf(maxHeight - 96.dp, maxWidth / 2 - 48.dp, 300.dp).coerceAtLeast(128.dp)
    } else {
      minOf(maxHeight - 300.dp, maxWidth - 48.dp, 300.dp).coerceAtLeast(160.dp)
    }
    val form: @Composable () -> Unit = {
      Column(
        Modifier.widthIn(max = 480.dp).fillMaxWidth().testTag("decision-form"),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(
          stringResource(R.string.app_name),
          style = titleStyle,
          color = MaterialTheme.colorScheme.primary,
          textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        TextField(
          value = question,
          onValueChange = onQuestionChange,
          label = { Text(stringResource(R.string.question_text_hint)) },
          modifier = Modifier.fillMaxWidth(),
          textStyle = MaterialTheme.typography.bodyLarge,
          singleLine = true
        )
        Spacer(Modifier.height(16.dp))
        Button(
          enabled = !diceState.isRolling,
          onClick = onDecide,
          modifier = Modifier.fillMaxWidth().height(56.dp).testTag("decide-button")
        ) {
          Text(stringResource(R.string.decide_button_text), style = MaterialTheme.typography.labelLarge)
        }
      }
    }
    val result: @Composable () -> Unit = {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AnswerDice(diceState, diceModifier.size(diceSize))
        floatingAction()
      }
    }
    // Both arrangements can still scroll with large accessibility fonts or the
    // keyboard. With ordinary text sizing the entire flow fits without scrolling.
    if (wide) {
      Row(
        Modifier.widthIn(max = 1000.dp).fillMaxWidth()
          .verticalScroll(scrollState).heightIn(min = maxHeight).padding(24.dp, 16.dp)
          .testTag("wide-decision-layout"),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { form() }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { result() }
      }
    } else {
      Column(
        Modifier.widthIn(max = 528.dp).fillMaxWidth()
          .verticalScroll(scrollState).heightIn(min = maxHeight).padding(24.dp, 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
      ) {
        form()
        Spacer(Modifier.height(24.dp))
        result()
      }
    }
  }
}
