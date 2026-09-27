package com.sblashkov.yesnorandomizer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sblashkov.yesnorandomizer.R

@Composable
internal fun LanguagePicker(
  languages: Map<String, String>,
  selected: String,
  onSelect: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  var open by rememberSaveable { mutableStateOf(false) }
  TextButton(onClick = { open = true }, modifier = modifier.testTag("language-picker")) {
    Text(languages[selected].orEmpty())
    Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.language_label))
  }
  if (open) {
    val entries = languages.entries.toList()
    val listState = rememberLazyListState(entries.indexOfFirst { it.key == selected }.coerceAtLeast(0))
    AlertDialog(
      onDismissRequest = { open = false },
      title = { Text(stringResource(R.string.language_label)) },
      text = {
        LazyColumn(
          state = listState,
          modifier = Modifier.heightIn(max = 360.dp).selectableGroup().testTag("language-list")
        ) {
          items(entries, key = { it.key }) { (code, name) ->
            Row(
              Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .selectable(selected = code == selected, role = Role.RadioButton, onClick = {
                  open = false
                  onSelect(code)
                }).padding(vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              RadioButton(selected = code == selected, onClick = null)
              Text(name)
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { open = false }) { Text(stringResource(android.R.string.cancel)) }
      }
    )
  }
}
