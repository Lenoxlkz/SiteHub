package com.sitehub.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.sitehub.app.core.Prefs

private val Teal = Color(0xFF0E6E63)
private val TealLight = Color(0xFF4DD0B5)

private val LightColors = lightColorScheme(
    primary = Teal,
    secondary = TealLight,
    tertiary = Color(0xFFFFB300)
)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    secondary = Teal,
    tertiary = Color(0xFFFFB300)
)

/** Tema de la app: claro/oscuro/sistema + color dinámico (Android 12+). */
@Composable
fun SiteHubTheme(content: @Composable () -> Unit) {
    val dark = when (Prefs.theme) {
        Prefs.ThemeMode.LIGHT -> false
        Prefs.ThemeMode.DARK -> true
        Prefs.ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
