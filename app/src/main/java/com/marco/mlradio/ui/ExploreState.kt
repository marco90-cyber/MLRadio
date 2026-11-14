package com.marco.mlradio.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.marco.mlradio.data.Continent
import com.marco.mlradio.data.Continents
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

/** Stato della schermata Esplora: continente → paese → (regione, genere, ordine), ricerca per nome. */
class ExploreState(private val env: AppEnv) {

    var countries by mutableStateOf<List<Country>>(emptyList())
        private set
    var countriesLoading by mutableStateOf(false)
        private set
    var countriesError by mutableStateOf<String?>(null)
        private set

    /** Filtro dell'elenco dei paesi / testo della ricerca mondiale. */
    var countryQuery by mutableStateOf("")

    /** Continente aperto (elenco dei suoi paesi); null = pagina iniziale con i continenti. */
    var continent by mutableStateOf<Continent?>(null)
        private set

    var country by mutableStateOf<Country?>(null)
        private set
    var global by mutableStateOf(false)
        private set
    var customOpen by mutableStateOf(false)

    var regions by mutableStateOf<List<RegionInfo>>(emptyList())
        private set
    /** Elenco delle regioni in caricamento. */
    var regionsLoading by mutableStateOf(false)
        private set
    var region by mutableStateOf("")
        private set
    private var regionInfo: RegionInfo? = null
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
    val canGoBack: Boolean get() = inStations || customOpen || continent != null

    /** Paese del telefono (scorciatoia nella pagina iniziale). */
    val homeCountry: Country?
        get() {
            val here = Locale.getDefault().country.uppercase(Locale.ROOT)
            return countries.firstOrNull { it.code == here }
        }

    /** Continenti con almeno un paese: (continente, numero di paesi, numero di radio). */
    fun continentSummary(): List<Triple<Continent, Int, Int>> {
        val groups = countries.groupBy { it.continent }
        return Continents.all.mapNotNull { c ->
            val l = groups[c] ?: return@mapNotNull null
            Triple(c, l.size, l.sumOf { it.count })
        }
    }

    /** Paesi del continente aperto, in ordine alfabetico italiano. */
    fun countriesOf(c: Continent): List<Country> {
        val col = java.text.Collator.getInstance(Locale.ITALIAN)
        return countries.filter { it.continent == c }.sortedWith { a, b -> col.compare(a.localName, b.localName) }
    }

    fun openContinent(c: Continent) {
        AppLog.i("Esplora", "Apro il continente ${c.name}")
        continent = c
        countryQuery = ""
    }

    fun loadCountries(force: Boolean = false) {
        if (countriesLoading) return
        if (countries.isNotEmpty() && !force) return
        countriesLoading = true
        countriesError = null
        AppLog.i("Esplora", "Carico l'elenco dei paesi${if (force) " (riprova)" else ""}")
        env.app.appScope.launch {
            try {
                countries = RadioApi.countries()
                AppLog.i("Esplora", "Continenti: " + continentSummary().joinToString { "${it.first.name} ${it.second} paesi" })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("Esplora", "Elenco paesi non caricato", e)
                countriesError = "Impossibile caricare i paesi. Controlla la connessione."
            } finally {
                countriesLoading = false
            }
        }
    }

    fun openCountry(c: Country) {
        AppLog.i("Esplora", "Apro il paese ${c.localName} (${c.code}, ${c.count} radio, nomi server: ${c.names})")
        country = c
        if (continent == null) continent = c.continent
        global = false
        customOpen = false
        regions = emptyList()
        regionsLoading = true
        region = ""
        regionInfo = null
        tag = ""
        query = ""
        env.app.appScope.launch {
            val list = try {
                RadioApi.states(c)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Esplora", "Regioni di ${c.code} non caricate", e)
                emptyList()
            }
            if (country?.code == c.code) {
                if (list.size > regions.size) regions = list
                regionsLoading = false
            }
        }
        reload()
    }

    fun openGlobalSearch(text: String) {
        AppLog.i("Esplora", "Ricerca nel mondo: «${text.trim()}»")
        country = null
        global = true
        customOpen = false
        regions = emptyList()
        region = ""
        regionInfo = null
        tag = ""
        query = text.trim()
        reload()
    }

    fun back() {
        when {
            customOpen -> customOpen = false
            inStations -> {
                AppLog.i("Esplora", "Indietro: chiudo l'elenco delle radio")
                job?.cancel()
                gen++
                country = null
                global = false
                stations = emptyList()
                raw.clear()
                loading = false
                regionsLoading = false
                query = ""
                loadedQuery = ""
            }
            continent != null -> {
                AppLog.i("Esplora", "Indietro: torno ai continenti")
                continent = null
                countryQuery = ""
            }
        }
    }

    fun selectRegion(name: String) {
        AppLog.i("Esplora", "Filtro regione: ${name.ifEmpty { "tutto il paese" }}")
        region = name
        regionInfo = regions.firstOrNull { it.name == name }
        reload()
    }

    fun selectTag(t: String) {
        AppLog.i("Esplora", "Filtro genere: ${t.ifEmpty { "tutti" }}")
        tag = t
        reload()
    }

    fun selectOrder(o: String) {
        AppLog.i("Esplora", "Ordine: $o")
        order = o
        reload()
    }

    private fun filter() = SearchFilter(
        name = query,
        countryCode = country?.code.orEmpty(),
        state = if (regionInfo == null) region else "",
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
        AppLog.i("Esplora", "Cerco radio: $f, regione=${regionInfo?.let { "${it.name} (${it.variants.size} scritture)" } ?: "-"}, da $offset")
        job = env.app.appScope.launch {
            try {
                val ri = regionInfo
                val page = if (ri != null) RadioApi.searchRegion(f, ri) else RadioApi.search(f, offset, PAGE)
                if (g != gen) return@launch
                val known = raw.mapTo(HashSet()) { it.uuid }
                raw.addAll(page.filter { known.add(it.uuid) })
                offset += page.size
                hasMore = ri == null && page.size >= PAGE
                stations = StationParser.sortOnlineFirst(StationParser.dedupe(raw))
                AppLog.i(
                    "Esplora",
                    "Ricevute ${page.size} radio (totale ${raw.size}, mostrate ${stations.size}, " +
                        "${stations.count { !it.online }} non raggiungibili, altre pagine: $hasMore)"
                )
                env.app.directory.remember(stations)
                // se il server non ha fornito l'elenco delle regioni, lo ricavo dalle radio caricate
                if (country != null && regions.isEmpty() && region.isEmpty()) {
                    val cn = country?.name.orEmpty()
                    val derived = com.marco.mlradio.data.Regions.merge(
                        raw.filter { it.state.isNotBlank() }.groupingBy { it.state }.eachCount().toList(),
                        country?.code.orEmpty(), cn
                    )
                    if (derived.size > 1) {
                        AppLog.i("Esplora", "Regioni ricavate dalle radio caricate: ${derived.size}")
                        regions = derived
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e("Esplora", "Ricerca radio non riuscita", e)
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
