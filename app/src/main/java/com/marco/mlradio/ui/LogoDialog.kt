package com.marco.mlradio.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.marco.mlradio.data.Logos
import com.marco.mlradio.data.Station
import kotlinx.coroutines.launch

/** «Cambia logo»: dalla galleria, da un link, nuova ricerca sul sito o ripristino. */
@Composable
fun LogoDialog(env: AppEnv, st: Station, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var linkMode by remember { mutableStateOf(false) }
    var link by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val ok = Logos.setCustomImage(ctx, st.uuid, uri)
            Toast.makeText(ctx, if (ok) "Logo aggiornato" else "Immagine non valida", Toast.LENGTH_SHORT).show()
            if (ok) onDismiss()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Logo di ${st.name}") },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                StationLogo(st, 96.dp)
                Spacer(Modifier.height(12.dp))
                if (linkMode) {
                    OutlinedTextField(
                        value = link, onValueChange = { link = it.trim() }, singleLine = true,
                        label = { Text("Indirizzo dell'immagine (https://…)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Option(Icons.Filled.Search, "Cerca sul web") {
                        env.webLogoFor = st; onDismiss()
                    }
                    Option(Icons.Filled.Image, "Scegli dalla galleria") {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    Option(Icons.Filled.Link, "Incolla un link") { linkMode = true }
                    if (st.homepage.startsWith("http")) {
                        Option(Icons.Filled.Refresh, "Cerca di nuovo sul sito della radio") {
                            Logos.searchAgain(st)
                            Toast.makeText(ctx, "Ricerca del logo avviata", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }
                    }
                    if (Logos.custom.containsKey(st.uuid)) {
                        Option(Icons.Filled.Restore, "Ripristina il logo originale") {
                            Logos.clearCustom(st.uuid); onDismiss()
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (linkMode) TextButton(
                enabled = link.startsWith("http://") || link.startsWith("https://"),
                onClick = { Logos.setCustomUrl(st.uuid, link); onDismiss() }
            ) { Text("Usa questo logo") }
        },
        dismissButton = {
            TextButton(onClick = { if (linkMode) linkMode = false else onDismiss() }) {
                Text(if (linkMode) "Indietro" else "Chiudi")
            }
        }
    )
}

@Composable
private fun Option(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
