package com.sitehub.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sitehub.app.core.Engine
import com.sitehub.app.core.Prefs
import com.sitehub.app.core.Sessions
import com.sitehub.app.core.extensions.ExtensionsManager
import org.mozilla.geckoview.GeckoView

/** Pestañas de la app (sin navigation-compose: interruptor simple y ligero). */
object Nav {
    enum class Tab(val label: String) {
        INICIO("Inicio"), SITIOS("Sitios"), EXTENSIONES("Extensiones"),
        DESCARGAS("Descargas"), AJUSTES("Ajustes")
    }

    var tab by mutableStateOf(Tab.INICIO)
}

/**
 * Raíz de la UI: barra inferior con las 5 pestañas + diálogo de popup de
 * extensiones (uBlock, AdGuard, etc. muestran su panel aquí).
 */
@Composable
fun AppRoot() {
    // Cuando el motor termina de cargar, abre la página de inicio (o la última).
    LaunchedEffect(Engine.ready) {
        if (Engine.ready && Sessions.activeId == -1L) {
            val home = Prefs.homeUrl.ifEmpty { Prefs.defaultSites().first().url }
            Sessions.open(home)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Nav.Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = Nav.tab == tab,
                        onClick = { Nav.tab = tab },
                        icon = {
                            Icon(
                                when (tab) {
                                    Nav.Tab.INICIO -> Icons.Filled.Language
                                    Nav.Tab.SITIOS -> Icons.Filled.Public
                                    Nav.Tab.EXTENSIONES -> Icons.Filled.Extension
                                    Nav.Tab.DESCARGAS -> Icons.Filled.Download
                                    Nav.Tab.AJUSTES -> Icons.Filled.Settings
                                },
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        val contentModifier = Modifier.padding(padding)
        when (Nav.tab) {
            Nav.Tab.INICIO -> BrowserScreen(contentModifier)
            Nav.Tab.SITIOS -> SitesScreen(contentModifier)
            Nav.Tab.EXTENSIONES -> ExtensionsScreen(contentModifier)
            Nav.Tab.DESCARGAS -> DownloadsScreen(contentModifier)
            Nav.Tab.AJUSTES -> SettingsScreen(contentModifier)
        }
    }

    // Popups reales de las extensiones (panel de uBlock, etc.).
    val popup = ExtensionsManager.popupRequest
    if (popup != null) {
        AlertDialog(
            onDismissRequest = { ExtensionsManager.closePopup(popup.extensionId) },
            title = { Text(popup.name) },
            text = {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    factory = { context ->
                        GeckoView(context).apply {
                            ExtensionsManager.popupSession(popup.extensionId)?.let { session ->
                                setSession(session)
                            }
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { ExtensionsManager.closePopup(popup.extensionId) }) {
                    Text("Cerrar")
                }
            }
        )
    }
}
