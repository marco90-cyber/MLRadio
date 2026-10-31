package com.marco.mlradio.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.marco.mlradio.MLRadioApp
import com.marco.mlradio.audio.PlayerEngine
import com.marco.mlradio.data.HistoryStore
import com.marco.mlradio.data.Settings
import com.marco.mlradio.data.Station
import com.marco.mlradio.data.StationStore
import com.marco.mlradio.feedback.Haptics

/** Stato condiviso dell'interfaccia (vive quanto l'app, quindi resta anche se l'Activity viene ricreata). */
class AppEnv(val app: MLRadioApp) {
    val settings: Settings get() = app.settings
    val engine: PlayerEngine get() = app.engine
    val feedback: Haptics get() = app.haptics
    val favorites: StationStore get() = app.favorites
    val custom: StationStore get() = app.custom
    val history: HistoryStore get() = app.history

    /** 0 Preferiti, 1 Esplora, 2 Cronologia, 3 Impostazioni */
    var tab by mutableIntStateOf(0)
    var showPlayer by mutableStateOf(false)
    var showEq by mutableStateOf(false)
    var showAbout by mutableStateOf(false)
    var addDialog by mutableStateOf(false)

    val explore = ExploreState(this)

    /** Curva calcolata dal motore nativo per l'equalizzatore. */
    val preview by lazy { com.marco.mlradio.audio.DspPreview() }

    fun play(st: Station, queue: List<Station>) {
        app.directory.remember(queue)
        engine.play(st, queue)
    }

    fun toggleFavorite(st: Station) {
        favorites.toggle(st)
    }
}
