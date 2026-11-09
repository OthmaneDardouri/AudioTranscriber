@file:OptIn(ExperimentalMaterial3Api::class)

package com.othmane.vocaleai.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.othmane.vocaleai.bubble.BubbleManager
import com.othmane.vocaleai.bubble.PendingVoice
import com.othmane.vocaleai.data.VoiceNotesRepo
import com.othmane.vocaleai.whisper.ModelStatus
import com.othmane.vocaleai.whisper.WhisperModel

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        BubbleManager.ensureChannel(this)
        setContent {
            AppTheme {
                val ui by vm.ui.collectAsState()
                MainScreen(ui, vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }
}

private val OK_GREEN = Color(0xFF2E7D32)

@Composable
private fun MainScreen(ui: MainUi, vm: MainViewModel) {
    val context = LocalContext.current

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                vm.onFolderPicked(uri)
            } catch (e: Exception) {
                Toast.makeText(context, "Accesso alla cartella non riuscito", Toast.LENGTH_LONG).show()
            }
        }
    }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.refresh()
    }

    Scaffold { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = inner.calculateTopPadding() + 16.dp,
                bottom = inner.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Text("Vocale AI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Trascrivi e riassumi i vocali di WhatsApp. Tutto offline, sul telefono.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 1. Modello
            item {
                SetupCard(
                    step = 1,
                    title = "Modello Whisper",
                    done = ui.modelStatus.values.any { it == ModelStatus.Ready },
                ) {
                    WhisperModel.entries.forEach { m ->
                        ModelRow(
                            model = m,
                            selected = ui.selectedModel == m,
                            status = ui.modelStatus[m] ?: ModelStatus.Missing,
                            onSelect = { vm.selectModel(m) },
                            onDownload = { vm.download(m) },
                            onDelete = { vm.deleteModel(m) },
                        )
                    }
                    Spacer(Modifier.padding(top = 4.dp))
                    Text("Lingua dei vocali", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("auto", "it", "ar", "en").forEach { code ->
                            FilterChip(
                                selected = ui.language == code,
                                onClick = { vm.setLanguage(code) },
                                label = { Text(languageName(code)) },
                            )
                        }
                    }
                    Hint("\"Automatica\" riconosce da sola italiano, arabo, inglese, ecc. Per il darija marocchino Whisper è meno preciso: meglio il modello Small o Turbo.")
                }
            }

            // 2. Cartella vocali
            item {
                SetupCard(step = 2, title = "Cartella dei vocali WhatsApp", done = ui.folderGranted) {
                    Hint("Si apre già su \"WhatsApp Voice Notes\": tocca \"Usa questa cartella\" e poi \"Consenti\". Serve per trovare il file del vocale appena arrivato.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { folderPicker.launch(VoiceNotesRepo.suggestedInitialUri()) }) {
                            Text(if (ui.folderGranted) "Cambia" else "Scegli cartella")
                        }
                        TextButton(onClick = { folderPicker.launch(VoiceNotesRepo.suggestedInitialUri(business = true)) }) {
                            Text("WhatsApp Business")
                        }
                    }
                }
            }

            // 3. Accesso notifiche
            item {
                SetupCard(step = 3, title = "Rileva i nuovi vocali", done = ui.listenerEnabled) {
                    Hint("L'app legge solo le notifiche di WhatsApp per capire quando arriva un vocale. Nessun dato esce dal telefono.")
                    Button(onClick = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }) { Text(if (ui.listenerEnabled) "Impostazioni" else "Attiva accesso notifiche") }
                    if (!ui.listenerEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Hint("Se l'interruttore è grigio (\"Impostazione con restrizioni\"): apri Info app → menu ⋮ in alto a destra → \"Consenti impostazioni con restrizioni\", poi riprova.")
                        OutlinedButton(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                            )
                        }) { Text("Apri Info app") }
                    }
                }
            }

            // 4. Bolla
            item {
                SetupCard(step = 4, title = "Bolla flottante", done = ui.overlayAllowed && ui.bubbleEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Mostra la bolla 🎤 sopra WhatsApp", modifier = Modifier.weight(1f))
                        Switch(checked = ui.bubbleEnabled, onCheckedChange = { vm.setBubbleEnabled(it) })
                    }
                    if (!ui.overlayAllowed) {
                        Button(onClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                            )
                        }) { Text("Consenti sovrapposizione") }
                    } else {
                        OutlinedButton(onClick = {
                            val latest = ui.recent.firstOrNull()?.note
                            BubbleManager.push(context.applicationContext, PendingVoice(latest?.uri, "Prova", System.currentTimeMillis()))
                        }) { Text("Prova la bolla") }
                    }
                    Hint("Tocca la bolla per trascrivere · trascinala per spostarla · tienila premuta per chiuderla. Senza bolla ricevi una notifica.")
                    if (!ui.notificationsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        TextButton(onClick = { notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                            Text("Consenti anche le notifiche (riserva)")
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Anche da WhatsApp", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Hint("Tieni premuto un vocale → icona Condividi → Vocale AI. Funziona sempre, anche per vocali vecchi o inoltrati.")
                    }
                }
            }

            // Ultimi vocali
            item {
                Text(
                    "Ultimi vocali",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            when {
                !ui.folderGranted -> item { Hint("Scegli la cartella dei vocali (passo 2) per vederli qui.") }
                ui.loadingRecent -> item { CircularProgressIndicator(Modifier.size(24.dp)) }
                ui.recent.isEmpty() -> item { Hint("Nessun vocale trovato nella cartella scelta.") }
                else -> items(ui.recent, key = { it.note.uri.toString() }) { item ->
                    RecentRow(item) {
                        context.startActivity(TranscribeActivity.intent(context, item.note.uri, null))
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupCard(step: Int, title: String, done: Boolean, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$step. $title", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (done) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = "Fatto", tint = OK_GREEN)
                } else {
                    Icon(Icons.Filled.Warning, contentDescription = "Da fare", tint = MaterialTheme.colorScheme.error)
                }
            }
            content()
        }
    }
}

@Composable
private fun ModelRow(
    model: WhisperModel,
    selected: Boolean,
    status: ModelStatus,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable(onClick = onSelect)) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(Modifier.weight(1f)) {
                Text("${model.label} · ~${model.sizeMb} MB", style = MaterialTheme.typography.bodyLarge)
                Text(model.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when (status) {
                ModelStatus.Ready -> TextButton(onClick = onDelete) { Text("Elimina") }
                is ModelStatus.Downloading -> Text(
                    status.percent?.let { "$it%" } ?: "…",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                ModelStatus.Missing, is ModelStatus.Failed -> TextButton(onClick = onDownload) { Text("Scarica") }
            }
        }
        if (status is ModelStatus.Downloading) {
            val p = status.percent
            if (p != null) {
                LinearProgressIndicator(progress = { p / 100f }, modifier = Modifier.fillMaxWidth().padding(start = 48.dp, end = 12.dp))
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(start = 48.dp, end = 12.dp))
            }
        }
        if (status is ModelStatus.Failed) {
            Text(status.reason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 48.dp))
        }
    }
}

@Composable
private fun RecentRow(item: RecentItem, onClick: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("🎤", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    DateUtils.getRelativeDateTimeString(
                        context, item.note.lastModified, DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS, 0,
                    ).toString(),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "${item.note.name} · ${item.note.size / 1024} KB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (item.transcribed) "✓ Letto" else "Trascrivi",
                style = MaterialTheme.typography.labelLarge,
                color = if (item.transcribed) OK_GREEN else MaterialTheme.colorScheme.primary,
            )
        }
        HorizontalDivider()
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
