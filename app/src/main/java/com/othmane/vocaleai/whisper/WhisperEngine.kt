package com.othmane.vocaleai.whisper

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class WhisperResult(val text: String, val language: String)

/**
 * Tiene il modello in memoria finché serve e lo libera dopo un po' di inattività,
 * così un secondo vocale subito dopo non deve ricaricarlo.
 */
object WhisperEngine {
    private const val RELEASE_AFTER_MS = 90_000L

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ctx = 0L
    private var loadedPath: String? = null
    private var releaseJob: Job? = null

    private fun threads(): Int =
        (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 4)

    suspend fun transcribe(
        model: File,
        audio: FloatArray,
        language: String,
        onStage: (String) -> Unit,
        onProgress: (Int) -> Unit,
    ): WhisperResult = mutex.withLock {
        withContext(Dispatchers.Default) {
            releaseJob?.cancel()
            if (ctx == 0L || loadedPath != model.absolutePath) {
                onStage("Caricamento modello…")
                freeLocked()
                val p = WhisperLib.initContext(model.absolutePath)
                if (p == 0L) {
                    throw IllegalStateException(
                        "Impossibile caricare il modello. Prova a cancellarlo e riscaricarlo."
                    )
                }
                ctx = p
                loadedPath = model.absolutePath
            }
            onStage("Trascrizione…")
            val bytes = WhisperLib.transcribe(
                ctx, audio, threads(), language, ProgressListener { onProgress(it) }
            ) ?: throw IllegalStateException("La trascrizione non è riuscita.")
            val lang = WhisperLib.detectedLanguage(ctx)
            scheduleRelease()
            WhisperResult(String(bytes, Charsets.UTF_8).trim(), lang)
        }
    }

    private fun scheduleRelease() {
        releaseJob?.cancel()
        releaseJob = scope.launch {
            delay(RELEASE_AFTER_MS)
            mutex.withLock { freeLocked() }
        }
    }

    private fun freeLocked() {
        if (ctx != 0L) {
            WhisperLib.freeContext(ctx)
            ctx = 0L
            loadedPath = null
        }
    }
}
