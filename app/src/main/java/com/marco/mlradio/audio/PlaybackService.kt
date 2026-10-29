package com.marco.mlradio.audio

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import android.os.Bundle
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionCommands
import androidx.compose.runtime.snapshotFlow
import com.marco.mlradio.R
import com.marco.mlradio.data.Country
import com.marco.mlradio.data.StationParser
import kotlinx.coroutines.flow.distinctUntilChanged
import java.text.Collator
import java.util.Locale
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.marco.mlradio.MLRadioApp
import com.marco.mlradio.MainActivity
import com.marco.mlradio.data.RadioApi
import com.marco.mlradio.data.SearchFilter
import com.marco.mlradio.data.Station
import com.marco.mlradio.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Servizio in background: notifica con controlli, riproduzione a schermo spento e catalogo per
 * Android Auto (Preferiti e Le mie radio, più la ricerca vocale).
 */
class PlaybackService : MediaLibraryService() {

    private var session: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as MLRadioApp
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaLibrarySession.Builder(this, RadioPlayer(app), LibraryCallback(app))
            .setSessionActivity(openApp)
            .build()
        // il tasto «Metti nei preferiti» cambia aspetto a seconda della radio in ascolto
        app.appScope.launch {
            snapshotFlow {
                val st = app.engine.currentStation
                st?.uuid to (st != null && app.favorites.items.any { it.uuid == st.uuid })
            }.distinctUntilChanged().collect { (id, fav) ->
                session?.setCustomLayout(if (id == null) emptyList() else listOf(favButton(fav)))
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }

    /**
     * Il player visto da notifica e Android Auto: «precedente/successiva» passano alla radio
     * precedente/successiva dell'elenco da cui si è partiti (preferiti, paese, regione...).
     */
    private class RadioPlayer(private val app: MLRadioApp) : ForwardingPlayer(app.engine.player) {
        private val extra = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_PLAY_PAUSE,
            ).build()

        override fun getAvailableCommands(): Player.Commands =
            super.getAvailableCommands().buildUpon().addAll(extra).build()

        override fun isCommandAvailable(command: Int): Boolean =
            extra.contains(command) || super.isCommandAvailable(command)

        override fun hasNextMediaItem(): Boolean = app.engine.queue.size > 1
        override fun hasPreviousMediaItem(): Boolean = app.engine.queue.size > 1

        override fun seekToNext() = app.engine.next()
        override fun seekToNextMediaItem() = app.engine.next()
        override fun seekToPrevious() = app.engine.previous()
        override fun seekToPreviousMediaItem() = app.engine.previous()
    }

    private class LibraryCallback(private val app: MLRadioApp) : MediaLibrarySession.Callback {

        private fun icon(res: Int): android.net.Uri =
            android.net.Uri.parse("android.resource://${app.packageName}/$res")

        /**
         * Cartella per Android Auto. [listStyle] = true: le radio dentro sono mostrate in elenco
         * (elenchi lunghi), altrimenti a griglia con i loghi grandi (più facili da toccare alla guida).
         */
        private fun folder(
            id: String,
            title: String,
            iconRes: Int? = null,
            listStyle: Boolean = false,
            subtitle: String? = null,
            group: String? = null,
        ): MediaItem {
            val extras = Bundle().apply {
                putInt(KEY_PLAYABLE_STYLE, if (listStyle) STYLE_LIST else STYLE_GRID)
                putInt(KEY_BROWSABLE_STYLE, STYLE_LIST)
                if (group != null) putString(KEY_GROUP, group)
            }
            val meta = MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                .setExtras(extras)
            if (subtitle != null) meta.setSubtitle(subtitle)
            if (iconRes != null) meta.setArtworkUri(icon(iconRes))
            return MediaItem.Builder().setMediaId(id).setMediaMetadata(meta.build()).build()
        }

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val cmds = SessionCommands.Builder()
                .addSessionCommands(MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.commands)
                .add(SessionCommand(CMD_FAV, Bundle.EMPTY))
                .build()
            val st = app.engine.currentStation
            val layout = if (st == null) emptyList()
            else listOf(favButton(app.favorites.items.any { it.uuid == st.uuid }))
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(cmds)
                .setCustomLayout(layout)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == CMD_FAV) {
                app.engine.currentStation?.let { app.favorites.toggle(it) }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(
                LibraryResult.ofItem(
                    folder(ROOT_ID, "MLRadio"),
                    LibraryParams.Builder()
                        .setExtras(Bundle().apply {
                            putBoolean(KEY_STYLE_SUPPORTED, true)
                            putInt(KEY_BROWSABLE_STYLE, STYLE_LIST)
                            putInt(KEY_PLAYABLE_STYLE, STYLE_GRID)
                        })
                        .build()
                )
            )

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val future = SettableFuture.create<LibraryResult<ImmutableList<MediaItem>>>()
            app.appScope.launch {
                try {
                    val all: List<MediaItem> = children(parentId)
                    val from = (page * pageSize).coerceAtMost(all.size)
                    val slice = if (pageSize > 0) all.drop(from).take(pageSize) else all
                    future.set(LibraryResult.ofItemList(slice, params))
                } catch (e: CancellationException) {
                    future.cancel(false)
                    throw e
                } catch (e: Exception) {
                    AppLog.w("Auto", "Elenco non disponibile: $parentId", e)
                    future.set(LibraryResult.ofItemList(emptyList<MediaItem>(), params))
                }
            }
            return future
        }

