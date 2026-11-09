package com.othmane.vocaleai.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.othmane.vocaleai.text.Summary

/** Finestra flottante: si apre dalla bolla o da WhatsApp → Condividi → Vocale AI. */
class TranscribeActivity : ComponentActivity() {

    private val vm: TranscribeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = extractUri(intent)
        if (uri == null) {
            Toast.makeText(this, "Nessun vocale ricevuto", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val sender = intent.getStringExtra(EXTRA_SENDER)
        vm.start(uri)

        setContent {
            AppTheme {
                val state by vm.state.collectAsState()
                TranscribeCard(
                    sender = sender,
                    state = state,
                    onClose = { finish() },
                    onRetry = { vm.start(uri, force = true) },
                    onOpenSettings = {
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    },
                )
            }
        }
        window.setLayout(
            (resources.displayMetrics.widthPixels * 0.94f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun extractUri(intent: Intent): Uri? = when (intent.action) {
        Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        else -> intent.data
    }

    companion object {
        const val EXTRA_SENDER = "sender"

        fun intent(context: Context, uri: Uri, sender: String?): Intent =
            Intent(context, TranscribeActivity::class.java)
                .setData(uri)
                .putExtra(EXTRA_SENDER, sender)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

@Composable
private fun TranscribeCard(
    sender: String?,
    state: TxState,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.8f).dp
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (sender.isNullOrBlank()) "🎤 Vocale" else "🎤 $sender",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, contentDescription = "Chiudi")
                }
            }
            Column(
                Modifier
                    .padding(end = 12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                when (state) {
                    is TxState.Working -> WorkingView(state)
                    is TxState.Done -> DoneView(state, onRetry)
                    is TxState.Error -> ErrorView(state.message, onRetry)
                    TxState.NoModel -> NoModelView(onOpenSettings)
                }
            }
        }
    }
}

@Composable
private fun WorkingView(state: TxState.Working) {
    Column(Modifier.padding(vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                text = state.step + (state.percent?.let { " $it%" } ?: ""),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        state.percent?.let {
            Spacer(Modifier.padding(top = 12.dp))
            LinearProgressIndicator(progress = { it / 100f }, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.padding(top = 8.dp))
        Text(
            "Tutto avviene sul telefono, offline.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DoneView(state: TxState.Done, onRetry: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val meta = buildList {
            add(languageName(state.language))
            if (state.audioSeconds > 0) add(formatDuration(state.audioSeconds))
            state.elapsedMs?.let { add("trascritto in %.1f s".format(it / 1000f)) }
        }.joinToString(" · ")
        Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SummaryBlock(state.summary)

        Text("Trascrizione", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        SelectionContainer {
            Text(state.text, style = MaterialTheme.typography.bodyLarge)
        }

        HorizontalDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                clipboard.setText(AnnotatedString(state.text))
                Toast.makeText(context, "Copiato", Toast.LENGTH_SHORT).show()
            }) { Text("Copia") }
            FilledTonalButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, state.text)
                context.startActivity(Intent.createChooser(send, "Condividi trascrizione"))
            }) { Text("Condividi") }
            OutlinedButton(onClick = onRetry) { Text("Rifai") }
        }
    }
}

@Composable
private fun SummaryBlock(summary: Summary) {
    if (summary.keySentences.isEmpty() && summary.keywords.isEmpty()) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (summary.keySentences.isNotEmpty()) {
                Text("In breve", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                summary.keySentences.forEach { s ->
                    Row {
                        Text("•  ", style = MaterialTheme.typography.bodyMedium)
                        Text(s, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                Text(
                    "Messaggio breve: la trascrizione è già il riassunto.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (summary.keywords.isNotEmpty()) {
                Text(
                    "Parole chiave: " + summary.keywords.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Qualcosa è andato storto", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(message, style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onRetry) { Text("Riprova") }
    }
}

@Composable
private fun NoModelView(onOpenSettings: () -> Unit) {
    Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Prima di trascrivere serve scaricare il modello Whisper (una volta sola).",
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = onOpenSettings) { Text("Apri impostazioni") }
    }
}
