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

        private fun folder(id: String, title: String): MediaItem =
            MediaItem.Builder()
                .setMediaId(id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_RADIO_STATIONS)
                        .build()
                )
                .build()

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
            Futures.immediateFuture(LibraryResult.ofItem(folder(ROOT_ID, "MLRadio"), params))

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

        private suspend fun children(parentId: String): List<MediaItem> {
            when {
                parentId == ROOT_ID -> return listOf(
                    folder(FAV_ID, "Preferiti"), folder(EXPLORE_ID, "Tutte le radio"), folder(CUSTOM_ID, "Le mie radio")
                )
                parentId == FAV_ID -> return stationItems(app.favorites.items)
                parentId == CUSTOM_ID -> return stationItems(app.custom.items)
                parentId == EXPLORE_ID -> {
                    // elenco degli stati A-Z; quello del telefono (es. Italia) in cima
                    val col = Collator.getInstance(Locale.ITALIAN)
                    val home = Locale.getDefault().country.uppercase(Locale.ROOT)
                    val all = countries().sortedWith { a, b -> col.compare(a.localName, b.localName) }
                    val first = all.filter { it.code == home }
                    return (first + all.filter { it.code != home })
                        .map { folder("c|${it.code}", "${it.flag} ${it.localName}") }
                }
                parentId.startsWith("c|") -> {
                    val code = parentId.substring(2)
                    val country = countries().firstOrNull { it.code == code } ?: return emptyList()
                    val out = ArrayList<MediaItem>()
                    out.add(folder("s|$code|", "Tutte le radio del paese"))
                    val regions = RadioApi.states(country.name)
                    if (regions.size > 1) regions.forEach { out.add(folder("s|$code|${it.name}", "${it.name} (${it.count})")) }
                    return out
                }
                parentId.startsWith("s|") -> {
                    val parts = parentId.split('|', limit = 3)
                    val list = RadioApi.search(
                        SearchFilter(countryCode = parts[1], state = parts.getOrElse(2) { "" }, order = "clickcount"),
                        0, 200
                    )
                    return stationItems(StationParser.sortOnlineFirst(StationParser.dedupe(list)))
                }
            }
            return emptyList()
        }

        /** Ultimo elenco mostrato in auto che contiene ogni radio: diventa la coda precedente/successiva. */
        private val stationLists = HashMap<String, List<Station>>()

        private fun stationItems(list: List<Station>): List<MediaItem> {
            app.directory.remember(list)
            for (st in list) stationLists[st.uuid] = list
            return list.map { StationItems.browseItem(it) }
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
        private const val CMD_FAV = "com.marco.mlradio.TOGGLE_FAVORITE"
        private const val EXPLORE_ID = "mlradio_explore"
        private const val ROOT_ID = "mlradio_root"
        private const val FAV_ID = "mlradio_favorites"
        private const val CUSTOM_ID = "mlradio_custom"
    }
}
