package com.marco.mlradio.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Elenco di radio salvato sul telefono (preferiti, radio personalizzate). Osservabile da Compose. */
class StationStore(context: Context, name: String) {
    private val sp = context.applicationContext.getSharedPreferences("mlradio_$name", Context.MODE_PRIVATE)

    var items by mutableStateOf(load())
        private set

    private fun load(): List<Station> = try {
        val a = JSONArray(sp.getString("list", "[]"))
        List(a.length()) { Station.fromJson(a.getJSONObject(it)) }.filter { it.uuid.isNotBlank() }
    } catch (_: Exception) {
        emptyList()
    }

    private fun save(list: List<Station>) {
        items = list
        val a = JSONArray()
        for (s in list) a.put(s.toJson())
        sp.edit().putString("list", a.toString()).apply()
    }

    fun contains(uuid: String): Boolean = items.any { it.uuid == uuid }

    fun add(st: Station) {
        if (!contains(st.uuid)) save(items + st)
    }

    fun remove(uuid: String) {
        if (contains(uuid)) save(items.filter { it.uuid != uuid })
    }

    fun toggle(st: Station) {
        if (contains(st.uuid)) remove(st.uuid) else add(st)
    }

    fun replaceAll(list: List<Station>) = save(list)
}

/** Memoria delle radio viste di recente: serve ad Android Auto e al servizio per ritrovare una radio dal suo id. */
class StationDirectory(
    private val favorites: StationStore,
    private val custom: StationStore,
) {
    private val map = LinkedHashMap<String, Station>()

    @Synchronized
    fun remember(list: List<Station>) {
        for (s in list) map[s.uuid] = s
        while (map.size > 2000) map.remove(map.keys.first())
    }

    @Synchronized
    fun find(id: String): Station? =
        favorites.items.firstOrNull { it.uuid == id }
            ?: custom.items.firstOrNull { it.uuid == id }
            ?: map[id]
}

data class HistoryEntry(
    val ts: Long,
    val title: String,
    val stationName: String,
    val stationUuid: String,
    val favicon: String,
)

/** Cronologia dei brani ascoltati (titolo, radio, data e ora). I più recenti per primi. */
class HistoryStore(context: Context, private val scope: CoroutineScope) {
    private val file = File(context.applicationContext.filesDir, "history.json")
    private val lock = Any()

    var items by mutableStateOf(load())
        private set

    private fun load(): List<HistoryEntry> = try {
        if (!file.exists()) emptyList()
        else {
            val a = JSONArray(file.readText())
            List(a.length()) {
                val o = a.getJSONObject(it)
                HistoryEntry(
                    ts = o.optLong("ts"),
                    title = o.optString("title"),
                    stationName = o.optString("station"),
                    stationUuid = o.optString("uuid"),
                    favicon = o.optString("icon"),
                )
            }.filter { it.title.isNotBlank() }
        }
    } catch (_: Exception) {
        emptyList()
    }

    fun add(e: HistoryEntry) {
        val cur = items
        val last = cur.firstOrNull()
        if (last != null && last.title == e.title && last.stationUuid == e.stationUuid) return
        items = (listOf(e) + cur).take(MAX)
        save()
    }

    fun remove(e: HistoryEntry) {
        items = items.filter { !(it.ts == e.ts && it.title == e.title && it.stationUuid == e.stationUuid) }
        save()
    }

    fun clear() {
        items = emptyList()
        save()
    }

    private fun save() {
        val snapshot = items
        scope.launch(Dispatchers.IO) {
            try {
                val a = JSONArray()
                for (e in snapshot) {
                    a.put(
                        JSONObject()
                            .put("ts", e.ts).put("title", e.title).put("station", e.stationName)
                            .put("uuid", e.stationUuid).put("icon", e.favicon)
                    )
                }
                synchronized(lock) { file.writeText(a.toString()) }
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        const val MAX = 1000
    }
}
