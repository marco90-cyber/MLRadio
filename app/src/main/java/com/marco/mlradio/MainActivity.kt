package com.marco.mlradio

import android.Manifest
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.marco.mlradio.ui.MLRadioTheme
import com.marco.mlradio.ui.MainScreen

class MainActivity : ComponentActivity() {

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    /** Con il volume digitale (DVC) acceso i tasti del volume regolano il volume dell'app. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val up = keyCode == KeyEvent.KEYCODE_VOLUME_UP
        if (up || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if ((application as MLRadioApp).engine.stepDvc(up)) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        askPermissionsOnce()
        val env = (application as MLRadioApp).env
        setContent {
            MLRadioTheme(env.settings) {
                MainScreen(env)
                AppUpdateHost()
            }
        }
    }

    /** Una sola volta: notifiche (controlli nella notifica) e Bluetooth (ripresa alla riconnessione). */
    private fun askPermissionsOnce() {
        val sp = getSharedPreferences("mlradio_boot", MODE_PRIVATE)
        if (sp.getBoolean("permissions_asked", false)) return
        sp.edit().putBoolean("permissions_asked", true).apply()
        permissions.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.BLUETOOTH_CONNECT))
    }
}
