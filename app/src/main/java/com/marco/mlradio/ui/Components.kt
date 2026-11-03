@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.marco.mlradio.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.marco.mlradio.data.Logos
import com.marco.mlradio.data.Station
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.sp
import com.marco.mlradio.data.flagEmoji

/** Logo standard: cerchio con il simbolo della radio. Si usa quando la radio non ha logo o non si carica. */
@Composable
fun RadioPlaceholder(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Radio, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

/** Iniziali o numero della radio per il segnaposto: «Radio 105 - Zoo» → 105, «Radio Kiss Kiss» → KK. */
fun stationInitials(name: String): String {
    val stop = setOf("radio", "fm", "am", "web", "the", "la", "il", "lo", "di", "del", "della", "de", "der", "die", "das", "le", "el", "station", "online", "tv")
    val words = name.split(Regex("[^\\p{L}\\p{N}%]+")).filter { it.isNotBlank() }
    val useful = words.filter { it.lowercase() !in stop }.ifEmpty { words }
    val first = useful.firstOrNull() ?: return "R"
    // numeri e sigle brevi si mostrano interi (105, R101, M2O, RDS, RTL)
    if (first.any { it.isDigit() } || (first.length <= 4 && first.all { !it.isLetter() || it.isUpperCase() })) {
        return first.take(4).uppercase()
    }
    val second = useful.getOrNull(1)
    return (first.take(1) + (second?.take(1) ?: "")).uppercase()
}

/** Colore stabile ricavato dal nome. */
private fun nameColor(name: String, dark: Boolean): Color {
    val hue = ((name.lowercase().hashCode().toLong() and 0xffffffffL) % 360).toFloat()
    return Color.hsl(hue, if (dark) 0.45f else 0.55f, if (dark) 0.38f else 0.45f)
}

/** Segnaposto quando non c'è un logo: iniziali o numero della radio su un colore ricavato dal nome. */
@Composable
fun InitialsPlaceholder(name: String, size: Dp, modifier: Modifier = Modifier) {
    val text = stationInitials(name)
    val dark = LocalDarkTheme.current
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size / 5))
            .background(nameColor(name, dark)),
        contentAlignment = Alignment.Center
    ) {
        val fs = (size.value * when (text.length) { 1, 2 -> 0.40f; 3 -> 0.32f; else -> 0.26f }).sp
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = fs, maxLines = 1, softWrap = false)
    }
}

/** Logo da un indirizzo (http o file). Senza logo o se non si carica: [fallback]. */
@Composable
fun StationLogo(logoUrl: String?, size: Dp, modifier: Modifier = Modifier, onError: () -> Unit = {}, fallback: @Composable () -> Unit = { RadioPlaceholder(size) }) {
    val url = logoUrl.orEmpty()
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (url.startsWith("http") || url.startsWith("file:")) {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size),
                onError = { _ -> onError() },
            ) {
                when (painter.state) {
                    is AsyncImagePainter.State.Success -> Box(
                        Modifier
                            .size(size)
                            .clip(RoundedCornerShape(size / 5))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        this@SubcomposeAsyncImage.SubcomposeAsyncImageContent(Modifier.padding(size / 14))
                    }
                    else -> fallback()
                }
            }
        } else {
            fallback()
        }
    }
}

/** Logo della radio: scelto da te, di Radio Browser o trovato sul sito; altrimenti le iniziali. */
@Composable
fun StationLogo(station: Station?, size: Dp, modifier: Modifier = Modifier) {
    if (station == null) {
        RadioPlaceholder(size, modifier); return
    }
    Logos.brokenRev // ridisegna quando un logo risulta rotto
    val url = Logos.urlFor(station)
    LaunchedEffect(station.uuid, url) { if (url == null) Logos.ensure(station) }
    StationLogo(
        url, size, modifier,
        onError = { if (url == station.favicon) Logos.markBroken(station) },
        fallback = { InitialsPlaceholder(station.name, size) },
    )
}

/** Riga di una radio: logo, nome, luogo/formato, cuore dei preferiti. Tocco = ascolta. */
@Composable
fun StationItem(
    env: AppEnv,
    st: Station,
    queue: List<Station>,
    modifier: Modifier = Modifier,
    trailingExtra: @Composable (() -> Unit)? = null,
) {
    val playing = env.engine.currentStation?.uuid == st.uuid && env.engine.wantsPlay
    val isFav = env.favorites.items.any { it.uuid == st.uuid }
    val dim = if (st.online) 1f else 0.5f
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = modifier
            .combinedClickable(
                onClick = { env.play(st, queue) },
                onLongClick = { env.feedback.tapScreen(); env.logoFor = st },
                onLongClickLabel = "Cambia logo",
            )
            .alpha(dim),
        leadingContent = { StationLogo(st, 52.dp) },
        headlineContent = {
            Text(
                st.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontWeight = if (playing) FontWeight.Bold else FontWeight.Medium,
                color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = {
            val place = st.place()
            val fmt = listOfNotNull(
                st.codec.takeIf { it.isNotBlank() && !it.equals("unknown", true) },
                st.bitrate.takeIf { it > 0 }?.let { "$it kbps" },
            ).joinToString(" ")
            Column {
                val flag = if (st.countryCode.length == 2) flagEmoji(st.countryCode) else ""
                val line = listOf(listOf(flag, place).filter { it.isNotBlank() }.joinToString(" "), fmt)
                    .filter { it.isNotBlank() }.joinToString(" · ")
                Text(line.ifBlank { "Radio web" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!st.online) {
                    Text(
                        "non raggiungibile", maxLines = 1,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                trailingExtra?.invoke()
                IconButton(onClick = { env.toggleFavorite(st) }) {
                    Icon(
                        if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (isFav) "Togli dai preferiti" else "Aggiungi ai preferiti",
                        tint = if (isFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    )
}

/** Etichetta tecnica (formato, bitrate, frequenza...). */
@Composable
fun InfoBadge(text: String, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (emphasized) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (emphasized) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    action: Pair<String, () -> Unit>? = null,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = action.second) { Text(action.first) }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
}
