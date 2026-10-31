package com.marco.mlradio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marco.mlradio.ui.eq.EqPanel
import com.marco.mlradio.ui.eq.Ml

/** Equalizzatore con i colori del tema dell'app (chiaro o scuro). */
@Composable
fun EqualizerScreen(env: AppEnv) {
    val scheme = MaterialTheme.colorScheme
    val p = Ml.fromScheme(scheme, amoled = false)
    Ml.palette = p
    Box(Modifier.fillMaxSize().background(p.background).safeDrawingPadding()) {
        EqPanel(env, "MLRadio", header = {
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { env.showEq = false }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = p.text)
                }
                Text("Equalizzatore", color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        })
    }
}
