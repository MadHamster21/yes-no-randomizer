package com.sblashkov.yesnorandomizer

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
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
        val languageCode = getSavedLocale()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val appLocale = LocaleList.forLanguageTags(languageCode)
            val localeManager = getSystemService(Context.LOCALE_SERVICE) as LocaleManager
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

    private fun getSavedLocale(): String {
        var savedLocale = getSharedPreferences(
            PREFS_NAME, Context.MODE_PRIVATE
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
        const val LANGUAGE_PREF_KEY = "language_code_pref"
        const val PREFS_NAME = "yesnorandomizer_prefs"

        val languageNames = mapOf(
            "en" to "\ud83c\uddfa\ud83c\uddf8 English (US)",
            "es" to "\ud83c\uddea\ud83c\uddf8 Español",
            "fr" to "\ud83c\uddeb\ud83c\uddf7 Français",
            "ar" to "\ud83c\uddf8\ud83c\udde6العربية ",
            "es-419" to "\ud83c\uddf2\ud83c\uddfd Español (Latinoamérica)",
            "de" to "\ud83c\udde9\ud83c\uddea Deutsch",
            "hi" to "\ud83c\uddee\ud83c\uddf3 हिन्दी",
            "id" to "\ud83c\uddee\ud83c\udde9 Indonesia",
            "it" to "\ud83c\uddee\ud83c\uddf9 Italiano",
            "ja" to "\ud83c\uddef\ud83c\uddf5 日本語",
            "ko" to "\ud83c\uddf0\ud83c\uddf7 한국어",
            "pl" to "\ud83c\uddf5\ud83c\uddf1 Polski",
            "pt" to "\ud83c\uddf5\ud83c\uddf9 Português",
            "ru" to "\ud83c\uddf7\ud83c\uddfa Русский",
            "th" to "\ud83c\uddf9\ud83c\udded ไทย",
            "tr" to "\ud83c\uddf9\ud83c\uddf7 Türkçe",
            "vi" to "\ud83c\uddfb\ud83c\uddf3 Tiếng Việt",
            "zh-CN" to "\ud83c\udde8\ud83c\uddf3 简体中文"
        )
    }
}