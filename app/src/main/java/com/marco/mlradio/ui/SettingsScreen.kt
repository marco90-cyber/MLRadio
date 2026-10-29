@file:OptIn(ExperimentalMaterial3Api::class)

package com.marco.mlradio.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.marco.mlradio.util.AppLog

@Composable
fun SettingsScreen(env: AppEnv) {
    val s = env.settings
    val ctx = LocalContext.current
    var clearDialog by remember { mutableStateOf(false) }
    var autoDialog by remember { mutableStateOf(false) }
    var updateOpen by remember { mutableStateOf(false) }
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Impostazioni") })
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SectionTitle("Aspetto")
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Sistema", "Chiaro", "Scuro").forEachIndexed { i, label ->
                    FilterChip(selected = s.themeMode == i, onClick = { s.themeMode = i }, label = { Text(label) })
                }
            }
            ListItem(
                colors = transparent,
                headlineContent = { Text("Colori dinamici (Material You)") },
                supportingContent = { Text("Usa i colori dello sfondo del telefono") },
                trailingContent = { Switch(checked = s.dynamicColor, onCheckedChange = { s.dynamicColor = it }) },
                modifier = Modifier.clickable { s.dynamicColor = !s.dynamicColor }
            )

            SectionTitle("Audio")
            ListItem(
                colors = transparent,
                headlineContent = { Text("Equalizzatore") },
                supportingContent = { Text("Bande, preamplificatore, bassi e acuti, limitatore, preimpostazioni") },
                modifier = Modifier.clickable { env.showEq = true }
            )
            ListItem(
                colors = transparent,
                headlineContent = { Text("Riprendi al collegamento") },
                supportingContent = { Text("Se la radio si ferma scollegando le cuffie o il Bluetooth, riparte quando li ricolleghi") },
                trailingContent = { Switch(checked = s.resumeOnConnect, onCheckedChange = { s.resumeOnConnect = it }) },
                modifier = Modifier.clickable { s.resumeOnConnect = !s.resumeOnConnect }
            )
            ListItem(
                colors = transparent,
                headlineContent = { Text("Vibrazione dei tocchi") },
                trailingContent = { Switch(checked = s.hapticScreen, onCheckedChange = { s.hapticScreen = it }) },
                modifier = Modifier.clickable { s.hapticScreen = !s.hapticScreen }
            )

            SectionTitle("Android Auto")
            ListItem(
                colors = transparent,
                headlineContent = { Text("Come usare MLRadio in auto") },
                supportingContent = { Text("Istruzioni per abilitare le app non da Play Store") },
                modifier = Modifier.clickable { autoDialog = true }
            )

            SectionTitle("Dati e permessi")
            ListItem(
                colors = transparent,
                headlineContent = { Text("Permessi dell'app") },
                supportingContent = { Text("Notifiche e Bluetooth servono ai comandi nella tendina e alla ripresa automatica") },
                modifier = Modifier.clickable {
                    ctx.startActivity(
                        Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            )
            ListItem(
                colors = transparent,
                headlineContent = { Text("Cancella la cronologia") },
                supportingContent = { Text("${env.history.items.size} brani salvati") },
                modifier = Modifier.clickable { clearDialog = true }
            )
            ListItem(
                colors = transparent,
                headlineContent = { Text("Registro diagnostico dettagliato") },
                supportingContent = { Text("Utile se qualcosa non funziona") },
                trailingContent = {
                    Switch(checked = s.verboseLog, onCheckedChange = { s.verboseLog = it; AppLog.verbose = it })
                },
                modifier = Modifier.clickable { s.verboseLog = !s.verboseLog; AppLog.verbose = s.verboseLog }
            )
            ListItem(
                colors = transparent,
                headlineContent = { Text("Condividi il registro") },
                modifier = Modifier.clickable {
                    try {
                        val f = AppLog.exportFile(ctx)
                        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                        ctx.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(Intent.EXTRA_STREAM, uri)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                "Condividi il registro"
                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) {
                    }
                }
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            ListItem(
                colors = transparent,
                headlineContent = { Text("Cerca aggiornamenti") },
                supportingContent = { Text("Versione installata ${com.marco.mlradio.BuildConfig.VERSION_NAME}") },
                modifier = Modifier.clickable { updateOpen = true }
            )
            ListItem(
                colors = transparent,
                headlineContent = { Text("Informazioni") },
                supportingContent = { Text("Versione, crediti, licenze") },
                modifier = Modifier.clickable { env.showAbout = true }
            )
            Text("", Modifier.padding(16.dp))
        }
    }

    if (updateOpen) UpdateDialog(env) { updateOpen = false }

    if (clearDialog) {
        AlertDialog(
            onDismissRequest = { clearDialog = false },
            title = { Text("Cancellare la cronologia?") },
            text = { Text("Verranno eliminati tutti i brani salvati.") },
            confirmButton = { TextButton(onClick = { env.history.clear(); clearDialog = false }) { Text("Cancella") } },
            dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("Annulla") } }
        )
    }
    if (autoDialog) {
        AlertDialog(
            onDismissRequest = { autoDialog = false },
            title = { Text("Android Auto") },
            text = {
                Text(
                    "MLRadio non è installata da Play Store, quindi Android Auto la mostra solo dopo aver abilitato le " +
                        "origini sconosciute:\n\n" +
                        "1. Apri Impostazioni di Android Auto sul telefono.\n" +
                        "2. Tocca più volte «Versione» per attivare le opzioni sviluppatore.\n" +
                        "3. Menu ⋮ → Impostazioni sviluppatore → attiva «Origini sconosciute».\n" +
                        "4. Ricollega l'auto: MLRadio compare tra le app multimediali con Preferiti e Le mie radio.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = { TextButton(onClick = { autoDialog = false }) { Text("Ho capito") } }
        )
    }
}
