package com.marco.mlradio.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import com.marco.mlradio.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Loghi delle radio. Ordine: logo scelto dall'utente → logo di Radio Browser → logo trovato sul sito
 * della radio (salvato sul telefono, cercato una volta sola) → segnaposto con le iniziali.
 */
object Logos {
    private lateinit var sp: SharedPreferences
    private lateinit var dir: File
    private lateinit var scope: CoroutineScope

    /** uuid → logo scelto dall'utente (file:// o http) */
    val custom = mutableStateMapOf<String, String>()

    /** uuid → logo trovato sul sito ("" = cercato, nessun risultato) */
    val found = mutableStateMapOf<String, String>()

    /** uuid il cui logo di Radio Browser non si carica */
    private val broken = HashSet<String>()

    /** Cambia quando un logo risulta rotto, per ridisegnare. */
    var brokenRev by androidx.compose.runtime.mutableIntStateOf(0)
        private set
    private val pending = HashSet<String>()
    private val limiter = Semaphore(3)

    private const val RETRY_MS = 7L * 24 * 3600 * 1000

    fun init(context: Context, appScope: CoroutineScope) {
        sp = context.getSharedPreferences("logos", Context.MODE_PRIVATE)
        dir = File(context.filesDir, "logos").apply { mkdirs() }
        scope = appScope
        val now = System.currentTimeMillis()
        for ((k, v) in sp.all) {
            val s = v as? String ?: continue
            when {
                k.startsWith("c:") -> custom[k.substring(2)] = s
                k.startsWith("f:") -> {
                    // "url|data": i tentativi falliti si ripetono dopo una settimana
                    val url = s.substringBefore('|')
                    val ts = s.substringAfter('|', "0").toLongOrNull() ?: 0L
                    if (url.isNotEmpty() || now - ts < RETRY_MS) found[k.substring(2)] = url
                }
            }
        }
    }

    /** Logo da mostrare, o null se serve il segnaposto (eventualmente avvia la ricerca sul sito). */
    fun urlFor(st: Station): String? {
        custom[st.uuid]?.let { return it }
        if (st.favicon.startsWith("http") && st.uuid !in broken) return st.favicon
        return found[st.uuid]?.ifEmpty { null }
    }

    /** Da chiamare fuori dalla composizione (LaunchedEffect): cerca il logo sul sito se serve. */
    fun ensure(st: Station) {
        if (custom.containsKey(st.uuid)) return
        if (st.favicon.startsWith("http") && st.uuid !in broken) return
        if (st.homepage.isBlank()) return
        search(st)
    }

    /** Il logo di Radio Browser non si carica: si prova a trovarne un altro. */
    fun markBroken(st: Station) {
        if (custom.containsKey(st.uuid) || !broken.add(st.uuid)) return
        val f = found[st.uuid]
        if (f == null) search(st) else found[st.uuid] = f // forza la ricomposizione
        brokenRev++
    }

    fun setCustomUrl(uuid: String, url: String) {
        custom[uuid] = url
        sp.edit().putString("c:$uuid", url).apply()
    }