        private var countriesCache: List<Country>? = null

        private suspend fun countries(): List<Country> =
            countriesCache ?: RadioApi.countries().filter { it.count > 0 }.also { countriesCache = it }

        private val regionCache = HashMap<String, List<com.marco.mlradio.data.RegionInfo>>()

        private suspend fun regionsOf(code: String): List<com.marco.mlradio.data.RegionInfo> {
            regionCache[code]?.let { return it }
            val country = countries().firstOrNull { it.code == code } ?: return emptyList()
            return RadioApi.states(country.name, code).also { if (it.isNotEmpty()) regionCache[code] = it }
        }

        private suspend fun children(parentId: String): List<MediaItem> {
            when {
                // le voci della radice diventano le schede in alto di Android Auto
                parentId == ROOT_ID -> return listOf(
                    folder(FAV_ID, "Preferiti", R.drawable.ic_fav_on),
                    folder(EXPLORE_ID, "Tutte le radio", R.drawable.ic_auto_globe, listStyle = true),
                    folder(CUSTOM_ID, "Le mie radio", R.drawable.ic_auto_radio),
                )
                parentId == FAV_ID -> return stationItems(app.favorites.items, hideOffline = false)
                parentId == CUSTOM_ID -> return stationItems(app.custom.items, hideOffline = false)
                parentId == EXPLORE_ID -> {
                    // elenco degli stati A-Z; quello del telefono (es. Italia) in cima
                    val col = Collator.getInstance(Locale.ITALIAN)
                    val home = Locale.getDefault().country.uppercase(Locale.ROOT)
                    val all = countries().sortedWith { a, b -> col.compare(a.localName, b.localName) }
                    val first = all.filter { it.code == home }
                    return (first + all.filter { it.code != home })
                        .map { folder("c|${it.code}", "${it.flag}  ${it.localName}", listStyle = true, subtitle = "${it.count} radio") }
                }
                parentId.startsWith("c|") -> {
                    // in cima: più ascoltate, più votate, genere, regione; sotto l'elenco completo A-Z
                    val code = parentId.substring(2)
                    val pick = "Scegli"
                    val out = arrayListOf(
                        folder("o|$code|clickcount", "Più ascoltate", R.drawable.ic_auto_trending, group = pick),
                        folder("o|$code|votes", "Più votate", R.drawable.ic_auto_star, group = pick),
                        folder("g|$code", "Genere", R.drawable.ic_auto_genre, group = pick),
                        folder("r|$code", "Regione", R.drawable.ic_auto_place, listStyle = true, group = pick),
                    )
                    val all = RadioApi.search(SearchFilter(countryCode = code, order = "name"), 0, 500)
                    out += stationItems(StationParser.dedupe(all), group = "Tutte le radio (A-Z)")
                    return out
                }
                parentId.startsWith("o|") -> {
                    val (_, code, order) = parentId.split('|', limit = 3)
                    val list = RadioApi.search(SearchFilter(countryCode = code, order = order), 0, 200)
                    return stationItems(StationParser.sortOnlineFirst(StationParser.dedupe(list)))
                }
                parentId.startsWith("g|") -> {
                    val code = parentId.substring(2)
                    return com.marco.mlradio.ui.Genres.map { (label, tag) -> folder("t|$code|$tag", label, R.drawable.ic_auto_genre) }
                }
                parentId.startsWith("t|") -> {
                    val (_, code, tag) = parentId.split('|', limit = 3)
                    val list = RadioApi.search(SearchFilter(countryCode = code, tag = tag, order = "clickcount"), 0, 200)
                    return stationItems(StationParser.sortOnlineFirst(StationParser.dedupe(list)))
                }
                parentId.startsWith("r|") -> {
                    val code = parentId.substring(2)
                    val regions = regionsOf(code)
                    return regions.map { folder("rr|$code|${it.name}", it.name, R.drawable.ic_auto_place, subtitle = "${it.count} radio") }
                }
                parentId.startsWith("rr|") -> {
                    val (_, code, name) = parentId.split('|', limit = 3)
                    val ri = regionsOf(code).firstOrNull { it.name == name } ?: return emptyList()
                    val list = RadioApi.searchRegion(SearchFilter(countryCode = code, order = "clickcount"), ri, 200)
                    return stationItems(StationParser.sortOnlineFirst(StationParser.dedupe(list)))
                }
            }
            return emptyList()
        }

