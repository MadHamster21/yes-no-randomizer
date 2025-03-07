package com.sblashkov.yesnorandomizer

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.core.content.edit
import com.sblashkov.yesnorandomizer.ui.theme.YesnorandomizerTheme
import java.util.Locale
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setLocale(this.resources)

        super.onCreate(savedInstanceState)
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
        setLocale(this.resources)
    }

    private fun setLocale(resources: Resources) {
        val config = resources.configuration
        val locale = getLocale(getSavedLocale())
        Locale.setDefault(locale)
        config.setLocale(locale)

        createConfigurationContext(config)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    @Composable
    fun YesNoScreen() {
        var question by remember { mutableStateOf("") }
        var answer by remember { mutableIntStateOf(R.string.answer_no_decision) }
        val focusManager = LocalFocusManager.current
        val context = LocalContext.current
        val resources: Resources = context.resources
        var currentLanguage by remember {
            mutableStateOf(
                getSavedLocale()
            )
        }
        var expanded by remember { mutableStateOf(false) }

        val languageNames = mapOf(
            "en" to "English (US)",
            "es" to "Español",
            "fr" to "Français",
            "ar" to "العربية",
            "es-419" to "Español (Latinoamérica)",
            "de" to "Deutsch",
            "hi" to "हिन्दी",
            "id" to "Indonesia",
            "it" to "Italiano",
            "ja" to "日本語",
            "ko" to "한국어",
            "pl" to "Polski",
            "pt" to "Português",
            "ru" to "Русский",
            "th" to "ไทย",
            "tr" to "Türkçe",
            "vi" to "Tiếng Việt",
            "zh-CN" to "简体中文"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .safeDrawingPadding()
                    .align(Alignment.TopEnd)
                    .clickable { expanded = !expanded }) {

                val languageName = languageNames[currentLanguage] ?: ""
                Text(text = languageName)

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    languageNames.forEach { (languageCode, language) ->
                        DropdownMenuItem(text = { Text(text = language) }, onClick = {
                            currentLanguage = languageCode

                            // Save the selected language to SharedPreferences
                            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            prefs.edit { putString(LANGUAGE_PREF_KEY, currentLanguage) }

                            expanded = false

                            setLocale(resources)
                        })
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            TextField(
                value = question,
                onValueChange = { question = it },
                label = { Text(context.getString(R.string.question_text_hint)) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = {
                answer = if (Random.nextBoolean()) R.string.yes_value else R.string.no_value
                focusManager.clearFocus()
            }) {
                Text(context.getString(R.string.decide_button_text))
            }

            Spacer(modifier = Modifier.height(64.dp))

            Text(
                text = context.getString(answer),
                fontSize = 80.sp,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }

    private fun getLocale(language: String) = when (language) {
        "zh-CN" -> Locale.SIMPLIFIED_CHINESE
        "es-419" -> Locale.forLanguageTag(language)
        else -> Locale(language)
    }

    private fun getSavedLocale(): String = getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    ).getString(LANGUAGE_PREF_KEY, "en")!!

    @Preview(showBackground = true)
    @Composable
    fun DefaultPreview() {
        YesnorandomizerTheme {
            YesNoScreen()
        }
    }

    private companion object {
        const val LANGUAGE_PREF_KEY = "language_code_pref"
        const val PREFS_NAME = "yesnorandomizer_prefs"
    }
}