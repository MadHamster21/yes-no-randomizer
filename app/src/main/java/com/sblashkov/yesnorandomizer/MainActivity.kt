package com.sblashkov.yesnorandomizer

import android.app.Activity
import android.app.LocaleManager
import android.content.res.Configuration
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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.sblashkov.yesnorandomizer.ui.theme.YesnorandomizerTheme
import java.util.Locale
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Set the locale before the activity is created.
        setLocale()
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
        // Also apply the locale on configuration changes.
        setLocale()
    }

    private fun setLocale() {
        val languageCode = getSavedLocale()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // For API 33+, use LocaleManager to set the locale.
            val appLocale = LocaleList.forLanguageTags(languageCode)
            val localeManager = getSystemService(LOCALE_SERVICE) as LocaleManager
            localeManager.applicationLocales = appLocale
        } else {
            // For older APIs, we update the configuration manually.
            // By recreating the activity, we avoid the deprecated `updateConfiguration` call.
            val locale = getLocale(languageCode)
            Locale.setDefault(locale)
            val config = resources.configuration
            config.setLocale(locale)
        }
    }

    @Composable
    fun YesNoScreen() {
        val isInPreview = LocalInspectionMode.current
        var question by remember { mutableStateOf("") }
        var answer by remember { mutableIntStateOf(R.string.answer_no_decision) }
        val focusManager = LocalFocusManager.current
        val context = LocalContext.current
        var currentLanguage by remember {
            mutableStateOf(
                if (isInPreview) "en" else getSavedLocale()
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
                            expanded = false

                            if (!isInPreview) {
                                // Save the selected language to SharedPreferences.
                                val prefs =
                                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                                prefs.edit { putString(LANGUAGE_PREF_KEY, currentLanguage) }

                                // Recreate the activity to apply the new locale. This is the
                                // recommended approach to ensure the configuration is applied
                                // consistently throughout the app, and it resolves the
                                // `updateConfiguration` deprecation issue.
                                val activity = context as? Activity
                                activity?.recreate()
                            }
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

    private fun getLocale(language: String): Locale {
        // Locale.forLanguageTag is the modern and robust way to create a Locale
        // from a language string (available since API 21). It correctly handles
        // complex tags like "es-419" and "zh-CN", resolving deprecation warnings.
        return Locale.forLanguageTag(language)
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
