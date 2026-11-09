package com.othmane.vocaleai.detect

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.othmane.vocaleai.bubble.BubbleManager
import com.othmane.vocaleai.bubble.PendingVoice
import com.othmane.vocaleai.data.Prefs
import com.othmane.vocaleai.data.VoiceNotesRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Riceve le notifiche di WhatsApp. Quando una notifica annuncia un messaggio vocale,
 * cerca il file .opus appena scaricato e mostra la bolla (o una notifica di riserva).
 */
class WaNotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in VoiceDetect.WHATSAPP_PACKAGES) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val msg = extractLastMessage(n) ?: return
        if (!VoiceDetect.isVoice(msg.text)) return

        val dedupeKey = "${sbn.packageName}|${msg.sender}|${msg.timestamp}|${msg.text}"
        if (!Dedupe.firstTime(dedupeKey)) return

        val postTime = sbn.postTime
        val appContext = applicationContext
        scope.launch {
            val repo = VoiceNotesRepo(appContext)
            var note = if (repo.hasAccess()) repo.newestAfter(postTime - 120_000) else null
            // WhatsApp scarica il vocale in automatico, ma a volte un attimo dopo la notifica.
            var attempts = 0
            while (note == null && repo.hasAccess() && attempts < 20) {
                delay(1_000)
                note = repo.newestAfter(postTime - 120_000)
                attempts++
            }
            note?.let { Prefs.setLastHandledModified(appContext, it.lastModified) }
            val pending = PendingVoice(uri = note?.uri, sender = msg.sender, time = postTime)
            withContext(Dispatchers.Main) {
                BubbleManager.announce(appContext, pending)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private data class Msg(val sender: String, val text: CharSequence, val timestamp: Long)

    private fun extractLastMessage(n: Notification): Msg? {
        val extras = n.extras
        val fallbackTitle = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        try {
            val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)
            val last = style?.messages?.lastOrNull()
            val lastText = last?.text
            if (last != null && lastText != null) {
                val who = last.person?.name?.toString()
                val conversation = style.conversationTitle?.toString()
                val sender = listOfNotNull(who, conversation?.takeIf { it != who })
                    .joinToString(" · ")
                    .ifBlank { fallbackTitle ?: "WhatsApp" }
                return Msg(sender, lastText, last.timestamp)
            }
        } catch (e: Exception) {
            Log.w(TAG, "MessagingStyle non leggibile", e)
        }
        val text = extras.getCharSequence(Notification.EXTRA_TEXT) ?: return null
        return Msg(fallbackTitle ?: "WhatsApp", text, n.`when`)
    }

    private object Dedupe {
        private val seen = LinkedHashSet<String>()

        @Synchronized
        fun firstTime(key: String): Boolean {
            if (!seen.add(key)) return false
            while (seen.size > 200) seen.remove(seen.first())
            return true
        }
    }

    companion object {
        private const val TAG = "WaListener"
    }
}
