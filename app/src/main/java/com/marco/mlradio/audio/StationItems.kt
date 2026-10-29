package com.marco.mlradio.audio

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.marco.mlradio.data.Station

/** Conversione fra radio e MediaItem di Media3 (notifica, Android Auto). */
object StationItems {
    const val EXTRA_STATION = "mlradio_station"

    private fun extras(st: Station) = Bundle().apply { putString(EXTRA_STATION, st.toJson().toString()) }

    private fun artwork(st: Station): Uri? =
        if (st.favicon.startsWith("http")) Uri.parse(st.favicon) else null

    /** Voce da mostrare negli elenchi di Android Auto (senza indirizzo: lo si ricava al momento del play). */
    fun browseItem(st: Station): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(st.name)
            .setArtist(st.place().ifBlank { "In diretta" })
            // sottotitolo breve, leggibile a colpo d'occhio: regione · genere
            .setSubtitle(
                listOfNotNull(
                    st.state.takeIf { it.isNotBlank() } ?: st.countryLocalName().takeIf { it.isNotBlank() },
                    st.genres(1).firstOrNull()?.replaceFirstChar { it.uppercase() },
                ).joinToString(" · ").ifBlank { "In diretta" }
            )
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
            .setExtras(extras(st))
        artwork(st)?.let { meta.setArtworkUri(it) }
        return MediaItem.Builder().setMediaId(st.uuid).setMediaMetadata(meta.build()).build()
    }

    /**
     * Voce riproducibile. Titolo "In diretta" e artista = nome della radio: quando la radio invia il titolo
     * del brano, Media3 lo sostituisce al titolo e la notifica mostra "brano / nome radio".
     */
    fun playItem(st: Station, url: String): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle("In diretta")
            .setArtist(st.name)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_RADIO_STATION)
            .setExtras(extras(st))
        artwork(st)?.let { meta.setArtworkUri(it) }
        val b = MediaItem.Builder().setMediaId(st.uuid).setUri(url).setMediaMetadata(meta.build())
        if (st.hls || url.contains(".m3u8", ignoreCase = true)) b.setMimeType(MimeTypes.APPLICATION_M3U8)
        return b.build()
    }

    fun stationFrom(item: MediaItem?): Station? =
        Station.fromJsonString(item?.mediaMetadata?.extras?.getString(EXTRA_STATION))
}
