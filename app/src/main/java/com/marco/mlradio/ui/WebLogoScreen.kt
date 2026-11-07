@file:OptIn(ExperimentalMaterial3Api::class)

package com.marco.mlradio.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.marco.mlradio.data.Logos
import com.marco.mlradio.data.Station
import kotlinx.coroutines.launch

private fun googleImages(q: String) = "https://www.google.com/search?tbm=isch&hl=it&q=" + Uri.encode(q)
private fun bingImages(q: String) = "https://www.bing.com/images/search?q=" + Uri.encode(q)

/**
 * Ricerca del logo sul web dentro l'app: si tocca un'immagine per aprirla grande, poi si tiene premuta
 * e si sceglie «Usa come logo». L'immagine viene scaricata, rifilata e salvata.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebLogoScreen(st: Station, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val query = "${st.name} logo"
    var progress by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    BackHandler {
        val w = webView
        if (w != null && w.canGoBack()) w.goBack() else onClose()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .blockTouches()
            .safeDrawingPadding()
    ) {
        TopAppBar(
            title = {
                Column {
                    Text("Cerca il logo", maxLines = 1)
                    Text(
                        "Tocca un'immagine, poi tienila premuta", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            },
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Chiudi") } },
            actions = {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Altro") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Cerca con Google") }, onClick = { menu = false; webView?.loadUrl(googleImages(query)) })
                        DropdownMenuItem(text = { Text("Cerca con Bing") }, onClick = { menu = false; webView?.loadUrl(bingImages(query)) })
                        DropdownMenuItem(text = { Text("Cerca «${st.name}» senza «logo»") }, onClick = { menu = false; webView?.loadUrl(googleImages(st.name)) })
                    }
                }
            }
        )
        if (progress in 1..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                WebView(c).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    webViewClient = object : WebViewClient() {
                        // tutti i link restano nella pagina interna
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val scheme = request.url.scheme.orEmpty()
                            return scheme != "http" && scheme != "https"
                        }
                    }
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
                    }
                    setOnLongClickListener { v ->
                        val hit = (v as WebView).hitTestResult
                        when (hit.type) {
                            WebView.HitTestResult.IMAGE_TYPE -> {
                                hit.extra?.let { picked = it }; true
                            }
                            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                                val msg = android.os.Handler(android.os.Looper.getMainLooper()) { m ->
                                    val src = m.data.getString("src") ?: hit.extra
                                    if (src != null) picked = src
                                    true
                                }.obtainMessage()
                                v.requestImageRef(msg)
                                true
                            }
                            else -> false
                        }
                    }
                    setDownloadListener { url, _, _, mime, _ ->
                        if (mime.startsWith("image/")) picked = url
                    }
                    loadUrl(googleImages(query))
                    webView = this
                }
            },
            onRelease = { it.stopLoading(); it.destroy() }
        )
    }

    val url = picked
    if (url != null) {
        AlertDialog(
            onDismissRequest = { if (!saving) picked = null },
            title = { Text("Usare questa immagine?") },
            text = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                        AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(140.dp))
                        if (saving) CircularProgressIndicator()
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Se l'immagine è piccola o sgranata, tocca prima il risultato per aprirlo grande e tienilo premuto lì.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = !saving, onClick = {
                    saving = true
                    scope.launch {
                        val ok = Logos.setCustomFromWeb(st.uuid, url, webView?.url)
                        saving = false
                        picked = null
                        Toast.makeText(ctx, if (ok) "Logo aggiornato" else "Impossibile usare questa immagine", Toast.LENGTH_SHORT).show()
                        if (ok) onClose()
                    }
                }) { Text("Usa come logo") }
            },
            dismissButton = { TextButton(enabled = !saving, onClick = { picked = null }) { Text("Annulla") } }
        )
    }
}
