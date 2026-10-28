package com.marco.mlradio.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Una radio. I dati arrivano da Radio Browser oppure dall'utente (radio personalizzate). */
data class Station(
    val uuid: String,
    val name: String,
    /** Indirizzo del flusso audio (già risolto da Radio Browser, se disponibile). */
    val url: String,
    val favicon: String = "",
    val country: String = "",
    val countryCode: String = "",
    val state: String = "",
    val language: String = "",
    val tags: String = "",
    val codec: String = "",
    val bitrate: Int = 0,
    val hls: Boolean = false,
    val homepage: String = "",
    val votes: Int = 0,
    val clickCount: Int = 0,
    /** Esito dell'ultimo controllo di Radio Browser: false = al momento non raggiungibile. */
    val online: Boolean = true,
    val custom: Boolean = false,
    /** Altri indirizzi della stessa radio (doppioni raggruppati): usati se il primo non funziona. */
    val alts: List<String> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("uuid", uuid)
        put("name", name)
        put("url", url)
        put("favicon", favicon)
        put("country", country)
        put("cc", countryCode)
        put("state", state)
        put("lang", language)
        put("tags", tags)
        put("codec", codec)
        put("br", bitrate)
        put("hls", hls)
        put("home", homepage)
        put("votes", votes)
        put("clicks", clickCount)
        put("online", online)
        put("custom", custom)
        put("alts", JSONArray(alts))
    }

    /** Luogo da mostrare: regione se c'è, altrimenti il paese. */
    fun place(): String = when {
        state.isNotBlank() && country.isNotBlank() -> "$state, ${countryLocalName()}"
        state.isNotBlank() -> state
        country.isNotBlank() -> countryLocalName()
        else -> ""
    }

    fun countryLocalName(): String {
        if (countryCode.length != 2) return country
        val n = Locale("", countryCode).getDisplayCountry(Locale.ITALIAN)
        return if (n.isBlank() || n.equals(countryCode, ignoreCase = true)) country else n
    }

    /** Generi/tag, solo i primi, in forma leggibile. */
    fun genres(max: Int = 3): List<String> =
        tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(max)

    companion object {
        fun fromJson(o: JSONObject): Station = Station(
            uuid = o.optString("uuid"),
            name = o.optString("name"),
            url = o.optString("url"),
            favicon = o.optString("favicon"),
            country = o.optString("country"),
            countryCode = o.optString("cc"),
            state = o.optString("state"),
            language = o.optString("lang"),
            tags = o.optString("tags"),
            codec = o.optString("codec"),
            bitrate = o.optInt("br", 0),
            hls = o.optBoolean("hls", false),
            homepage = o.optString("home"),
            votes = o.optInt("votes", 0),
            clickCount = o.optInt("clicks", 0),
            online = o.optBoolean("online", true),
            custom = o.optBoolean("custom", false),
            alts = o.optJSONArray("alts")?.let { a -> List(a.length()) { a.optString(it) }.filter { it.isNotBlank() } }
                ?: emptyList(),
        )

        fun fromJsonString(s: String?): Station? {
            if (s.isNullOrBlank()) return null
            return try {
                fromJson(JSONObject(s)).takeIf { it.uuid.isNotBlank() && it.url.isNotBlank() }
            } catch (_: Exception) {
                null
            }
        }
    }
}

/** Lettura delle risposte di Radio Browser e pulizia dell'elenco. */
object StationParser {

    /** Converte un oggetto JSON di Radio Browser. Restituisce null se mancano i dati essenziali. */
    fun fromApi(o: JSONObject): Station? {
        val uuid = o.optString("stationuuid")
        val name = o.optString("name").replace(Regex("\\s+"), " ").trim()
        val resolved = o.optString("url_resolved")
        val url = resolved.ifBlank { o.optString("url") }
        if (uuid.isBlank() || name.isBlank() || url.isBlank()) return null
        return Station(
            uuid = uuid,
            name = name,
            url = url.trim(),
            favicon = o.optString("favicon").trim(),
            country = o.optString("country").trim(),
            countryCode = o.optString("countrycode").trim().uppercase(Locale.ROOT),
            state = o.optString("state").trim(),
            language = o.optString("language").trim(),
            tags = o.optString("tags").trim(),
            codec = o.optString("codec").trim(),
            bitrate = o.optInt("bitrate", 0),
            hls = o.optInt("hls", 0) == 1,
            homepage = o.optString("homepage").trim(),
            votes = o.optInt("votes", 0),
            clickCount = o.optInt("clickcount", 0),
            online = o.optInt("lastcheckok", 1) == 1,
        )
    }

    fun parseList(json: String): List<Station> {
        val a = JSONArray(json)
        val out = ArrayList<Station>(a.length())
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            fromApi(o)?.let { out.add(it) }
        }
        return out
    }

    private fun norm(s: String): String = s.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), "")

    /** Chiave di raggruppamento: stesso nome e stesso paese = stessa radio. */
    fun groupKey(s: Station): String = norm(s.name) + "|" + s.countryCode.ifBlank { norm(s.country) }

    private fun better(a: Station, b: Station): Boolean {
        if (a.online != b.online) return a.online
        if (a.votes != b.votes) return a.votes > b.votes
        return a.bitrate > b.bitrate
    }

    /**
     * Raggruppa i doppioni (stesso nome e paese): resta la voce migliore (raggiungibile, più votata),
     * gli altri indirizzi diventano alternative da provare se il flusso principale non funziona.
     */
    fun dedupe(list: List<Station>): List<Station> {
        val order = ArrayList<String>()
        val groups = HashMap<String, MutableList<Station>>()
        for (s in list) {
            val k = groupKey(s)
            if (k.startsWith("|")) {
                // senza nome utilizzabile non si raggruppa
                order.add(s.uuid); groups[s.uuid] = mutableListOf(s)
                continue
            }
            val g = groups[k]
            if (g == null) {
                order.add(k); groups[k] = mutableListOf(s)
            } else g.add(s)
        }
        return order.map { k ->
            val g = groups.getValue(k)
            var best = g[0]
            for (s in g) if (better(s, best)) best = s
            if (g.size == 1) best
            else {
                val alts = g.filter { it !== best }
                    .sortedByDescending { it.online }
                    .map { it.url }
                    .filter { it != best.url }
                    .distinct()
                    .take(3)
                best.copy(alts = (best.alts + alts).distinct().take(3))
            }
        }
    }

    /** Le radio raggiungibili prima, quelle non raggiungibili in fondo (l'ordine interno resta). */
    fun sortOnlineFirst(list: List<Station>): List<Station> =
        list.filter { it.online } + list.filter { !it.online }
}