        /** Ultimo elenco mostrato in auto che contiene ogni radio: diventa la coda precedente/successiva. */
        private val stationLists = HashMap<String, List<Station>>()

        /**
         * Radio per Android Auto. Alla guida le radio non raggiungibili si nascondono (tranne preferiti
         * e radio personali), così ogni tocco fa partire qualcosa.
         */
        private fun stationItems(all: List<Station>, hideOffline: Boolean = true, group: String? = null): List<MediaItem> {
            val list = if (hideOffline) all.filter { it.online } else all
            app.directory.remember(list)
            for (st in list) stationLists[st.uuid] = list
            return list.map { st ->
                val item = StationItems.browseItem(st)
                if (group == null) item
                else {
                    val ex = Bundle(item.mediaMetadata.extras ?: Bundle()).apply { putString(KEY_GROUP, group) }
                    item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(ex).build()).build()
                }
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item = when (mediaId) {
                ROOT_ID -> folder(ROOT_ID, "MLRadio")
                EXPLORE_ID -> folder(EXPLORE_ID, "Tutte le radio")
                FAV_ID -> folder(FAV_ID, "Preferiti")
                CUSTOM_ID -> folder(CUSTOM_ID, "Le mie radio")
                else -> if (mediaId.contains('|')) folder(mediaId, mediaId.substringAfterLast('|').ifBlank { "Radio" })
                else app.directory.find(mediaId)?.let { StationItems.browseItem(it) }
            }
            return if (item != null) Futures.immediateFuture(LibraryResult.ofItem(item, null))
            else Futures.immediateFuture(LibraryResult.ofError<MediaItem>(LibraryResult.RESULT_ERROR_BAD_VALUE))
        }

        /** Trasforma le voci scelte (anche da Android Auto o a voce) in flussi riproducibili con l'indirizzo aggiornato. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val future = SettableFuture.create<MutableList<MediaItem>>()
            app.appScope.launch {
                try {
                    val out = ArrayList<MediaItem>()
                    for (item in mediaItems) {
                        val st = findStation(item)
                        if (st != null) {
                            out.add(StationItems.playItem(st, RadioApi.resolveUrl(st)))
                        } else if (item.localConfiguration != null) {
                            out.add(item)
                        }
                    }
                    if (mediaItems.size == 1) {
                        val first = out.firstOrNull()?.let { StationItems.stationFrom(it) }
                        if (first != null) app.engine.setQueueFromAuto(stationLists[first.uuid] ?: listOf(first))
                    }
                    future.set(out)
                } catch (e: CancellationException) {
                    future.cancel(false)
                    throw e
                } catch (e: Exception) {
                    AppLog.w("Auto", "Impossibile preparare la radio richiesta", e)
                    future.setException(e)
                }
            }
            return future
        }

        private suspend fun findStation(item: MediaItem): Station? {
            if (item.mediaId.isNotBlank()) app.directory.find(item.mediaId)?.let { return it }
            StationItems.stationFrom(item)?.let { return it }
            val q = item.requestMetadata.searchQuery
            if (!q.isNullOrBlank()) {
                // ricerca vocale: "riproduci Radio Deejay"
                val found = RadioApi.search(SearchFilter(name = q), 0, 20)
                app.directory.remember(found)
                return found.firstOrNull { it.online } ?: found.firstOrNull()
            }
            return null
        }
    }

    companion object {
        fun favButton(isFav: Boolean): CommandButton =
            CommandButton.Builder()
                .setDisplayName(if (isFav) "Togli dai preferiti" else "Metti nei preferiti")
                .setIconResId(if (isFav) R.drawable.ic_fav_on else R.drawable.ic_fav_off)
                .setSessionCommand(SessionCommand(CMD_FAV, Bundle.EMPTY))
                .build()
        // suggerimenti di impaginazione per Android Auto (androidx.media.utils.MediaConstants)
        private const val KEY_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
        private const val KEY_BROWSABLE_STYLE = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        private const val KEY_PLAYABLE_STYLE = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        private const val KEY_GROUP = "android.media.browse.CONTENT_STYLE_GROUP_TITLE_HINT"
        private const val STYLE_LIST = 1
        private const val STYLE_GRID = 2
        private const val CMD_FAV = "com.marco.mlradio.TOGGLE_FAVORITE"
        private const val EXPLORE_ID = "mlradio_explore"
        private const val ROOT_ID = "mlradio_root"
        private const val FAV_ID = "mlradio_favorites"
        private const val CUSTOM_ID = "mlradio_custom"
    }
}
