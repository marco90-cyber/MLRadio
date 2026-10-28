@file:OptIn(ExperimentalMaterial3Api::class)

package com.marco.mlradio.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun FavoritesScreen(env: AppEnv) {
    val favs = env.favorites.items
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Preferiti") },
            actions = {
                IconButton(onClick = { env.addDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Aggiungi una radio")
                }
            }
        )
        if (favs.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.FavoriteBorder,
                title = "Nessun preferito",
                text = "Cerca una radio in Esplora e tocca il cuore per aggiungerla qui. " +
                    "Puoi anche aggiungere una radio con il suo indirizzo (tasto +).",
                action = "Esplora le radio" to { env.tab = 1 }
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(favs, key = { it.uuid }) { st ->
                    StationItem(env, st, favs)
                }
            }
        }
    }
}
