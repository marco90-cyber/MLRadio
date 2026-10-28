@file:OptIn(ExperimentalMaterial3Api::class)

package com.marco.mlradio.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.marco.mlradio.BuildConfig

@Composable
fun AboutScreen(env: AppEnv) {
    val ctx = LocalContext.current
    BackHandler { env.showAbout = false }
    val transparent = ListItemDefaults.colors(containerColor = Color.Transparent)
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .blockTouches()
    ) {
        TopAppBar(
            title = { Text("Informazioni") },
            navigationIcon = {
                IconButton(onClick = { env.showAbout = false }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                }
            }
        )
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                RadioPlaceholder(88.dp)
                Spacer(Modifier.height(12.dp))
                Text("MLRadio", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Versione ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Le radio di tutto il mondo, con equalizzatore, cronologia dei brani e Android Auto.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            SectionTitle("Progetto")
            Link(ctx, "Codice sorgente e segnalazioni", BuildConfig.REPO_URL, transparent)
            Link(ctx, "Segnala un problema", BuildConfig.REPO_URL + "/issues", transparent)
            ListItem(
                colors = transparent,
                headlineContent = { Text("Autore") },
                supportingContent = { Text("Marco (marco90-cyber)") }
            )

            SectionTitle("Dati delle radio")
            Link(ctx, "Radio Browser", "https://www.radio-browser.info", transparent,
                "L'elenco delle radio, i loghi e le informazioni arrivano dal database libero e collaborativo Radio Browser.")
            Text(
                "I flussi audio e i loghi appartengono alle rispettive emittenti: MLRadio non ospita né ritrasmette alcun contenuto, " +
                    "riproduce direttamente i flussi pubblici indicati dalle radio.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            SectionTitle("Licenze")
            LicenseRow("MLRadio", "GNU GPL v3 o successiva")
            LicenseRow("libsoxr (ricampionamento)", "GNU LGPL v2.1 o successiva")
            LicenseRow("AndroidX Media3, Jetpack Compose, Material 3", "Apache License 2.0")
            LicenseRow("Coil (caricamento dei loghi)", "Apache License 2.0")
            LicenseRow("Guava", "Apache License 2.0")
            LicenseRow("Filtri dell'equalizzatore", "Audio EQ Cookbook di Robert Bristow-Johnson")
            LicenseRow("Ispirazione dell'interfaccia", "Tryptify (GPL-3.0)")

            SectionTitle("Privacy")
            Text(
                "MLRadio non ha account, pubblicità né statistiche. Preferiti, cronologia e impostazioni restano sul telefono. " +
                    "L'app contatta solo Radio Browser e i server delle radio che ascolti; i server delle radio possono vedere " +
                    "il tuo indirizzo IP come in qualsiasi altro ascolto in streaming.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Link(ctx: android.content.Context, title: String, url: String, colors: androidx.compose.material3.ListItemColors, sub: String? = null) {
    ListItem(
        colors = colors,
        headlineContent = { Text(title) },
        supportingContent = { Text(sub ?: url.removePrefix("https://")) },
        modifier = Modifier.clickable { openUrl(ctx, url) }
    )
}

@Composable
private fun LicenseRow(name: String, license: String) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(name) },
        supportingContent = { Text(license) }
    )
}
