@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.marco.mlradio.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.marco.mlradio.data.Station
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

/** Logo della radio (da Radio Browser). Senza logo: cerchio con il simbolo della radio. */
@Composable
fun StationLogo(logoUrl: String?, size: Dp, modifier: Modifier = Modifier) {
    val url = logoUrl.orEmpty()
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (url.startsWith("http")) {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size),
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
                    else -> RadioPlaceholder(size)
                }
            }
        } else {
            RadioPlaceholder(size)
        }
    }
}

@Composable
fun StationLogo(station: Station?, size: Dp, modifier: Modifier = Modifier) =
    StationLogo(station?.favicon, size, modifier)

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
            .clickable { env.play(st, queue) }
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
