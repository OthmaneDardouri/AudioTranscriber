package com.othmane.vocaleai.whisper

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import com.othmane.vocaleai.data.Prefs
import java.io.File

enum class WhisperModel(
    val id: String,
    val label: String,
    val fileName: String,
    val sizeMb: Int,
    val description: String,
) {
    BASE("base", "Base", "ggml-base-q5_1.bin", 57, "Veloce, qualità discreta"),
    SMALL("small", "Small", "ggml-small-q5_1.bin", 181, "Consigliato: buon equilibrio, anche per l'arabo"),
    TURBO("turbo", "Large v3 Turbo", "ggml-large-v3-turbo-q5_0.bin", 547, "Qualità massima, molto più lento");

    val url: String
        get() = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$fileName"

    companion object {
        fun fromId(id: String?): WhisperModel = entries.firstOrNull { it.id == id } ?: SMALL
    }
}

sealed interface ModelStatus {
    data object Missing : ModelStatus
    data class Downloading(val percent: Int?) : ModelStatus
    data object Ready : ModelStatus
    data class Failed(val reason: String) : ModelStatus
}

object ModelManager {
    private const val MIN_VALID_BYTES = 20L * 1024 * 1024

    private fun dir(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "models").apply { mkdirs() }

    fun file(context: Context, model: WhisperModel): File = File(dir(context), model.fileName)

    private fun dm(context: Context) =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun status(context: Context, model: WhisperModel): ModelStatus {
        val id = Prefs.downloadId(context, model.id)
        if (id != -1L) {
            dm(context).query(DownloadManager.Query().setFilterById(id))?.use { c ->
                if (c.moveToFirst()) {
                    val st = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    when (st) {
                        DownloadManager.STATUS_SUCCESSFUL -> Prefs.setDownloadId(context, model.id, -1L)
                        DownloadManager.STATUS_FAILED -> {
                            val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            Prefs.setDownloadId(context, model.id, -1L)
                            file(context, model).delete()
                            return ModelStatus.Failed("Download non riuscito (codice $reason)")
                        }
                        else -> {
                            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                            val pct = if (total > 0) ((done * 100) / total).toInt() else null
                            return ModelStatus.Downloading(pct)
                        }
                    }
                } else {
                    // Download annullato dall'utente dal pannello di sistema
                    Prefs.setDownloadId(context, model.id, -1L)
                }
            }
        }
        val f = file(context, model)
        return if (f.exists() && f.length() >= MIN_VALID_BYTES) ModelStatus.Ready else ModelStatus.Missing
    }

    fun download(context: Context, model: WhisperModel) {
        val f = file(context, model)
        f.delete()
        val request = DownloadManager.Request(Uri.parse(model.url))
            .setTitle("Modello Whisper ${model.label}")
            .setDescription("Vocale AI · ~${model.sizeMb} MB")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setDestinationUri(Uri.fromFile(f))
        val id = dm(context).enqueue(request)
        Prefs.setDownloadId(context, model.id, id)
    }

    fun delete(context: Context, model: WhisperModel) {
        val id = Prefs.downloadId(context, model.id)
        if (id != -1L) {
            dm(context).remove(id)
            Prefs.setDownloadId(context, model.id, -1L)
        }
        file(context, model).delete()
    }

    /** Il modello scelto, se pronto; altrimenti un qualsiasi altro modello già scaricato. */
    fun usableModelFile(context: Context): File? {
        val chosen = WhisperModel.fromId(Prefs.modelId(context))
        if (status(context, chosen) == ModelStatus.Ready) return file(context, chosen)
        return WhisperModel.entries.firstOrNull { status(context, it) == ModelStatus.Ready }
            ?.let { file(context, it) }
    }
}
