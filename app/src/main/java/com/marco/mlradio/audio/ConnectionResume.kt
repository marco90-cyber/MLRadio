package com.marco.mlradio.audio

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.core.content.ContextCompat
import com.marco.mlradio.data.Settings
import com.marco.mlradio.util.AppLog

/**
 * Riprende la radio quando si riconnettono cuffie o Bluetooth, ma solo se era stata messa in pausa
 * dal loro scollegamento (non se l'utente l'aveva fermata).
 */
object ConnectionResume {

    fun register(context: Context, engine: PlayerEngine, settings: Settings) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (!settings.resumeOnConnect) return
                val connected = when (intent.action) {
                    BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED ->
                        intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1) == BluetoothProfile.STATE_CONNECTED
                    AudioManager.ACTION_HEADSET_PLUG -> intent.getIntExtra("state", 0) == 1
                    else -> false
                }
                if (connected) {
                    AppLog.i("Audio", "Uscita audio riconnessa: controllo se riprendere la radio")
                    engine.resumeAfterReconnect()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(AudioManager.ACTION_HEADSET_PLUG)
        }
        try {
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        } catch (e: Throwable) {
            AppLog.w("Audio", "Ripresa alla riconnessione non disponibile", e)
        }
    }
}
