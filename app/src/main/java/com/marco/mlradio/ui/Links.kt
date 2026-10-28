package com.marco.mlradio.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        Toast.makeText(ctx, "Nessuna app per aprire il link", Toast.LENGTH_SHORT).show()
    }
}

/** Impedisce che i tocchi su una schermata a tutto schermo arrivino a quella che sta sotto. */
fun Modifier.blockTouches(): Modifier = pointerInput(Unit) { detectTapGestures { } }
