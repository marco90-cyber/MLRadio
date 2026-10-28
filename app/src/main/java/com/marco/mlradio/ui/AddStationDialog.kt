package com.marco.mlradio.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.marco.mlradio.data.Station
import java.util.UUID

/** Aggiunge una radio che non è nel database, con l'indirizzo del suo flusso audio. */
@Composable
fun AddStationDialog(env: AppEnv, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var logo by remember { mutableStateOf("") }
    var fav by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aggiungi una radio") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(60); error = null },
                    singleLine = true, label = { Text("Nome della radio") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url, onValueChange = { url = it.trim(); error = null },
                    singleLine = true, label = { Text("Indirizzo del flusso (http...)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = logo, onValueChange = { logo = it.trim() },
                    singleLine = true, label = { Text("Indirizzo del logo (facoltativo)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { fav = !fav },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = fav, onCheckedChange = { fav = it })
                    Spacer(Modifier.width(4.dp))
                    Text("Aggiungi anche ai preferiti")
                }
                if (error != null) {
                    Text(error ?: "", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    name.isBlank() -> error = "Scrivi il nome della radio."
                    !(url.startsWith("http://") || url.startsWith("https://")) ->
                        error = "L'indirizzo deve iniziare con http:// o https://"
                    else -> {
                        val st = Station(
                            uuid = "custom-" + UUID.randomUUID(),
                            name = name.trim(),
                            url = url,
                            favicon = if (logo.startsWith("http")) logo else "",
                            hls = url.contains(".m3u8", ignoreCase = true),
                            custom = true,
                        )
                        env.app.directory.remember(listOf(st))
                        env.custom.add(st)
                        if (fav) env.favorites.add(st)
                        onDismiss()
                    }
                }
            }) { Text("Aggiungi") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}
