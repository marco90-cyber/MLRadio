@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.marco.mlradio.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marco.mlradio.audio.EngineState
import com.marco.mlradio.audio.PlayerEngine
import com.marco.mlradio.data.Station
import com.marco.mlradio.data.flagEmoji
import java.util.Locale
import kotlin.math.roundToInt

private fun statusText(e: PlayerEngine): String = when (e.state) {
    EngineState.IDLE -> "Pronta"
    EngineState.CONNECTING -> "Connessione…"
    EngineState.BUFFERING -> "Caricamento…"
    EngineState.PLAYING -> "In onda"
    EngineState.PAUSED -> "In pausa"
    EngineState.ERROR -> e.errorText ?: "Errore"
}

private fun hz(sr: Int): String =
    if (sr % 1000 == 0) "${sr / 1000} kHz" else String.format(Locale.ITALY, "%.1f kHz", sr / 1000.0)

private fun channelsText(n: Int): String = when (n) {
    1 -> "Mono"
    2 -> "Stereo"
    else -> "$n canali"
}

/** Player a schermo intero, in stile radio DAB: logo, nome, brano in onda, dati del flusso, volume. */
@Composable
fun PlayerScreen(env: AppEnv) {
    val engine = env.engine
    val st = engine.currentStation
    val ctx = LocalContext.current
    BackHandler { env.showPlayer = false }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .blockTouches()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // barra superiore
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { env.showPlayer = false }) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Chiudi il player", modifier = Modifier.size(32.dp))
            }
            Text(
                "In riproduzione", modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = { env.showEq = true }) {
                Icon(Icons.Filled.Equalizer, contentDescription = "Equalizzatore")
            }
        }

        Spacer(Modifier.height(8.dp))
        StationLogo(st, 220.dp)
        Spacer(Modifier.height(20.dp))

        Text(
            st?.name ?: "Nessuna radio",
            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        val place = st?.place().orEmpty()
        if (place.isNotBlank()) {
            val flag = if ((st?.countryCode?.length ?: 0) == 2) flagEmoji(st!!.countryCode) + " " else ""
            Text(
                flag + place, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(16.dp))

        // "display" della radio: stato e titolo del brano
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val live = engine.state == EngineState.PLAYING
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    engine.state == EngineState.ERROR -> MaterialTheme.colorScheme.error
                                    live -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.outline
                                }
                            )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        statusText(engine).uppercase(Locale.ITALY),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (engine.state == EngineState.ERROR) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(10.dp))
                val song = engine.nowPlaying
                Text(
                    if (song.isNotBlank()) song else "Nessuna informazione sul brano",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = if (song.isNotBlank()) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (song.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    st?.name ?: "", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // dati tecnici del flusso
        if (st != null) {
            Spacer(Modifier.height(14.dp))
            val codec = engine.codecLabel.ifBlank { st.codec.takeIf { !it.equals("unknown", true) }?.uppercase().orEmpty() }
            val br = engine.bitrateKbps.takeIf { it > 0 } ?: st.bitrate.takeIf { it > 0 }
            val main = listOfNotNull(codec.takeIf { it.isNotBlank() }, br?.let { "$it kbps" }).joinToString(" · ")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (main.isNotBlank()) InfoBadge(main, emphasized = true)
                if (engine.sampleRate > 0) InfoBadge(hz(engine.sampleRate))
                if (engine.channels > 0) InfoBadge(channelsText(engine.channels))
                for (g in st.genres(2)) InfoBadge(g.replaceFirstChar { it.uppercase() })
                if (st.custom) InfoBadge("Radio personalizzata")
            }
        }

        Spacer(Modifier.height(20.dp))

        // volume
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { engine.toggleMute() }) {
                val half = engine.maxVolume / 2
                Icon(
                    when {
                        engine.muted || engine.volume == 0 -> Icons.AutoMirrored.Filled.VolumeOff
                        engine.volume <= half -> Icons.AutoMirrored.Filled.VolumeDown
                        else -> Icons.AutoMirrored.Filled.VolumeUp
                    },
                    contentDescription = if (engine.muted) "Riattiva l'audio" else "Silenzia"
                )
            }
            Slider(
                value = engine.volume.toFloat(),
                onValueChange = { engine.setVolume(it.roundToInt()) },
                valueRange = 0f..engine.maxVolume.toFloat(),
                steps = (engine.maxVolume - 1).coerceAtLeast(0),
                modifier = Modifier.weight(1f)
            )
            Text(
                "${(engine.volume * 100f / engine.maxVolume).roundToInt()}%",
                modifier = Modifier.width(44.dp), textAlign = TextAlign.End,
                style = MaterialTheme.typography.labelLarge
            )
        }

        Spacer(Modifier.height(12.dp))

        // comandi
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { engine.previous() }, enabled = engine.queue.size > 1, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Radio precedente", modifier = Modifier.size(32.dp))
            }
            val busy = engine.state == EngineState.CONNECTING || engine.state == EngineState.BUFFERING
            Box(contentAlignment = Alignment.Center) {
                if (busy) CircularProgressIndicator(Modifier.size(88.dp), strokeWidth = 3.dp)
                FilledIconButton(
                    onClick = { engine.togglePlayPause() },
                    enabled = st != null,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        if (engine.wantsPlay) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (engine.wantsPlay) "Pausa" else "Ascolta",
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            IconButton(onClick = { engine.next() }, enabled = engine.queue.size > 1, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Radio successiva", modifier = Modifier.size(32.dp))
            }
        }

        Spacer(Modifier.height(16.dp))

        // preferito, equalizzatore, sito
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val isFav = st != null && env.favorites.items.any { it.uuid == st.uuid }
            FilledTonalButton(
                onClick = { if (st != null) env.toggleFavorite(st) },
                enabled = st != null,
                modifier = Modifier.weight(1f)
            ) {
                Icon(if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Preferita", maxLines = 1)
            }
            FilledTonalButton(onClick = { env.showEq = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Equalizer, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Equalizzatore", maxLines = 1)
            }
        }

        // dettagli della radio
        if (st != null) {
            Spacer(Modifier.height(20.dp))
            StationDetails(st) { url -> openUrl(ctx, url) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StationDetails(st: Station, open: (String) -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Informazioni sulla radio", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            DetailRow("Paese", st.countryLocalName().let { n ->
                if (st.countryCode.length == 2) flagEmoji(st.countryCode) + " " + n else n
            })
            DetailRow("Regione", st.state)
            DetailRow("Lingua", st.language.replaceFirstChar { it.uppercase() })
            DetailRow("Generi", st.genres(6).joinToString(", "))
            DetailRow("Formato", listOfNotNull(
                st.codec.takeIf { it.isNotBlank() && !it.equals("unknown", true) },
                st.bitrate.takeIf { it > 0 }?.let { "$it kbps" },
            ).joinToString(" · "))
            if (st.homepage.startsWith("http")) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { open(st.homepage) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Sito web", modifier = Modifier.width(88.dp),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        st.homepage.removePrefix("https://").removePrefix("http://").trimEnd('/'),
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                if (st.custom) "Radio aggiunta da te." else "Dati: Radio Browser (radio-browser.info).",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth()) {
        Text(
            label, modifier = Modifier.width(88.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}
