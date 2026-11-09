package com.othmane.vocaleai.ui

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.othmane.vocaleai.audio.AudioDecoder
import com.othmane.vocaleai.data.CachedTranscript
import com.othmane.vocaleai.data.Prefs
import com.othmane.vocaleai.data.TranscriptCache
import com.othmane.vocaleai.text.Summarizer
import com.othmane.vocaleai.text.Summary
import com.othmane.vocaleai.whisper.ModelManager
import com.othmane.vocaleai.whisper.WhisperEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface TxState {
    data class Working(val step: String, val percent: Int?) : TxState
    data class Done(
        val text: String,
        val language: String,
        val summary: Summary,
        val audioSeconds: Int,
        val elapsedMs: Long?,
    ) : TxState
    data class Error(val message: String) : TxState
    data object NoModel : TxState
}

class TranscribeViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow<TxState>(TxState.Working("Preparazione…", null))
    val state: StateFlow<TxState> = _state

    private var currentUri: Uri? = null

    fun start(uri: Uri, force: Boolean = false) {
        if (uri == currentUri && !force) return
        currentUri = uri
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            try {
                if (!force) {
                    val cached = withContext(Dispatchers.IO) { TranscriptCache.get(ctx, uri) }
                    if (cached != null) {
                        _state.value = done(cached.text, cached.language, cached.audioSeconds, null)
                        return@launch
                    }
                }
                val model = withContext(Dispatchers.IO) { ModelManager.usableModelFile(ctx) }
                if (model == null) {
                    _state.value = TxState.NoModel
                    return@launch
                }
                _state.value = TxState.Working("Decodifica audio…", null)
                val audio = withContext(Dispatchers.Default) { AudioDecoder.decodeToMono16k(ctx, uri) }
                val seconds = audio.size / AudioDecoder.TARGET_RATE

                val t0 = SystemClock.elapsedRealtime()
                val result = WhisperEngine.transcribe(
                    model = model,
                    audio = audio,
                    language = Prefs.language(ctx),
                    onStage = { _state.value = TxState.Working(it, null) },
                    onProgress = { _state.value = TxState.Working("Trascrizione…", it) },
                )
                val elapsed = SystemClock.elapsedRealtime() - t0
                if (result.text.isBlank()) {
                    _state.value = TxState.Error("Non ho sentito parlato in questo vocale.")
                    return@launch
                }
                withContext(Dispatchers.IO) {
                    TranscriptCache.put(ctx, uri, CachedTranscript(result.text, result.language, seconds))
                }
                _state.value = done(result.text, result.language, seconds, elapsed)
            } catch (e: Exception) {
                _state.value = TxState.Error(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    private suspend fun done(text: String, lang: String, seconds: Int, elapsed: Long?): TxState.Done {
        val summary = withContext(Dispatchers.Default) { Summarizer.summarize(text) }
        return TxState.Done(text, lang, summary, seconds, elapsed)
    }
}
