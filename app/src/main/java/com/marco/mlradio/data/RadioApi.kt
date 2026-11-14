package com.marco.mlradio.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import java.net.URLEncoder
import java.text.Collator
import java.util.Locale
import com.marco.mlradio.util.AppLog

/**
 * Paese di Radio Browser. [names] sono tutti i nomi con cui il server lo elenca
 * (a volte lo stesso codice compare più volte con nomi diversi).
 */
data class Country(val name: String, val code: String, val count: Int, val names: List<String> = listOf(name)) {
    val continent: Continent get() = Continents.of(code)

    val localName: String
        get() {
            val n = Locale("", code).getDisplayCountry(Locale.ITALIAN)
            return if (n.isBlank() || n.equals(code, ignoreCase = true)) name else n
        }

    val flag: String get() = flagEmoji(code)
}

/** Regione pulita; [variants] sono le scritture originali di Radio Browser (per cercarne le radio). */
data class RegionInfo(val name: String, val count: Int, val variants: List<String> = listOf(name))

data class SearchFilter(
    val name: String = "",
    val countryCode: String = "",
    val state: String = "",
    /** true = la regione deve coincidere esattamente (si usa con le scritture originali). */
    val stateExact: Boolean = false,
    val tag: String = "",
    /** votes (più votate), clickcount (più ascoltate), name (A-Z) */
    val order: String = "votes",
)

fun flagEmoji(code: String): String {
    if (code.length != 2 || !code.all { it.isLetter() }) return "🌐"
    return code.uppercase(Locale.ROOT).map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
}

/**
 * Client di Radio Browser (https://www.radio-browser.info): database gratuito e aperto di radio web.
 * Nessuna chiave: si prova un elenco di server e si ricorda l'ultimo che ha risposto.
 */
object RadioApi {
    private val servers = listOf(
        "https://de1.api.radio-browser.info",
        "https://de2.api.radio-browser.info",
        "https://nl1.api.radio-browser.info",
        "https://at1.api.radio-browser.info",
        "https://all.api.radio-browser.info",
    )

    @Volatile
    private var preferred = 0

    const val USER_AGENT = "MLRadio/1.0 (Android; +https://github.com/marco90-cyber/MLRadio)"

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun httpGet(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 30000
            c.setRequestProperty("User-Agent", USER_AGENT)
            c.setRequestProperty("Accept", "application/json")
            val code = c.responseCode
            if (code !in 200..299) throw IOException("HTTP $code")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun get(path: String): String {
        var last: Exception? = null
        for (k in servers.indices) {
            val idx = (preferred + k) % servers.size
            val host = servers[idx].removePrefix("https://")
            val t0 = System.currentTimeMillis()
            try {
                val text = httpGet(servers[idx] + path)
                preferred = idx
                AppLog.i("Rete", "GET $host${shorten(path)} → ${text.length / 1024} KB in ${System.currentTimeMillis() - t0} ms")
                return text
            } catch (e: Exception) {
                AppLog.w("Rete", "GET $host${shorten(path)} fallita dopo ${System.currentTimeMillis() - t0} ms: ${e.javaClass.simpleName}: ${e.message}")
                last = e
            }
        }
        AppLog.e("Rete", "Nessun server Radio Browser ha risposto per ${shorten(path)}")
        throw last ?: IOException("Nessun server raggiungibile")
    }

    private fun shorten(path: String) = if (path.length > 160) path.take(160) + "…" else path

    /** Elenco dei paesi con il numero di radio. Il paese del telefono è in cima, poi ordine alfabetico. */
    suspend fun countries(): List<Country> = withContext(Dispatchers.IO) {
        parseCountries(get("/json/countries"), Locale.getDefault().country)
    }

    /**
     * Legge l'elenco dei paesi e unisce le voci con lo stesso codice ISO (il server a volte ne restituisce
     * più di una: due righe con la stessa chiave mandavano in crash l'elenco di Esplora).
     */
    fun parseCountries(json: String, phoneCountry: String): List<Country> {
        val a = JSONArray(json)
        class Acc(var count: Int = 0, val names: LinkedHashMap<String, Int> = LinkedHashMap())
        val byCode = LinkedHashMap<String, Acc>()
        var rows = 0
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val code = o.optString("iso_3166_1").trim().uppercase(Locale.ROOT)
            val name = o.optString("name").trim()
            val n = o.optInt("stationcount", 0)
            if (code.length != 2 || !code.all { it in 'A'..'Z' } || name.isEmpty() || n <= 0) continue
            rows++
            val acc = byCode.getOrPut(code) { Acc() }
            acc.count += n
            acc.names[name] = (acc.names[name] ?: 0) + n
        }
        val dup = byCode.filterValues { it.names.size > 1 }
        if (dup.isNotEmpty()) {
            AppLog.w("Esplora", "Paesi doppi dal server, uniti: " + dup.entries.joinToString { "${it.key}=${it.value.names.keys}" })
        }
        val list = byCode.map { (code, acc) ->
            val names = acc.names.entries.sortedByDescending { it.value }.map { it.key }
            Country(names.first(), code, acc.count, names)
        }
        val here = phoneCountry.uppercase(Locale.ROOT)
        val collator = Collator.getInstance(Locale.ITALIAN)
        AppLog.i("Esplora", "Paesi: $rows voci dal server → ${list.size} paesi, ${list.sumOf { it.count }} radio")
        return list.sortedWith { x, y ->
            val xh = x.code == here
            val yh = y.code == here
            when {
                xh != yh -> if (xh) -1 else 1
                else -> collator.compare(x.localName, y.localName)
            }
        }
    }

