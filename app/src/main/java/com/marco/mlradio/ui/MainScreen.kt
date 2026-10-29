@file:OptIn(ExperimentalMaterial3Api::class)

package com.marco.mlradio.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marco.mlradio.audio.EngineState

@Composable
fun MainScreen(env: AppEnv) {
    // Un solo gestore del tasto indietro, con priorità: equalizzatore, informazioni, player, esplora.
    BackHandler(enabled = env.showEq || env.showAbout || env.showPlayer || (env.tab == 1 && env.explore.canGoBack) || env.tab != 0) {
        when {
            env.showEq -> env.showEq = false
            env.showAbout -> env.showAbout = false
            env.showPlayer -> env.showPlayer = false
            env.tab == 1 && env.explore.canGoBack -> env.explore.back()
            else -> env.tab = 0
        }
    }

    // Surface: dà a tutti i testi e le icone il colore giusto per il tema chiaro/scuro
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                Column {
                    MiniPlayer(env)
                    NavigationBar {
                        val items = listOf(
                            "Preferiti" to Icons.Filled.Favorite,
                            "Esplora" to Icons.Filled.Explore,
                            "Cronologia" to Icons.Filled.History,
                            "Impostazioni" to Icons.Filled.Settings,
                        )
                        items.forEachIndexed { i, (label, icon) ->
                            NavigationBarItem(
                                selected = env.tab == i,
                                onClick = { env.tab = i },
                                icon = { Icon(icon, contentDescription = label) },
                                label = { Text(label, maxLines = 1) }
                            )
                        }
                    }
                }
            }
        ) { pad ->
            Box(
                Modifier
                    .padding(pad)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .fillMaxSize()
            ) {
                when (env.tab) {
                    0 -> FavoritesScreen(env)
                    1 -> ExploreScreen(env)
                    2 -> HistoryScreen(env)
                    else -> SettingsScreen(env)
                }
            }
        }

        if (env.showPlayer) PlayerScreen(env)
        if (env.showAbout) AboutScreen(env)
        if (env.showEq) {
            Box(Modifier.fillMaxSize().blockTouches()) { EqualizerScreen(env) }
        }
        if (env.addDialog) AddStationDialog(env) { env.addDialog = false }
    }
}
}

@Composable
private fun MiniPlayer(env: AppEnv) {
    val engine = env.engine
    val st = engine.currentStation ?: return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().clickable { env.showPlayer = true }
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            StationLogo(st, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(st.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = when (engine.state) {
                    EngineState.CONNECTING -> "Connessione…"
                    EngineState.BUFFERING -> "Caricamento…"
                    EngineState.ERROR -> engine.errorText ?: "Errore"
                    else -> engine.nowPlaying.ifBlank { "In diretta" }
                }
                Text(
                    sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            val busy = engine.state == EngineState.CONNECTING || engine.state == EngineState.BUFFERING
            Box(contentAlignment = Alignment.Center) {
                if (busy) CircularProgressIndicator(Modifier.size(40.dp), strokeWidth = 2.dp)
                IconButton(onClick = { engine.togglePlayPause() }) {
                    Icon(
                        if (engine.wantsPlay) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (engine.wantsPlay) "Pausa" else "Ascolta"
                    )
                }
            }
        }
    }
}
