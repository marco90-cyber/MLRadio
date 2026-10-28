package com.marco.mlradio.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.marco.mlradio.data.Country
import com.marco.mlradio.data.RadioApi
import com.marco.mlradio.data.RegionInfo
import com.marco.mlradio.data.SearchFilter
import com.marco.mlradio.data.Station
import com.marco.mlradio.data.StationParser
import com.marco.mlradio.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

/** Generi proposti nel filtro: (etichetta, tag di Radio Browser). */
val Genres = listOf(
    "Pop" to "pop",
    "Rock" to "rock",
    "Notizie" to "news",
    "Talk" to "talk",
    "Sport" to "sport",
    "Hit" to "hits",
    "Dance" to "dance",
    "Elettronica" to "electronic",
    "Hip hop" to "hip hop",
    "Jazz" to "jazz",
    "Classica" to "classical",
    "Country" to "country",
    "Folk" to "folk",
    "Reggae" to "reggae",
    "Latina" to "latin",
    "Religiosa" to "religious",
    "Anni 80" to "80s",
    "Anni 90" to "90s",
)

val Orders = listOf(
    "Più votate" to "votes",
    "Più ascoltate" to "clickcount",
    "Nome (A-Z)" to "name",
)

/** Stato della schermata Esplora: paesi → regione → genere → ordine, ricerca per nome. */
class ExploreState(private val env: AppEnv) {

    var countries by mutableStateOf<List<Country>>(emptyList())
        private set
    var countriesLoading by mutableStateOf(false)
        private set
    var countriesError by mutableStateOf<String?>(null)
        private set

    /** Filtro dell'elenco dei paesi / testo della ricerca mondiale. */
    var countryQuery by mutableStateOf("")

    var country by mutableStateOf<Country?>(null)
        private set
    var global by mutableStateOf(false)
        private set
    var customOpen by mutableStateOf(false)

    var regions by mutableStateOf<List<RegionInfo>>(emptyList())
        private set
    var region by mutableStateOf("")
        private set
    var tag by mutableStateOf("")
        private set
    var order by mutableStateOf("votes")
        private set

    /** Ricerca per nome nell'elenco di radio aperto. */
    var query by mutableStateOf("")

    var stations by mutableStateOf<List<Station>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var hasMore by mutableStateOf(false)
        private set

    /** Ultimo testo con cui è stato caricato l'elenco (evita ricaricamenti inutili). */
    var loadedQuery = ""
        private set

    private val raw = ArrayList<Station>()
    private var offset = 0
    private var gen = 0
    private var job: Job? = null

    val inStations: Boolean get() = country != null || global
    val canGoBack: Boolean get() = inStations || customOpen

    fun loadCountries(force: Boolean = false) {
        if (countriesLoading) return
        if (countries.isNotEmpty() && !force) return
        countriesLoading = true
        countriesError = null
        env.app.appScope.launch {
            try {
                countries = RadioApi.countries()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Esplora", "Elenco paesi non caricato", e)
                countriesError = "Impossibile caricare i paesi. Controlla la connessione."
            } finally {
                countriesLoading = false
            }
        }
    }

    fun openCountry(c: Country) {
        country = c
        global = false
        customOpen = false
        regions = emptyList()
        region = ""
        tag = ""
        query = ""
        env.app.appScope.launch {
            val list = try {
                RadioApi.states(c.name)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            if (country?.code == c.code && regions.isEmpty()) regions = list
        }
        reload()
    }

    fun openGlobalSearch(text: String) {
        country = null
        global = true
        customOpen = false
        regions = emptyList()
        region = ""
        tag = ""
        query = text.trim()
        reload()
    }

    fun back() {
        when {
            customOpen -> customOpen = false
            inStations -> {
                job?.cancel()
                gen++
                country = null
                global = false
                stations = emptyList()
                raw.clear()
                loading = false
                query = ""
                loadedQuery = ""
            }
        }
    }

    fun selectRegion(name: String) {
        region = name
        reload()
    }

    fun selectTag(t: String) {
        tag = t
        reload()
    }

    fun selectOrder(o: String) {
        order = o
        reload()
    }

    private fun filter() = SearchFilter(
        name = query,
        countryCode = country?.code.orEmpty(),
        state = region,
        tag = tag,
        order = order,
    )

    fun reload() {
        job?.cancel()
        gen++
        raw.clear()
        offset = 0
        hasMore = false
        stations = emptyList()
        loading = false
        error = null
        loadedQuery = query
        loadMore()
    }

    fun loadMore() {
        if (loading || !inStations) return
        if (offset > 0 && !hasMore) return
        val g = gen
        val f = filter()
        loading = true
        error = null
        job = env.app.appScope.launch {
            try {
                val page = RadioApi.search(f, offset, PAGE)
                if (g != gen) return@launch
                val known = raw.mapTo(HashSet()) { it.uuid }
                raw.addAll(page.filter { known.add(it.uuid) })
                offset += page.size
                hasMore = page.size >= PAGE
                stations = StationParser.sortOnlineFirst(StationParser.dedupe(raw))
                env.app.directory.remember(stations)
                // se il server non ha fornito l'elenco delle regioni, lo ricavo dalle radio caricate
                if (country != null && regions.isEmpty() && region.isEmpty()) {
                    val derived = raw.filter { it.state.isNotBlank() }
                        .groupingBy { it.state }.eachCount()
                        .map { RegionInfo(it.key, it.value) }
                        .sortedBy { it.name.lowercase(Locale.ROOT) }
                    if (derived.size > 1) regions = derived
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Esplora", "Ricerca radio non riuscita", e)
                if (g == gen) error = "Impossibile caricare le radio. Controlla la connessione."
            } finally {
                if (g == gen) loading = false
            }
        }
    }

    companion object {
        const val PAGE = 300
    }
}
