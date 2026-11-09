package com.othmane.vocaleai.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit

object Prefs {
    private fun sp(context: Context): SharedPreferences =
        context.getSharedPreferences("vocaleai", Context.MODE_PRIVATE)

    fun modelId(context: Context): String = sp(context).getString("model", "small") ?: "small"
    fun setModelId(context: Context, id: String) = sp(context).edit { putString("model", id) }

    /** "auto", "it", "ar", "en", … */
    fun language(context: Context): String = sp(context).getString("lang", "auto") ?: "auto"
    fun setLanguage(context: Context, lang: String) = sp(context).edit { putString("lang", lang) }

    fun downloadId(context: Context, modelId: String): Long = sp(context).getLong("dl_$modelId", -1L)
    fun setDownloadId(context: Context, modelId: String, id: Long) =
        sp(context).edit { putLong("dl_$modelId", id) }

    fun voiceTree(context: Context): Uri? = sp(context).getString("tree", null)?.let(Uri::parse)
    fun setVoiceTree(context: Context, uri: Uri) = sp(context).edit { putString("tree", uri.toString()) }

    fun bubbleEnabled(context: Context): Boolean = sp(context).getBoolean("bubble", true)
    fun setBubbleEnabled(context: Context, on: Boolean) = sp(context).edit { putBoolean("bubble", on) }

    /** lastModified dell'ultimo vocale già segnalato, per non riproporre lo stesso file. */
    fun lastHandledModified(context: Context): Long = sp(context).getLong("last_handled", 0L)
    fun setLastHandledModified(context: Context, value: Long) =
        sp(context).edit { putLong("last_handled", value) }
}