    /** Regioni/stati di un paese (tutti i nomi inglesi con cui il server lo conosce). Vuoto se non disponibili. */
    suspend fun states(country: Country): List<RegionInfo> = withContext(Dispatchers.IO) {
        val wanted = country.names.map { it.lowercase(Locale.ROOT) }.toSet()
        val raw = ArrayList<Pair<String, Int>>()
        var result = emptyList<RegionInfo>()
        for (name in country.names.take(3)) {
            val c = enc(name).replace("+", "%20")
            // Più percorsi: il formato accettato dal server cambia fra versioni.
            for (path in listOf("/json/states/$c", "/json/states/$c/")) {
                val text = try {
                    get(path)
                } catch (_: Exception) {
                    continue
                }
                try {
                    val a = JSONArray(text)
                    for (i in 0 until a.length()) {
                        val o = a.optJSONObject(i) ?: continue
                        if (o.optString("country").trim().lowercase(Locale.ROOT) !in wanted) continue
                        raw += o.optString("name") to o.optInt("stationcount", 0)
                    }
                } catch (e: Exception) {
                    AppLog.w("Esplora", "Risposta regioni non leggibile ($path)", e)
                }
                break
            }
        }
        result = Regions.merge(raw.distinct(), country.code, country.name)
        AppLog.i("Esplora", "Regioni di ${country.code}: ${raw.size} voci grezze → ${result.size} regioni")
        result
    }

    /** Radio di una regione: una ricerca per ogni scrittura originale, poi unione senza doppioni. */
    suspend fun searchRegion(f: SearchFilter, region: RegionInfo, limitEach: Int = 300): List<Station> = coroutineScope {
        val variants = region.variants.take(25)
        val pages = variants.map { v ->
            async {
                try {
                    search(f.copy(state = v, stateExact = true), 0, limitEach)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }.awaitAll()
        val seen = HashSet<String>()
        val all = pages.flatten().filter { seen.add(it.uuid) }
        when (f.order) {
            "clickcount" -> all.sortedByDescending { it.clickCount }
            "name" -> {
                val col = Collator.getInstance(Locale.ITALIAN)
                all.sortedWith { a, b -> col.compare(a.name, b.name) }
            }
            else -> all.sortedByDescending { it.votes }
        }
    }

    private fun query(f: SearchFilter, offset: Int, limit: Int): String {
        val p = ArrayList<Pair<String, String>>()
        if (f.name.isNotBlank()) p += "name" to f.name.trim()
        if (f.countryCode.isNotBlank()) p += "countrycode" to f.countryCode
        if (f.state.isNotBlank()) {
            p += "state" to f.state
            if (f.stateExact) p += "state_exact" to "true"
        }
        if (f.tag.isNotBlank()) p += "tag" to f.tag
        // Si mostrano anche le radio non raggiungibili (finiranno in fondo all'elenco)
        p += "hidebroken" to "false"
        p += "order" to f.order
        p += "reverse" to (if (f.order == "name") "false" else "true")
        p += "limit" to limit.toString()
        p += "offset" to offset.toString()
        return p.joinToString("&") { enc(it.first) + "=" + enc(it.second) }
    }

    suspend fun search(filter: SearchFilter, offset: Int, limit: Int): List<Station> = withContext(Dispatchers.IO) {
        StationParser.parseList(get("/json/stations/search?" + query(filter, offset, limit)))
    }

    /** Dati aggiornati di più radio (preferiti) in base all'identificativo univoco. */
    suspend fun byUuids(uuids: List<String>): List<Station> = withContext(Dispatchers.IO) {
        if (uuids.isEmpty()) return@withContext emptyList<Station>()
        val out = ArrayList<Station>()
        for (chunk in uuids.chunked(40)) {
            val text = try {
                get("/json/stations/byuuid?uuids=" + enc(chunk.joinToString(",")))
            } catch (_: Exception) {
                null
            }
            if (text != null) {
                out.addAll(StationParser.parseList(text))
            } else {
                // percorso alternativo: una richiesta per radio
                for (id in chunk) {
                    try {
                        out.addAll(StationParser.parseList(get("/json/stations/byuuid/" + enc(id))))
                    } catch (_: Exception) {
                    }
                }
            }
        }
        out
    }

    /**
     * Indirizzo aggiornato del flusso. Se Radio Browser ha cambiato il link della radio, si usa
     * quello nuovo (e la richiesta conta anche come ascolto, come richiesto dal servizio).
     * Se la richiesta fallisce si usa l'ultimo indirizzo noto.
     */
    suspend fun resolveUrl(st: Station): String = withContext(Dispatchers.IO) {
        if (st.custom || st.uuid.startsWith("custom-")) return@withContext st.url
        try {
            val o = JSONObject(get("/json/url/" + enc(st.uuid)))
            val u = o.optString("url").trim()
            if (o.optBoolean("ok", true) && u.startsWith("http")) u else st.url
        } catch (_: Exception) {
            st.url
        }
    }
}
