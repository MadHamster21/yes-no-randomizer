package com.sblashkov.yesnorandomizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sblashkov.yesnorandomizer.ui.theme.YesnorandomizerTheme
import kotlin.random.Random
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.res.stringResource
import android.content.res.Resources
import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalMapOf
import java.util.Locale
import android.content.Intent


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    private fun recreate() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
        finish()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun YesNoScreen() {
        var question by remember { mutableStateOf("") }
        var answer by remember { mutableStateOf("...") }
        val focusManager = LocalFocusManager.current
        val context = LocalContext.current
        val resources: Resources = context.resources
        val config = resources.configuration
        var currentLanguage by remember { mutableStateOf("en") }
        var expanded by remember { mutableStateOf(false) }
        val languages = listOf("en", "es", "fr", "ar", "b+es+419", "de", "hi", "id", "it", "ja", "ko", "pl", "pt", "ru", "th", "tr", "vi", "zh-rCN")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(modifier = Modifier
                .align(Alignment.TopEnd)
                .clickable { expanded = !expanded }
            ) {
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    languages.forEach { language ->
                        DropdownMenuItem(
                            text = language,
                            onClick = {
                                currentLanguage = language
                                expanded = false
                            }
                        )
                    }
                }
            }
            TextField(
                value = question,
                onValueChange = { question = it },
                label = { Text(context.getString(R.string.question_text_hint)) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = {
                val locale = java.util.Locale(currentLanguage)
                config.setLocale(locale)
                val newLocale = config.locale
                answer = if (Random.nextBoolean()) {
                    when (newLocale.language) {
                        "en" -> {
                            context.resources.getString(R.string.yes_value)
                        }
                        "es" -> {
                            context.resources.getString(R.string.yes_value)
                        }
                        "fr" -> {
                            context.resources.getString(R.string.no_value)
                        }
                        else -> {
                            context.resources.getString(R.string.yes_value)
                        }
                    }
                } else {
                    context.resources.getString(R.string.no_value)
                }
                focusManager.clearFocus()
            }) {
                Text(context.getString(R.string.decide_button_text))
            }

            Spacer(modifier = Modifier.height(64.dp))

            Text(
                text = answer, fontSize = 80.sp, style = MaterialTheme.typography.titleMedium
            )
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun DefaultPreview() {
        YesnorandomizerTheme {
            YesNoScreen()
        }
    }
}

    @Preview(showBackground = true)
    @Composable
    fun DefaultPreview() {
        YesnorandomizerTheme {
            YesNoScreen()
        }
    }
}