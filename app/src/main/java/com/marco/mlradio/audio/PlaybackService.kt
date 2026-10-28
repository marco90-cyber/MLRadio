package com.marco.mlradio.audio

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
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
        session = MediaLibrarySession.Builder(this, app.engine.player, LibraryCallback(app))
            .setSessionActivity(openApp)
            .build()
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
            val items: List<MediaItem> = when (parentId) {
                ROOT_ID -> listOf(folder(FAV_ID, "Preferiti"), folder(CUSTOM_ID, "Le mie radio"))
                FAV_ID -> stationItems(app.favorites.items)
                CUSTOM_ID -> stationItems(app.custom.items)
                else -> emptyList()
            }
            return Futures.immediateFuture(LibraryResult.ofItemList(items, params))
        }

        private fun stationItems(list: List<Station>): List<MediaItem> {
            app.directory.remember(list)
            return list.map { StationItems.browseItem(it) }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val item = when (mediaId) {
                ROOT_ID -> folder(ROOT_ID, "MLRadio")
                FAV_ID -> folder(FAV_ID, "Preferiti")
                CUSTOM_ID -> folder(CUSTOM_ID, "Le mie radio")
                else -> app.directory.find(mediaId)?.let { StationItems.browseItem(it) }
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
        private const val ROOT_ID = "mlradio_root"
        private const val FAV_ID = "mlradio_favorites"
        private const val CUSTOM_ID = "mlradio_custom"
    }
}
