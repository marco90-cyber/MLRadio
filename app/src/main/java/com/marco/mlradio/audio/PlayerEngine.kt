package com.marco.mlradio.audio

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.metadata.icy.IcyHeaders
import androidx.media3.extractor.metadata.icy.IcyInfo
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.marco.mlradio.data.HistoryEntry
import com.marco.mlradio.data.HistoryStore
import com.marco.mlradio.data.RadioApi
import com.marco.mlradio.data.Settings
import com.marco.mlradio.data.Station
import com.marco.mlradio.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class EngineState { IDLE, CONNECTING, BUFFERING, PLAYING, PAUSED, ERROR }

/**
 * Motore della radio: ExoPlayer + equalizzatore DSP.
 * Condiviso dall'interfaccia e dal servizio in background (notifica, Android Auto).
 */
class PlayerEngine(
    private val app: Application,
    private val settings: Settings,
    private val history: HistoryStore,
    private val scope: CoroutineScope,
) {
    val eqProcessor = EqAudioProcessor()

    val player: ExoPlayer

    private val main = Handler(Looper.getMainLooper())

    var currentStation by mutableStateOf<Station?>(null)
        private set
    var state by mutableStateOf(EngineState.IDLE)
        private set

    /** Titolo del brano in onda, se la radio lo comunica (ICY / ID3). Vuoto = nessuna informazione. */
    var nowPlaying by mutableStateOf("")
        private set

    /** Dati reali del flusso aperto. 0 / vuoto = non noto. */
    var codecLabel by mutableStateOf("")
        private set
    var bitrateKbps by mutableIntStateOf(0)
        private set
    var sampleRate by mutableIntStateOf(0)
        private set
    var channels by mutableIntStateOf(0)
        private set
    var errorText by mutableStateOf<String?>(null)
        private set

    var volume by mutableIntStateOf(0)
        private set
    var maxVolume by mutableIntStateOf(15)
        private set
    var muted by mutableStateOf(false)
        private set

    /** Radio fra cui spostarsi con precedente/successiva (l'elenco da cui si è avviata la radio). */
    var queue by mutableStateOf<List<Station>>(emptyList())
        private set

    /** True se l'ultima pausa è dovuta a cuffie/Bluetooth scollegati: serve per riprendere alla riconnessione. */
    @Volatile
    var pausedByNoisy = false

    /** True quando l'utente vuole ascoltare (in riproduzione, in connessione o in caricamento). */
    val wantsPlay: Boolean
        get() = state == EngineState.PLAYING || state == EngineState.BUFFERING || state == EngineState.CONNECTING

    private var resolving = false
    private var connectedOnce = false
    private var resolveJob: Job? = null
    private var urls: List<String> = emptyList()
    private var urlIndex = 0
    private var attempts = 0
    private var pauseStartedAt = 0L
    private var lastRecorded = ""
    private var icyBitrate = 0
    private var controllerFuture: Any? = null

    init {
        val factory = object : DefaultRenderersFactory(app) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink {
                val inner = DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(true)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
                return ProcessingAudioSink(inner, eqProcessor)
            }
        }
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(RadioApi.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(20_000)
        val mediaSources = DefaultMediaSourceFactory(DefaultDataSource.Factory(app, http))

        player = ExoPlayer.Builder(app, factory)
            .setMediaSourceFactory(mediaSources)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true // pausa durante chiamate e altri audio
            )
            .setHandleAudioBecomingNoisy(true) // pausa se si staccano le cuffie
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        eqProcessor.setConfig(settings.eq)
        settings.onEqChanged = { cfg -> eqProcessor.setConfig(cfg) }

        syncVolume()
        val volFilter = android.content.IntentFilter().apply {
            addAction("android.media.VOLUME_CHANGED_ACTION")
            addAction("android.media.STREAM_MUTE_CHANGED_ACTION")
        }
        androidx.core.content.ContextCompat.registerReceiver(
            app, object : android.content.BroadcastReceiver() {
                override fun onReceive(c: android.content.Context?, i: android.content.Intent?) = syncVolume()
            }, volFilter, androidx.core.content.ContextCompat.RECEIVER_EXPORTED
        )

        // Ultima radio ascoltata: pronta nel mini player, senza partire da sola
        Station.fromJsonString(settings.lastStationJson)?.let { currentStation = it }

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    connectedOnce = true
                    attempts = 0
                }
                refreshState()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady) {
                    pauseStartedAt = SystemClock.elapsedRealtime()
                    if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY) pausedByNoisy = true
                } else {
                    pausedByNoisy = false
                    val paused = pauseStartedAt
                    pauseStartedAt = 0L
                    // dopo una pausa lunga il flusso dal vivo è vecchio: si riparte dalla diretta
                    if (paused > 0 && SystemClock.elapsedRealtime() - paused > STALE_MS) {
                        main.post { currentStation?.let { startStation(it) } }
                    }
                }
                refreshState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                refreshState()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                adoptExternalItem(mediaItem)
                refreshState()
            }

            override fun onTracksChanged(tracks: Tracks) {
                for (g in tracks.groups) {
                    if (g.type == C.TRACK_TYPE_AUDIO && g.isSelected && g.length > 0) {
                        val f = g.getTrackFormat(0)
                        codecLabel = codecName(f.sampleMimeType, f.codecs)
                        val br = if (f.bitrate > 0) f.bitrate / 1000 else 0
                        if (br > 0) bitrateKbps = br
                        sampleRate = if (f.sampleRate > 0) f.sampleRate else 0
                        channels = if (f.channelCount > 0) f.channelCount else 0
                        AppLog.i(
                            "Audio",
                            "Flusso: ${f.sampleMimeType} ${f.codecs ?: ""} ${f.sampleRate} Hz, ${f.channelCount} ch, bitrate=${f.bitrate}"
                        )
                        break
                    }
                }
            }

            override fun onMetadata(metadata: Metadata) {
                var title: String? = null
                var artist: String? = null
                for (i in 0 until metadata.length()) {
                    when (val e = metadata.get(i)) {
                        is IcyInfo -> title = e.title
                        is IcyHeaders -> {
                            if (e.bitrate > 0) {
                                icyBitrate = e.bitrate / 1000
                                if (bitrateKbps <= 0) bitrateKbps = icyBitrate
                            }
                        }
                        is TextInformationFrame -> when (e.id) {
                            "TIT2" -> title = e.values.firstOrNull()
                            "TPE1" -> artist = e.values.firstOrNull()
                        }
                        else -> {}
                    }
                }
                if (title != null) {
                    val full = if (!artist.isNullOrBlank() && !title.contains(" - ")) "$artist - $title" else title
                    onSongTitle(full)
                }
            }

            override fun onDeviceVolumeChanged(v: Int, isMuted: Boolean) {
                syncVolume()
            }

            override fun onPlayerError(error: PlaybackException) {
                AppLog.e("Audio", "Errore di riproduzione: ${error.errorCodeName}", error)
                handleError(error)
            }
        })
        refreshState()
        AppLog.i("Audio", "Motore radio pronto")
    }

    // ------------------------------------------------------------------ stato

    private fun refreshState() {
        state = when {
            errorText != null -> EngineState.ERROR
            currentStation == null -> EngineState.IDLE
            resolving -> EngineState.CONNECTING
            else -> when (player.playbackState) {
                Player.STATE_BUFFERING ->
                    if (player.playWhenReady) (if (connectedOnce) EngineState.BUFFERING else EngineState.CONNECTING)
                    else EngineState.PAUSED
                Player.STATE_READY -> if (player.playWhenReady) EngineState.PLAYING else EngineState.PAUSED
                Player.STATE_IDLE ->
                    if (player.playWhenReady && player.mediaItemCount > 0) EngineState.CONNECTING else EngineState.PAUSED
                else -> EngineState.PAUSED
            }
        }
    }

    private fun resetStreamInfo() {
        nowPlaying = ""
        codecLabel = ""
        bitrateKbps = 0
        sampleRate = 0
        channels = 0
        icyBitrate = 0
        lastRecorded = ""
        connectedOnce = false
    }

    private fun codecName(mime: String?, codecs: String?): String {
        val c = codecs.orEmpty()
        return when {
            mime == MimeTypes.AUDIO_AAC || c.startsWith("mp4a.40") ->
                if (c.contains("mp4a.40.5") || c.contains("mp4a.40.29")) "HE-AAC" else "AAC"
            mime == MimeTypes.AUDIO_MPEG -> "MP3"
            mime == MimeTypes.AUDIO_MPEG_L2 -> "MP2"
            mime == MimeTypes.AUDIO_OPUS -> "Opus"
            mime == MimeTypes.AUDIO_VORBIS -> "Vorbis"
            mime == MimeTypes.AUDIO_FLAC -> "FLAC"
            !mime.isNullOrBlank() -> mime.substringAfter('/').uppercase()
            else -> ""
        }
    }

    // ------------------------------------------------------------------ titolo del brano e cronologia

    private fun onSongTitle(raw: String) {
        val t = raw.replace(Regex("\\s+"), " ").trim()
        val st = currentStation
        if (t.isBlank() || t == "-" || (st != null && t.equals(st.name, ignoreCase = true))) {
            nowPlaying = ""
            return
        }
        nowPlaying = t
        if (t.length >= 3 && t != lastRecorded && st != null) {
            lastRecorded = t
            history.add(
                HistoryEntry(
                    ts = System.currentTimeMillis(),
                    title = t,
                    stationName = st.name,
                    stationUuid = st.uuid,
                    favicon = st.favicon,
                )
            )
        }
    }

    // ------------------------------------------------------------------ avvio e ripresa

    /** Avvia una radio. [queue] è l'elenco da cui proviene (per precedente/successiva). */
    fun play(station: Station, queue: List<Station> = listOf(station)) {
        this.queue = queue
        startStation(station)
    }

    private fun startStation(st: Station) {
        resolveJob?.cancel()
        main.removeCallbacksAndMessages(null)
        currentStation = st
        settings.lastStationJson = st.toJson().toString()
        errorText = null
        attempts = 0
        pauseStartedAt = 0L
        resetStreamInfo()
        resolving = true
        refreshState()
        resolveJob = scope.launch {
            val url = try {
                RadioApi.resolveUrl(st)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                st.url
            }
            resolving = false
            urls = (listOf(url, st.url) + st.alts).filter { it.isNotBlank() }.distinct()
            urlIndex = 0
            if (urls.isEmpty()) {
                errorText = "Questa radio non ha un indirizzo valido."
                refreshState()
            } else {
                setItem(st, urls[0])
            }
        }
    }

    private fun setItem(st: Station, url: String) {
        player.setMediaItem(StationItems.playItem(st, url))
        player.prepare()
        player.play()
        refreshState()
    }

    /** Se la riproduzione è partita da fuori (es. Android Auto), si allinea lo stato dell'app. */
    private fun adoptExternalItem(item: MediaItem?) {
        val st = StationItems.stationFrom(item) ?: return
        val uri = item?.localConfiguration?.uri?.toString()
        val sameStation = st.uuid == currentStation?.uuid
        val sameUrl = uri != null && uri == urls.getOrNull(urlIndex)
        if (sameStation && sameUrl) return
        if (sameStation && uri != null && uri in urls) return
        currentStation = st
        settings.lastStationJson = st.toJson().toString()
        urls = (listOfNotNull(uri) + st.alts).distinct()
        urlIndex = 0
        errorText = null
        attempts = 0
        resetStreamInfo()
        if (queue.none { it.uuid == st.uuid }) queue = emptyList()
    }

    private fun handleError(error: PlaybackException) {
        val st = currentStation ?: return
        // 1) un altro indirizzo della stessa radio
        if (urlIndex + 1 < urls.size) {
            urlIndex++
            setItem(st, urls[urlIndex])
            return
        }
        // 2) nuovi tentativi, ripartendo dalla ricerca dell'indirizzo aggiornato
        attempts++
        if (attempts <= MAX_RETRIES) {
            val keep = attempts
            main.postDelayed({
                if (currentStation?.uuid == st.uuid) {
                    startStation(st)
                    attempts = keep
                }
            }, 2000)
            return
        }
        errorText = explain(error)
        refreshState()
    }

    private fun explain(e: PlaybackException): String = when (e.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "Connessione non riuscita. Controlla la rete."
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
            "La radio non risponde (errore del server)."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
            "Formato audio non supportato."
        else -> "Impossibile riprodurre questa radio."
    }

    fun resume() {
        val st = currentStation ?: return
        if (errorText != null || player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED
        ) {
            startStation(st)
        } else {
            player.play()
        }
    }

    fun togglePlayPause() {
        if (resolving) {
            resolveJob?.cancel()
            resolving = false
            player.pause()
            refreshState()
            return
        }
        if (wantsPlay) player.pause() else resume()
    }

    /** Riprende dopo la riconnessione di cuffie/Bluetooth, solo se la pausa era dovuta allo scollegamento. */
    fun resumeAfterReconnect() {
        if (!pausedByNoisy) return
        pausedByNoisy = false
        main.postDelayed({ if (!wantsPlay) resume() }, 1500)
    }

    /** Elenco scelto da Android Auto: serve per passare alla radio precedente/successiva anche in auto. */
    fun setQueueFromAuto(list: List<Station>) {
        queue = list
    }

    fun next() = step(+1)

    fun previous() = step(-1)

    private fun step(delta: Int) {
        val q = queue
        if (q.size < 2) return
        val i = q.indexOfFirst { it.uuid == currentStation?.uuid }
        if (i < 0) return
        play(q[(i + delta + q.size) % q.size], q)
    }

    // ------------------------------------------------------------------ volume (volume di sistema)

    // getter (senza campo): usabile già dal blocco init
    private val audioManager: android.media.AudioManager
        get() = app.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager

    private fun syncVolume() {
        val am = audioManager
        maxVolume = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        volume = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        muted = am.isStreamMute(android.media.AudioManager.STREAM_MUSIC)
    }

    fun changeVolume(v: Int) {
        val n = v.coerceIn(0, maxVolume)
        audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, n, 0)
        if (n > 0 && audioManager.isStreamMute(android.media.AudioManager.STREAM_MUSIC)) {
            audioManager.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_UNMUTE, 0)
        }
        syncVolume()
    }

    fun toggleMute() {
        val m = audioManager.isStreamMute(android.media.AudioManager.STREAM_MUSIC)
        audioManager.adjustStreamVolume(
            android.media.AudioManager.STREAM_MUSIC,
            if (m) android.media.AudioManager.ADJUST_UNMUTE else android.media.AudioManager.ADJUST_MUTE, 0
        )
        syncVolume()
    }

    /** Collega il servizio in background (notifica, controlli multimediali, Android Auto). */
    fun connectService() {
        try {
            val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
            controllerFuture = MediaController.Builder(app, token).buildAsync()
        } catch (e: Throwable) {
            AppLog.e("Audio", "Servizio di riproduzione non collegato", e)
        }
    }

    companion object {
        private const val STALE_MS = 20_000L
        private const val MAX_RETRIES = 2
    }
}
