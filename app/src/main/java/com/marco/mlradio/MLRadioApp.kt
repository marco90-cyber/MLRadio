package com.marco.mlradio

import android.app.Application
import com.marco.mlradio.audio.ConnectionResume
import com.marco.mlradio.audio.PlayerEngine
import com.marco.mlradio.data.HistoryStore
import com.marco.mlradio.data.RadioApi
import com.marco.mlradio.data.Settings
import com.marco.mlradio.data.StationDirectory
import com.marco.mlradio.data.StationStore
import com.marco.mlradio.feedback.Haptics
import com.marco.mlradio.ui.AppEnv
import com.marco.mlradio.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MLRadioApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    lateinit var settings: Settings
        private set
    lateinit var favorites: StationStore
        private set
    lateinit var custom: StationStore
        private set
    lateinit var history: HistoryStore
        private set
    lateinit var directory: StationDirectory
        private set
    lateinit var haptics: Haptics
        private set
    lateinit var engine: PlayerEngine
        private set

    /** Stato dell'interfaccia: sopravvive alla ricreazione dell'Activity. */
    val env: AppEnv by lazy { AppEnv(this) }

    override fun onCreate() {
        super.onCreate()
        AppLog.init(this)
        settings = Settings(this)
        AppLog.verbose = settings.verboseLog
        favorites = StationStore(this, "favorites")
        custom = StationStore(this, "custom")
        history = HistoryStore(this, appScope)
        directory = StationDirectory(favorites, custom)
        haptics = Haptics(this, settings)
        engine = PlayerEngine(this, settings, history, appScope)
        engine.connectService()
        ConnectionResume.register(this, engine, settings)

        appScope.launch { refreshFavorites() }
    }

    /**
     * Aggiorna i dati dei preferiti da Radio Browser (nuovo indirizzo del flusso, logo, stato).
     * Se non c'è rete restano i dati salvati.
     */
    suspend fun refreshFavorites() {
        val ids = favorites.items.filter { !it.custom }.map { it.uuid }
        if (ids.isEmpty()) return
        val fresh = try {
            RadioApi.byUuids(ids)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("Dati", "Aggiornamento dei preferiti non riuscito", e)
            return
        }
        if (fresh.isEmpty()) return
        val map = fresh.associateBy { it.uuid }
        favorites.replaceAll(favorites.items.map { old ->
            val n = map[old.uuid]
            if (n != null) n.copy(alts = old.alts) else old
        })
    }
}
