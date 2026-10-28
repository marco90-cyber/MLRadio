@file:OptIn(ExperimentalMaterial3Api::class)

package com.marco.mlradio.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marco.mlradio.data.HistoryEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private sealed class HistoryRow {
    data class Header(val label: String) : HistoryRow()
    data class Entry(val e: HistoryEntry) : HistoryRow()
}

private val DayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ITALIAN)
private val TimeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ITALIAN)

private fun dayOf(ts: Long): LocalDate = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()

private fun dayLabel(d: LocalDate): String {
    val today = LocalDate.now()
    return when (d) {
        today -> "Oggi"
        today.minusDays(1) -> "Ieri"
        else -> d.format(DayFormat).replaceFirstChar { it.uppercase() }
    }
}

private fun buildRows(items: List<HistoryEntry>): List<HistoryRow> {
    val out = ArrayList<HistoryRow>(items.size + 8)
    var last: LocalDate? = null
    for (e in items) {
        val d = dayOf(e.ts)
        if (d != last) {
            out.add(HistoryRow.Header(dayLabel(d)))
            last = d
        }
        out.add(HistoryRow.Entry(e))
    }
    return out
}

@Composable
fun HistoryScreen(env: AppEnv) {
    val items = env.history.items
    var confirmClear by remember { mutableStateOf(false) }
    val rows = remember(items) { buildRows(items) }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Cronologia") },
            actions = {
                if (items.isNotEmpty()) {
                    IconButton(onClick = { confirmClear = true }) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Cancella la cronologia")
                    }
                }
            }
        )
        if (items.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.History,
                title = "Nessun brano nella cronologia",
                text = "I brani compaiono qui mentre ascolti una radio che ne comunica il titolo. " +
                    "Non tutte le radio lo fanno."
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(rows) { row ->
                    when (row) {
                        is HistoryRow.Header -> SectionTitle(row.label)
                        is HistoryRow.Entry -> HistoryItem(env, row.e)
                    }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Cancellare la cronologia?") },
            text = { Text("Verranno eliminati tutti i brani salvati. L'operazione non si può annullare.") },
            confirmButton = {
                TextButton(onClick = { env.history.clear(); confirmClear = false }) { Text("Cancella") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Annulla") } }
        )
    }
}

@Composable
private fun HistoryItem(env: AppEnv, e: HistoryEntry) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var menu by remember { mutableStateOf(false) }
    val time = Instant.ofEpochMilli(e.ts).atZone(ZoneId.systemDefault()).format(TimeFormat)
    Box {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable { menu = true },
            leadingContent = { StationLogo(e.favicon, 48.dp) },
            headlineContent = { Text(e.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Text(
                    if (e.stationName.isNotBlank()) "${e.stationName} · $time" else time,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            },
            trailingContent = {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Azioni") }
            }
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Cerca su Spotify") },
                onClick = {
                    menu = false
                    openUrl(ctx, "https://open.spotify.com/search/" + Uri.encode(e.title))
                }
            )
            DropdownMenuItem(
                text = { Text("Cerca su YouTube") },
                onClick = {
                    menu = false
                    openUrl(ctx, "https://www.youtube.com/results?search_query=" + Uri.encode(e.title))
                }
            )
            DropdownMenuItem(
                text = { Text("Copia il titolo") },
                leadingIcon = { Icon(Icons.Filled.ContentCopy, null) },
                onClick = {
                    menu = false
                    clipboard.setText(AnnotatedString(e.title))
                    Toast.makeText(ctx, "Titolo copiato", Toast.LENGTH_SHORT).show()
                }
            )
            DropdownMenuItem(
                text = { Text("Elimina") },
                leadingIcon = { Icon(Icons.Filled.Delete, null) },
                onClick = { menu = false; env.history.remove(e) }
            )
        }
    }
}
