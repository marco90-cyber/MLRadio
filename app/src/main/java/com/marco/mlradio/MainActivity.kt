package com.marco.mlradio

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.marco.mlradio.ui.MLRadioTheme
import com.marco.mlradio.ui.MainScreen

class MainActivity : ComponentActivity() {

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

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
