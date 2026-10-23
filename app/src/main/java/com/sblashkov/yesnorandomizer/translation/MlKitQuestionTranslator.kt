package com.sblashkov.yesnorandomizer.translation

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal enum class QuestionTranslationStatus {
  Idle, Translating, Downloading, NeedsWifi, UnknownLanguage, Unavailable
}

internal class QuestionTranslationException(val status: QuestionTranslationStatus) : Exception()

internal interface QuestionTranslator {
  suspend fun translate(
    text: String,
    targetLanguage: String,
    downloading: () -> Unit
  ): QuestionTranslationResult
}

/** Both detection and translation run locally. Only model downloads need Wi-Fi. */
internal class MlKitQuestionTranslator(context: Context) : QuestionTranslator {
  private val applicationContext = context.applicationContext

  override suspend fun translate(
    text: String,
    targetLanguage: String,
    downloading: () -> Unit
  ): QuestionTranslationResult {
    val identifier = LanguageIdentification.getClient()
    val identified = try {
      identifier.identifyLanguage(text).awaitResult()
    } finally {
      identifier.close()
    }
    // Do not guess the source from the UI language, especially for mixed text.
    val source = TranslateLanguage.fromLanguageTag(identified)
      ?: throw QuestionTranslationException(QuestionTranslationStatus.UnknownLanguage)
    val target = TranslateLanguage.fromLanguageTag(targetLanguage)
      ?: throw QuestionTranslationException(QuestionTranslationStatus.Unavailable)
    if (source == target) return QuestionTranslationResult(text, source, false)

    val manager = RemoteModelManager.getInstance()
    var needsDownload = false
    for (language in setOf(source, target) - TranslateLanguage.ENGLISH) {
      val model = TranslateRemoteModel.Builder(language).build()
      if (!manager.isModelDownloaded(model).awaitResult()) needsDownload = true
    }
    if (needsDownload && !hasWifi()) {
      throw QuestionTranslationException(QuestionTranslationStatus.NeedsWifi)
    }
    val translator = Translation.getClient(
      TranslatorOptions.Builder()
        .setSourceLanguage(source)
        .setTargetLanguage(target)
        .build()
    )
    try {
      if (needsDownload) downloading()
      translator.downloadModelIfNeeded(DownloadConditions.Builder().requireWifi().build())
        .awaitResult()
      // Translate the whole sentence naturally, including mixed-language input.
      val translated = translator.translate(text).awaitResult()
      if (translated.isBlank()) {
        throw QuestionTranslationException(QuestionTranslationStatus.Unavailable)
      }
      return QuestionTranslationResult(translated, source, translated != text)
    } finally {
      translator.close()
    }
  }

  private fun hasWifi(): Boolean {
    val manager = applicationContext.getSystemService(ConnectivityManager::class.java)
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
  addOnCompleteListener { task ->
    if (continuation.isActive) {
      if (task.isSuccessful) continuation.resume(task.result)
      else continuation.resumeWithException(
        task.exception ?: IllegalStateException("ML Kit task cancelled")
      )
    }
  }
}
