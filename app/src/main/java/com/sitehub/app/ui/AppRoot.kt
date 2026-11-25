package com.sitehub.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sitehub.app.core.Engine
import com.sitehub.app.core.HomeData
import com.sitehub.app.core.Sessions
import com.sitehub.app.core.extensions.ExtensionsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoView

/** Pestañas de la app. Inicio es un Home; el navegador solo aparece al navegar. */
object Nav {
    enum class Tab(val label: String) {
        INICIO("Inicio"), EXTENSIONES("Extensiones"), DESCARGAS("Descargas"), AJUSTES("Ajustes")
    }
    var tab by mutableStateOf(Tab.INICIO)
    var browsing by mutableStateOf(false)   // false = Home, true = navegador
}

@Composable
fun AppRoot() {
    val scope = rememberCoroutineScope()

    // Navegar solo cuando el usuario lo pide (el motor ya está listo a estas alturas).
    fun navigate(raw: String) {
        val q = raw.trim()
        if (q.isEmpty()) return
        scope.launch {
            var waited = 0
            while (!Engine.ready && waited < 15000) { delay(50); waited += 50 }
            if (Engine.ready) {
                Sessions.load(q)
                Nav.browsing = true
                Nav.tab = Nav.Tab.INICIO
            }
        }
    }

    // Historial de recientes mientras se navega.
    val currentUrl = Sessions.currentUrl
    LaunchedEffect(currentUrl, Nav.browsing) {
        if (Nav.browsing && currentUrl.startsWith("http")) HomeData.addRecent(currentUrl)
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Nav.Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = Nav.tab == tab,
                        onClick = { Nav.tab = tab; if (tab == Nav.Tab.INICIO) Nav.browsing = false },
                        icon = {
                            Icon(
                                when (tab) {
                                    Nav.Tab.INICIO -> Icons.Filled.Home
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
            Nav.Tab.INICIO -> if (Nav.browsing) BrowserScreen(contentModifier) else HomeScreen(contentModifier, onNavigate = ::navigate)
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
                    modifier = Modifier.fillMaxWidth().height(420.dp),
                    factory = { context ->
                        GeckoView(context).apply {
                            ExtensionsManager.popupSession(popup.extensionId)?.let { session -> setSession(session) }
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { ExtensionsManager.closePopup(popup.extensionId) }) { Text("Cerrar") }
            }
        )
    }

    // Confirmación de permisos antes de instalar una extensión.
    val installPrompt = ExtensionsManager.installPrompt
    if (installPrompt != null) {
        AlertDialog(
            onDismissRequest = { ExtensionsManager.resolveInstallPrompt(false) },
            title = { Text("Instalar «${installPrompt.name}»") },
            text = {
                Column {
                    Text("Esta extensión solicita los siguientes permisos:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    if (installPrompt.permissions.isEmpty() && installPrompt.origins.isEmpty()) {
                        Text("Sin permisos especiales.", style = MaterialTheme.typography.bodySmall)
                    }
                    installPrompt.permissions.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    installPrompt.origins.forEach { Text("• Acceso a: $it", style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = { TextButton(onClick = { ExtensionsManager.resolveInstallPrompt(true) }) { Text("Instalar") } },
            dismissButton = { TextButton(onClick = { ExtensionsManager.resolveInstallPrompt(false) }) { Text("Cancelar") } }
        )
    }
}
