package com.othmane.vocaleai.ui

import android.app.Application
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.othmane.vocaleai.data.Prefs
import com.othmane.vocaleai.data.TranscriptCache
import com.othmane.vocaleai.data.VoiceNote
import com.othmane.vocaleai.data.VoiceNotesRepo
import com.othmane.vocaleai.whisper.ModelManager
import com.othmane.vocaleai.whisper.ModelStatus
import com.othmane.vocaleai.whisper.WhisperModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RecentItem(val note: VoiceNote, val transcribed: Boolean)

data class MainUi(
    val selectedModel: WhisperModel = WhisperModel.SMALL,
    val modelStatus: Map<WhisperModel, ModelStatus> = emptyMap(),
    val language: String = "auto",
    val listenerEnabled: Boolean = false,
    val overlayAllowed: Boolean = false,
    val notificationsAllowed: Boolean = false,
    val folderGranted: Boolean = false,
    val bubbleEnabled: Boolean = true,
    val recent: List<RecentItem> = emptyList(),
    val loadingRecent: Boolean = false,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()
    private val _ui = MutableStateFlow(MainUi())
    val ui: StateFlow<MainUi> = _ui

    private var pollJob: Job? = null

    fun refresh() {
        viewModelScope.launch {
            val statuses = withContext(Dispatchers.IO) { WhisperModel.entries.associateWith { ModelManager.status(ctx, it) } }
            val repo = VoiceNotesRepo(ctx)
            _ui.value = _ui.value.copy(
                selectedModel = WhisperModel.fromId(Prefs.modelId(ctx)),
                modelStatus = statuses,
                language = Prefs.language(ctx),
                listenerEnabled = isListenerEnabled(),
                overlayAllowed = Settings.canDrawOverlays(ctx),
                notificationsAllowed = NotificationManagerCompat.from(ctx).areNotificationsEnabled(),
                folderGranted = repo.hasAccess(),
                bubbleEnabled = Prefs.bubbleEnabled(ctx),
                loadingRecent = repo.hasAccess() && _ui.value.recent.isEmpty(),
            )
            if (statuses.values.any { it is ModelStatus.Downloading }) startPolling()
            val recent = withContext(Dispatchers.IO) {
                repo.recent(limit = 15).map { RecentItem(it, TranscriptCache.has(ctx, it.uri)) }
            }
            _ui.value = _ui.value.copy(recent = recent, loadingRecent = false)
        }
    }

    private fun isListenerEnabled(): Boolean =
        ctx.packageName in NotificationManagerCompat.getEnabledListenerPackages(ctx)

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                val statuses = withContext(Dispatchers.IO) { WhisperModel.entries.associateWith { ModelManager.status(ctx, it) } }
                _ui.value = _ui.value.copy(modelStatus = statuses)
                if (statuses.values.none { it is ModelStatus.Downloading }) break
            }
        }
    }

    fun selectModel(model: WhisperModel) {
        Prefs.setModelId(ctx, model.id)
        _ui.value = _ui.value.copy(selectedModel = model)
    }

    fun download(model: WhisperModel) {
        selectModel(model)
        ModelManager.download(ctx, model)
        refresh()
    }

    fun deleteModel(model: WhisperModel) {
        ModelManager.delete(ctx, model)
        refresh()
    }

    fun setLanguage(lang: String) {
        Prefs.setLanguage(ctx, lang)
        _ui.value = _ui.value.copy(language = lang)
    }

    fun setBubbleEnabled(on: Boolean) {
        Prefs.setBubbleEnabled(ctx, on)
        _ui.value = _ui.value.copy(bubbleEnabled = on)
    }

    fun onFolderPicked(uri: Uri) {
        val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        // Rilascia eventuali accessi vecchi per non accumularli.
        ctx.contentResolver.persistedUriPermissions.forEach {
            if (it.uri != uri) runCatching { ctx.contentResolver.releasePersistableUriPermission(it.uri, flags) }
        }
        ctx.contentResolver.takePersistableUriPermission(uri, flags)
        Prefs.setVoiceTree(ctx, uri)
        // I vocali già presenti non vanno segnalati come nuovi.
        Prefs.setLastHandledModified(ctx, System.currentTimeMillis())
        refresh()
    }
}
