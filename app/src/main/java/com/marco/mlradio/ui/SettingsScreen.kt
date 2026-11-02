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
import com.marco.mltheme.AccentPreview
import com.marco.mltheme.MLChoiceRow
import com.marco.mltheme.MLGroup
import com.marco.mltheme.MLNavRow
import com.marco.mltheme.MLSettingsScaffold
import com.marco.mltheme.MLSwitchRow
import com.marco.mltheme.MLThemeSettings
import com.marco.mltheme.SWITCH_STYLES
import com.marco.mltheme.SwitchStylePreview
import com.marco.mltheme.THEME_MODES
import com.marco.mltheme.ThemePreview
import com.marco.mltheme.availableAccents

@Composable
fun SettingsScreen(env: AppEnv) {
    val s = env.settings
    val ctx = LocalContext.current
    var clearDialog by remember { mutableStateOf(false) }
    var autoDialog by remember { mutableStateOf(false) }
    val ml = MLThemeSettings.get(ctx)

    MLSettingsScaffold("Impostazioni", onBack = null) {
        MLGroup("Aspetto") {
            MLChoiceRow(
                "Tema",
                THEME_MODES,
                when (s.themeMode) { 1 -> "light"; 2 -> "dark"; else -> "auto" },
                preview = { ThemePreview(it) },
            ) { s.themeMode = when (it) { "light" -> 1; "dark" -> 2; else -> 0 } }
            MLChoiceRow("Colori", availableAccents, ml.accent, preview = { AccentPreview(it) }) { ml.accent = it }
            MLChoiceRow("Stile degli interruttori", SWITCH_STYLES, ml.switchStyle, preview = { SwitchStylePreview(it) }) {
                ml.switchStyle = it
            }
        }
        MLGroup("Audio") {
            MLNavRow("Equalizzatore", "Bande, preamplificatore, bassi e acuti, limitatore, preimpostazioni") { env.showEq = true }
            MLSwitchRow(
                "Riprendi al collegamento",
                "Se la radio si ferma scollegando le cuffie o il Bluetooth, riparte quando li ricolleghi",
                s.resumeOnConnect,
            ) { s.resumeOnConnect = it }
            MLSwitchRow("Vibrazione dei tocchi", null, s.hapticScreen) { s.hapticScreen = it }
        }
        MLGroup("Android Auto") {
            MLNavRow("Come usare MLRadio in auto", "Istruzioni per abilitare le app non da Play Store") { autoDialog = true }
        }
        MLGroup("Dati e permessi") {
            MLNavRow("Permessi dell'app", "Notifiche e Bluetooth servono ai comandi nella tendina e alla ripresa automatica") {
                ctx.startActivity(
                    Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            MLNavRow("Cancella la cronologia", "${env.history.items.size} brani salvati", trailing = null) { clearDialog = true }
            MLSwitchRow("Registro diagnostico dettagliato", "Utile se qualcosa non funziona", s.verboseLog) {
                s.verboseLog = it; AppLog.verbose = it
            }
            MLNavRow("Condividi il registro", null, trailing = null) {
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
        }
        MLGroup("Informazioni") {
            MLNavRow("Cerca aggiornamenti", "Versione installata ${com.marco.mlradio.BuildConfig.VERSION_NAME}") {
                com.marco.mlradio.AppUpdate.open(ctx)
            }
            MLNavRow("Informazioni", "Versione, crediti, licenze") { env.showAbout = true }
        }
    }

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
