package com.othmane.vocaleai.data

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class CachedTranscript(val text: String, val language: String, val audioSeconds: Int)

/** Salva le trascrizioni già fatte, così riaprire lo stesso vocale è istantaneo. */
object TranscriptCache {
    private fun dir(context: Context) = File(context.filesDir, "transcripts").apply { mkdirs() }

    private fun key(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(uri.toString().toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun file(context: Context, uri: Uri) = File(dir(context), key(uri) + ".json")

    fun has(context: Context, uri: Uri): Boolean = file(context, uri).exists()

    fun get(context: Context, uri: Uri): CachedTranscript? = try {
        val f = file(context, uri)
        if (!f.exists()) null else {
            val o = JSONObject(f.readText())
            CachedTranscript(o.getString("text"), o.optString("lang"), o.optInt("secs"))
        }
    } catch (e: Exception) {
        null
    }

    fun put(context: Context, uri: Uri, value: CachedTranscript) {
        val o = JSONObject()
            .put("text", value.text)
            .put("lang", value.language)
            .put("secs", value.audioSeconds)
        file(context, uri).writeText(o.toString())
    }
}
