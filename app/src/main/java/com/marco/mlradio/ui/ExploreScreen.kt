@file:OptIn(ExperimentalMaterial3Api::class)

package com.marco.mlradio.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marco.mlradio.data.Country
import kotlinx.coroutines.delay

@Composable
fun ExploreScreen(env: AppEnv) {
    val ex = env.explore
    LaunchedEffect(Unit) { ex.loadCountries() }
    val title = when {
        ex.customOpen -> "Le mie radio"
        ex.global -> "Ricerca nel mondo"
        ex.country != null -> ex.country!!.let { it.flag + " " + it.localName }
        else -> "Esplora"
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title, maxLines = 1) },
            navigationIcon = {
                if (ex.canGoBack) {
                    IconButton(onClick = { ex.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                }
            },
            actions = {
                IconButton(onClick = { env.addDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Aggiungi una radio")
                }
            }
        )
        when {
            ex.customOpen -> CustomList(env)
            ex.inStations -> StationList(env)
            else -> CountryList(env)
        }
    }
}

@Composable
private fun CountryList(env: AppEnv) {
    val ex = env.explore
    val q = ex.countryQuery.trim()
    val filtered = remember(ex.countries, q) {
        if (q.isEmpty()) ex.countries
        else ex.countries.filter { it.localName.contains(q, ignoreCase = true) || it.name.contains(q, ignoreCase = true) }
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            OutlinedTextField(
                value = ex.countryQuery,
                onValueChange = { ex.countryQuery = it },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                placeholder = { Text("Cerca un paese o una radio") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (ex.countryQuery.isNotEmpty()) {
                        IconButton(onClick = { ex.countryQuery = "" }) { Icon(Icons.Filled.Clear, "Cancella") }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (q.length >= 2) ex.openGlobalSearch(q) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        if (q.length >= 2) {
            item {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { ex.openGlobalSearch(q) },
                    leadingContent = { Icon(Icons.Filled.Public, null, tint = MaterialTheme.colorScheme.primary) },
                    headlineContent = { Text("Cerca «$q» tra le radio di tutto il mondo") },
                    supportingContent = { Text("Per nome della radio") },
                )
            }
        } else {
            item {
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { ex.customOpen = true },
                    leadingContent = { Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary) },
                    headlineContent = { Text("Le mie radio") },
                    supportingContent = {
                        val n = env.custom.items.size
                        Text(if (n == 0) "Aggiungi una radio con il suo indirizzo (tasto +)" else "$n radio aggiunte da te")
                    },
                )
            }
        }
        when {
            ex.countriesLoading && ex.countries.isEmpty() -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            ex.countriesError != null && ex.countries.isEmpty() -> item {
                Column(
                    Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(ex.countriesError ?: "", textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { ex.loadCountries(force = true) }) { Text("Riprova") }
                }
            }
            else -> {
                items(filtered, key = { it.code }) { c -> CountryRow(c) { ex.openCountry(c) } }
                if (filtered.isEmpty() && q.isNotEmpty() && ex.countries.isNotEmpty()) {
                    item {
                        Text(
                            "Nessun paese trovato.",
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CountryRow(c: Country, onClick: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { Text(c.flag, fontSize = 28.sp) },
        headlineContent = { Text(c.localName) },
        supportingContent = { Text("${c.count} radio") },
    )
}

@Composable
private fun FilterDropdown(
    label: String,
    valueLabel: String?,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = valueLabel != null,
            onClick = { open = true },
            label = { Text(valueLabel ?: label, maxLines = 1) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null, Modifier.size(18.dp)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((text, value) in options) {
                DropdownMenuItem(text = { Text(text) }, onClick = { open = false; onSelect(value) })
            }
        }
    }
}

@Composable
private fun StationList(env: AppEnv) {
    val ex = env.explore
    // la ricerca per nome parte poco dopo l'ultimo carattere digitato
    LaunchedEffect(ex.query) {
        if (ex.query == ex.loadedQuery) return@LaunchedEffect
        delay(450)
        ex.reload()
    }
    val stations = ex.stations
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = ex.query,
            onValueChange = { ex.query = it },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            placeholder = { Text("Cerca per nome") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (ex.query.isNotEmpty()) {
                    IconButton(onClick = { ex.query = "" }) { Icon(Icons.Filled.Clear, "Cancella") }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { ex.reload() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (ex.regions.isNotEmpty() && !ex.global) {
                FilterDropdown(
                    label = "Regione",
                    valueLabel = ex.region.ifEmpty { null },
                    options = listOf("Tutto il paese" to "") + ex.regions.map { "${it.name} (${it.count})" to it.name },
                    onSelect = { ex.selectRegion(it) },
                )
            }
            FilterDropdown(
                label = "Genere",
                valueLabel = ex.tag.ifEmpty { null }?.let { t -> Genres.firstOrNull { it.second == t }?.first ?: t },
                options = listOf("Tutti i generi" to "") + Genres,
                onSelect = { ex.selectTag(it) },
            )
            FilterDropdown(
                label = "Ordine",
                valueLabel = Orders.firstOrNull { it.second == ex.order }?.first,
                options = Orders,
                onSelect = { ex.selectOrder(it) },
            )
        }
        val offline = stations.count { !it.online }
        if (stations.isNotEmpty()) {
            Text(
                buildString {
                    append("${stations.size} radio")
                    if (ex.hasMore) append(" caricate")
                    if (offline > 0) append(" · $offline non raggiungibili (in fondo)")
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Spacer(Modifier.height(8.dp))
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(stations, key = { it.uuid }) { st -> StationItem(env, st, stations) }
            item {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when {
                        ex.loading -> CircularProgressIndicator()
                        ex.error != null -> {
                            Text(ex.error ?: "", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { if (stations.isEmpty()) ex.reload() else ex.loadMore() }) { Text("Riprova") }
                        }
                        ex.hasMore -> OutlinedButton(onClick = { ex.loadMore() }) { Text("Carica altre radio") }
                        stations.isEmpty() -> Text(
                            "Nessuna radio trovata con questi filtri.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomList(env: AppEnv) {
    val list = env.custom.items
    if (list.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Radio,
            title = "Nessuna radio personalizzata",
            text = "Se una radio non è nel database, aggiungila con l'indirizzo del suo flusso audio.",
            action = "Aggiungi una radio" to { env.addDialog = true }
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(list, key = { it.uuid }) { st ->
            StationItem(
                env, st, list,
                trailingExtra = {
                    IconButton(onClick = {
                        env.custom.remove(st.uuid)
                        env.favorites.remove(st.uuid)
                    }) { Icon(Icons.Filled.Delete, contentDescription = "Elimina questa radio") }
                }
            )
        }
    }
}
