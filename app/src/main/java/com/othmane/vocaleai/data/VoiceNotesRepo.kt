package com.othmane.vocaleai.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.util.Log

data class VoiceNote(
    val uri: Uri,
    val name: String,
    val lastModified: Long,
    val size: Long,
)

/**
 * Legge i file della cartella "WhatsApp Voice Notes" tramite l'accesso concesso dall'utente
 * (Storage Access Framework). Struttura tipica:
 *   Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes/202645/PTT-20261109-WA0003.opus
 */
class VoiceNotesRepo(private val context: Context) {

    private data class Child(
        val docId: String,
        val name: String,
        val mime: String,
        val modified: Long,
        val size: Long,
    ) {
        val isDir: Boolean get() = mime == Document.MIME_TYPE_DIR
    }

    fun treeUri(): Uri? = Prefs.voiceTree(context)

    fun hasAccess(): Boolean {
        val tree = treeUri() ?: return false
        return context.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission }
    }

    private fun children(tree: Uri, parentDocId: String): List<Child> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentDocId)
        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_SIZE,
        )
        val out = ArrayList<Child>()
        try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    out += Child(
                        docId = c.getString(0),
                        name = c.getString(1) ?: "",
                        mime = c.getString(2) ?: "",
                        modified = if (c.isNull(3)) 0L else c.getLong(3),
                        size = if (c.isNull(4)) 0L else c.getLong(4),
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lettura cartella fallita", e)
        }
        return out
    }

    /** Se l'utente ha scelto una cartella più in alto (es. "Media"), scende fino a "Voice Notes". */
    private fun voiceRootId(tree: Uri): String {
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        var frontier = listOf(rootId)
        repeat(4) {
            val next = ArrayList<String>()
            for (id in frontier) {
                val kids = children(tree, id)
                if (kids.any { !it.isDir && isAudio(it.name) } || kids.any { it.isDir && it.name.matches(WEEK_DIR) }) {
                    return id
                }
                kids.firstOrNull { it.isDir && it.name.contains("Voice Notes", ignoreCase = true) }
                    ?.let { return it.docId }
                next += kids.filter { it.isDir && !it.name.startsWith(".") }.map { it.docId }
            }
            frontier = next.take(30)
        }
        return rootId
    }

    fun recent(limit: Int = 20, maxWeekFolders: Int = 3): List<VoiceNote> {
        val tree = treeUri() ?: return emptyList()
        if (!hasAccess()) return emptyList()
        val rootId = voiceRootId(tree)
        val top = children(tree, rootId)
        val files = ArrayList<Child>()
        files += top.filter { !it.isDir && isAudio(it.name) }
        top.filter { it.isDir }
            .sortedByDescending { it.name }
            .take(maxWeekFolders)
            .forEach { dir -> files += children(tree, dir.docId).filter { !it.isDir && isAudio(it.name) } }
        return files.sortedByDescending { it.modified }
            .take(limit)
            .map { VoiceNote(DocumentsContract.buildDocumentUriUsingTree(tree, it.docId), it.name, it.modified, it.size) }
    }

    /** Il vocale più recente arrivato dopo [minModified] e non ancora segnalato. */
    fun newestAfter(minModified: Long): VoiceNote? {
        val floor = maxOf(minModified, Prefs.lastHandledModified(context))
        return recent(limit = 1, maxWeekFolders = 1).firstOrNull()?.takeIf { it.lastModified > floor && it.size > 0 }
    }

    companion object {
        private const val TAG = "VoiceNotesRepo"
        private val WEEK_DIR = Regex("\\d{6}")
        private val AUDIO_EXT = listOf(".opus", ".ogg", ".m4a", ".aac", ".mp3", ".amr", ".wav")

        fun isAudio(name: String): Boolean = AUDIO_EXT.any { name.endsWith(it, ignoreCase = true) }

        /** Punto di partenza suggerito al selettore di cartelle. */
        fun suggestedInitialUri(business: Boolean = false): Uri {
            val path = if (business) {
                "primary:Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Voice Notes"
            } else {
                "primary:Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes"
            }
            return DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", path)
        }
    }
}