    /** Copia (ridotta) di un'immagine scelta dalla galleria. */
    suspend fun setCustomImage(context: Context, uuid: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return@withContext false
            val scale = minOf(1f, 512f / maxOf(bmp.width, bmp.height))
            val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
            val f = File(dir, "$uuid-${System.currentTimeMillis()}.png")
            f.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
            dir.listFiles()?.filter { it.name.startsWith("$uuid-") && it != f }?.forEach { it.delete() }
            withContext(Dispatchers.Main) { setCustomUrl(uuid, Uri.fromFile(f).toString()) }
            true
        } catch (e: Exception) {
            AppLog.w("Loghi", "Immagine non valida", e)
            false
        }
    }

    fun clearCustom(uuid: String) {
        custom.remove(uuid)
        sp.edit().remove("c:$uuid").apply()
        dir.listFiles()?.filter { it.name.startsWith("$uuid-") }?.forEach { it.delete() }
    }

    /** Cerca di nuovo il logo sul sito, ignorando i risultati salvati. */
    fun searchAgain(st: Station) {
        found.remove(st.uuid)
        sp.edit().remove("f:${st.uuid}").apply()
        search(st, force = true)
    }

    private fun search(st: Station, force: Boolean = false) {
        if (!::scope.isInitialized) return
        if (!force && (found.containsKey(st.uuid))) return
        if (!pending.add(st.uuid)) return
        scope.launch {
            val url = try {
                limiter.withPermit { LogoFinder.find(st) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            pending.remove(st.uuid)
            found[st.uuid] = url.orEmpty()
            sp.edit().putString("f:${st.uuid}", url.orEmpty() + "|" + System.currentTimeMillis()).apply()
            if (url != null) AppLog.d("Loghi", "${st.name}: $url")
        }
    }
}

/** Ricerca del logo sul sito della radio. */
object LogoFinder {
    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Mobile Safari/537.36"

    suspend fun find(st: Station): String? = withContext(Dispatchers.IO) {
        val home = st.homepage.trim()
        if (!home.startsWith("http")) return@withContext null
        val base = try { URL(home) } catch (_: Exception) { return@withContext null }
        val candidates = ArrayList<Pair<String, Int>>() // url, punteggio
        try {
            val html = fetchText(home, 400_000)
            if (html != null) candidates += parse(html, base)
        } catch (_: Exception) {
        }
        val root = "${base.protocol}://${base.host}"
        candidates += "$root/apple-touch-icon.png" to 40
        candidates += "$root/apple-touch-icon-precomposed.png" to 39
        for ((u, _) in candidates.sortedByDescending { it.second }.distinctBy { it.first }.take(8)) {
            if (isImage(u)) return@withContext u
        }
        // ultima possibilità: servizio pubblico delle icone dei siti
        val g = "https://www.google.com/s2/favicons?domain=${base.host}&sz=256"
        if (isImage(g, minBytes = 1500)) g else null
    }

    private val linkRe = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val metaRe = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
    private fun attr(tag: String, name: String): String? =
        Regex("\\b$name\\s*=\\s*([\"'])(.*?)\\1", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(2)
            ?: Regex("\\b$name\\s*=\\s*([^\\s>]+)", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)

    internal fun parse(html: String, base: URL): List<Pair<String, Int>> {
        val out = ArrayList<Pair<String, Int>>()
        fun abs(u: String): String? = try {
            URL(base, u.replace("&amp;", "&").trim()).toString()
        } catch (_: Exception) { null }
        for (m in linkRe.findAll(html)) {
            val tag = m.value
            val rel = attr(tag, "rel")?.lowercase() ?: continue
            val href = attr(tag, "href") ?: continue
            if (href.startsWith("data:")) continue
            val size = attr(tag, "sizes")?.substringBefore('x')?.toIntOrNull() ?: 0
            val score = when {
                "apple-touch-icon" in rel -> 60 + size / 20
                "icon" in rel && (size >= 96 || href.endsWith(".svg").not() && size >= 64) -> 50 + size / 20
                "icon" in rel -> 20 + size / 20
                else -> continue
            }
            if (href.endsWith(".svg", ignoreCase = true)) continue // Coil senza decoder SVG
            abs(href)?.let { out += it to score }
        }
        for (m in metaRe.findAll(html)) {
            val tag = m.value
            val prop = (attr(tag, "property") ?: attr(tag, "name") ?: "").lowercase()
            if (prop == "og:image" || prop == "twitter:image" || prop == "og:logo") {
                attr(tag, "content")?.let { c -> abs(c)?.let { out += it to 45 } }
            }
        }
        return out
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 10000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
        }

    private fun fetchText(url: String, max: Int): String? {
        val c = open(url)
        return try {
            if (c.responseCode !in 200..299) return null
            val bytes = c.inputStream.use { s ->
                val buf = java.io.ByteArrayOutputStream()
                val b = ByteArray(16 * 1024)
                while (buf.size() < max) {
                    val n = s.read(b); if (n < 0) break; buf.write(b, 0, n)
                }
                buf.toByteArray()
            }
            String(bytes, Charsets.UTF_8)
        } finally {
            c.disconnect()
        }
    }

    /** L'indirizzo restituisce davvero un'immagine abbastanza grande? */
    private fun isImage(url: String, minBytes: Int = 600): Boolean = try {
        val c = open(url)
        try {
            val type = c.contentType.orEmpty().lowercase()
            if (c.responseCode !in 200..299 || !type.startsWith("image/") || "svg" in type) false
            else {
                val head = c.inputStream.use { s -> val b = ByteArray(minBytes); var t = 0
                    while (t < minBytes) { val n = s.read(b, t, minBytes - t); if (n < 0) break; t += n }; t }
                if (head < minBytes) false
                else {
                    // le icone minuscole (16-32 px) non servono come logo
                    true
                }
            }
        } finally {
            c.disconnect()
        }
    } catch (_: Exception) {
        false
    }
}
