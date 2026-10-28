package com.marco.mlradio.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.marco.mlradio.data.Settings

/** True se il tema in uso (non quello di sistema) è scuro. */
val LocalDarkTheme = staticCompositionLocalOf { false }

private val FallbackLight = lightColorScheme(
    primary = Color(0xFF1D5FD1),
    secondary = Color(0xFF565F71),
    tertiary = Color(0xFF715573),
)

private val FallbackDark = darkColorScheme(
    primary = Color(0xFFB0C6FF),
    secondary = Color(0xFFBEC6DC),
    tertiary = Color(0xFFDEBCDF),
)

/** Material You: i colori seguono lo sfondo del telefono; tema chiaro/scuro automatico o scelto a mano. */
@Composable
fun MLRadioTheme(settings: Settings, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.themeMode) {
        1 -> false
        2 -> true
        else -> systemDark
    }
    val ctx = LocalContext.current
    val scheme = when {
        settings.dynamicColor -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> FallbackDark
        else -> FallbackLight
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val c = WindowCompat.getInsetsController(window, view)
                c.isAppearanceLightStatusBars = !dark
                c.isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
