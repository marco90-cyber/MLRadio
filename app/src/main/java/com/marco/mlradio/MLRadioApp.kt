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
        AppLog.init(this, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
        registerActivityLifecycleCallbacks(LifecycleLogger)
        settings = Settings(this)
        AppLog.verbose = settings.verboseLog
        com.marco.mlradio.data.Logos.init(this, appScope)
        favorites = StationStore(this, "favorites")
        custom = StationStore(this, "custom")
        history = HistoryStore(this, appScope)
        directory = StationDirectory(favorites, custom)
        haptics = Haptics(this, settings)
        engine = PlayerEngine(this, settings, history, appScope)
        engine.connectService()
        ConnectionResume.register(this, engine, settings)

        appScope.launch { refreshFavorites() }
        AppLog.i("App", "Avvio completato: ${favorites.items.size} preferiti, ${custom.items.size} radio personali")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        AppLog.i("App", "Sistema: richiesta di liberare memoria (livello $level)")
    }

    override fun onLowMemory() {
        super.onLowMemory()
        AppLog.w("App", "Sistema: memoria quasi esaurita")
    }

    /** Registra apertura/chiusura delle schermate (utile per capire cosa succedeva prima di un errore). */
    private object LifecycleLogger : android.app.Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(a: android.app.Activity, b: android.os.Bundle?) =
            AppLog.i("Ciclo", "${a.javaClass.simpleName} creata${if (b != null) " (ripristinata)" else ""}")
        override fun onActivityStarted(a: android.app.Activity) = AppLog.d("Ciclo", "${a.javaClass.simpleName} visibile")
        override fun onActivityResumed(a: android.app.Activity) = AppLog.i("Ciclo", "${a.javaClass.simpleName} in primo piano")
        override fun onActivityPaused(a: android.app.Activity) = AppLog.i("Ciclo", "${a.javaClass.simpleName} in pausa")
        override fun onActivityStopped(a: android.app.Activity) = AppLog.d("Ciclo", "${a.javaClass.simpleName} nascosta")
        override fun onActivitySaveInstanceState(a: android.app.Activity, b: android.os.Bundle) {}
        override fun onActivityDestroyed(a: android.app.Activity) = AppLog.i("Ciclo", "${a.javaClass.simpleName} chiusa")
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
