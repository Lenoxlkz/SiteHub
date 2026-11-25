package com.sitehub.app.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sitehub.app.core.AmoClient
import com.sitehub.app.core.DownloadCenter
import com.sitehub.app.core.Engine
import com.sitehub.app.core.Prefs
import com.sitehub.app.core.Sessions
import com.sitehub.app.core.extensions.ExtensionsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension

// =====================================================================================
// INICIO — navegador
// =====================================================================================

@Composable
fun BrowserScreen(modifier: Modifier = Modifier) {
    val url = Sessions.currentUrl
    var urlField by remember(url) { mutableStateOf(url) }
    val canGoBack = Sessions.canGoBack
    val canGoForward = Sessions.canGoForward
    val loading = Sessions.loading
    val ready = Engine.ready
    val actions = ExtensionsManager.toolbarActions

    Column(modifier.fillMaxSize()) {
        // Barra de navegación
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { Sessions.goBack() }, enabled = canGoBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
            }
            OutlinedTextField(
                value = urlField,
                onValueChange = { urlField = it },
                singleLine = true,
                placeholder = { Text("Busca o escribe una dirección") },
                textStyle = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    if (loading) Sessions.stopLoading() else Sessions.reload()
                }
            ) {
                Icon(
                    if (loading) Icons.Filled.Close else Icons.Filled.Refresh,
                    contentDescription = if (loading) "Detener" else "Recargar"
                )
            }
            IconButton(onClick = { Sessions.load(urlField) }) {
                Icon(Icons.Filled.Done, contentDescription = "Ir")
            }
        }

        // Botones de acción de las extensiones (uBlock, AdGuard, …)
        if (actions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Extensiones:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                actions.forEach { (extId, _) ->
                    val ext = ExtensionsManager.extensions.firstOrNull { it.id == extId }
                    val letter = (ext?.metaData?.name ?: "?").take(1).uppercase()
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .clickable { ExtensionsManager.onToolbarAction(extId) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            letter,
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
            }
        }

        if (loading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Vista del motor: la sesión activa se adjunta aquí.
        if (ready && Sessions.activeId != -1L) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { context -> GeckoView(context).also { Sessions.attachActive(it) } },
                update = { view -> Sessions.attachActive(view) }
            )
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(Modifier.size(32.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Preparando el motor Firefox…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// =====================================================================================
// SITIOS — accesos embebidos + personalizados
// =====================================================================================

@Composable
fun SitesScreen(modifier: Modifier = Modifier) {
    val sites = Prefs.sites
    var showAdd by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Prefs.Site?>(null) }

    Column(modifier.padding(16.dp)) {
        Text("Sitios", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Toca para abrir. El primero es la página de inicio.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))

        Button(onClick = { showAdd = true }) {
            Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Agregar sitio")
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sites, key = { it.url }) { site ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            Sessions.openSite(site)
                            Nav.tab = Nav.Tab.INICIO
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                site.name.take(1).uppercase(),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(site.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                site.host(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { deleteTarget = site }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Eliminar",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddSiteDialog(
            onConfirm = { name, url ->
                Prefs.saveSites(sites + Prefs.Site(name, url))
                showAdd = false
            },
            onDismiss = { showAdd = false }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Eliminar sitio") },
            text = { Text("¿Quitar «${target.name}» de la lista de sitios?") },
            confirmButton = {
                TextButton(onClick = {
                    Prefs.saveSites(sites.filterNot { it.url == target.url })
                    deleteTarget = null
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun AddSiteDialog(onConfirm: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar sitio") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Dirección (mangalovers.josenunez.cl)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && url.isNotBlank(),
                onClick = { onConfirm(name.trim(), url.trim()) }
            ) { Text("Agregar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

// =====================================================================================
// EXTENSIONES
// =====================================================================================

@Composable
fun ExtensionsScreen(modifier: Modifier = Modifier) {
    val extensions = ExtensionsManager.extensions
    val busy = ExtensionsManager.busy
    val notice = ExtensionsManager.notice
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showStore by remember { mutableStateOf(false) }
    var installError by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            installError = null
            scope.launch {
                val result = ExtensionsManager.installFromFile(context, uri)
                result.onFailure { installError = it.message ?: "No se pudo instalar" }
            }
        }
    }

    LaunchedEffect(notice) {
        notice?.let {
            snackbar.showSnackbar(it)
            ExtensionsManager.consumeNotice()
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Extensiones", style = MaterialTheme.typography.headlineSmall)
                if (busy) {
                    Spacer(Modifier.width(10.dp))
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "WebExtensions reales de Firefox (MV2/MV3) sobre el motor Gecko: " +
                    "se instalan de la tienda oficial AMO, por URL .xpi o por archivo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showStore = true }, enabled = !busy) {
                    Icon(Icons.Filled.Search, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Tienda")
                }
                Button(onClick = { filePicker.launch(arrayOf("*/*")) }, enabled = !busy) {
                    Icon(Icons.Filled.Save, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Desde archivo")
                }
            }

            installError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(8.dp))

            if (extensions.isEmpty()) {
                Text(
                    "No hay extensiones instaladas. Prueba con «ublock origin» en la tienda: " +
                        "es la extensión más popular para bloquear anuncios y rastreadores.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(extensions, key = { it.id }) { ext ->
                        ExtensionCard(
                            ext = ext,
                            onToggle = { enabled ->
                                scope.launch { ExtensionsManager.setEnabled(ext, enabled) }
                            },
                            onDelete = {
                                scope.launch { ExtensionsManager.uninstall(ext) }
                            }
                        )
                    }
                }
            }
        }

        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (showStore) {
        StoreDialog(
            onDismiss = { showStore = false }
        )
    }
}

@Composable
private fun ExtensionCard(
    ext: WebExtension,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val enabled = ext.metaData?.enabled != false
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    ext.metaData?.name ?: ext.id,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "v" + (ext.metaData?.version ?: "?") + " · " + ext.id,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Desinstalar",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun StoreDialog(onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<AmoClient.AmoAddon>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tienda de complementos de Firefox") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Buscar o pegar URL/.xpi") },
                        placeholder = { Text("«ublock origin», addons.mozilla.org/…") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        enabled = !searching && query.isNotBlank(),
                        onClick = {
                            val q = query.trim()
                            if (q.startsWith("http")) {
                                searching = true
                                error = null
                                scope.launch {
                                    val r = ExtensionsManager.install(q)
                                    r.onSuccess {
                                        onDismiss()
                                    }.onFailure {
                                        error = it.message ?: "No se pudo instalar"
                                    }
                                    searching = false
                                }
                            } else {
                                searching = true
                                error = null
                                results = null
                                scope.launch {
                                    val outcome = withContext(Dispatchers.IO) {
                                        AmoClient.search(q)
                                    }
                                    outcome.onSuccess { results = it }
                                        .onFailure { err ->
                                            error = err.message ?: "No se pudo buscar"
                                        }
                                    searching = false
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = "Buscar")
                    }
                }
                Spacer(Modifier.height(8.dp))
                when {
                    searching -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Trabajando…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    error != null -> {
                        Text(error ?: "", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                    results != null -> {
                        val list = results.orEmpty()
                        if (list.isEmpty()) {
                            Text(
                                "Sin resultados: prueba otro nombre o pega la URL.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            LazyColumn(Modifier.height(300.dp)) {
                                items(list, key = { it.guid }) { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                searching = true
                                                error = null
                                                scope.launch {
                                                    val r = ExtensionsManager.installFromSlug(item.slug)
                                                    r.onSuccess { onDismiss() }
                                                        .onFailure { err ->
                                                            error = err.message ?: "No se pudo instalar"
                                                        }
                                                    searching = false
                                                }
                                            }
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Filled.OpenInNew,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(item.name, style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                "${item.users} usuarios · ★%.1f".format(item.rating),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        Text(
                            "Busca por nombre (por ejemplo «ublock origin») o pega la URL " +
                                "del complemento en addons.mozilla.org o un enlace .xpi directo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

// =====================================================================================
// DESCARGAS
// =====================================================================================

@Composable
fun DownloadsScreen(modifier: Modifier = Modifier) {
    val items = DownloadCenter.items
    val lastSaved = DownloadCenter.lastSaved
    val snackbar = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) { DownloadCenter.refresh(context) }
    LaunchedEffect(lastSaved) {
        lastSaved?.let {
            snackbar.showSnackbar(it)
            DownloadCenter.consumeLastSaved()
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            Text("Descargas", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Todo lo que descargues se guarda en Descargas/SiteHub del dispositivo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            if (items.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Aún no hay descargas.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items, key = { it.uri.toString() }) { item ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.displayName, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        humanSize(item.sizeBytes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = {
                                    runCatching {
                                        context.startActivity(DownloadCenter.openIntent(item))
                                    }
                                }) {
                                    Icon(Icons.Filled.OpenInNew, contentDescription = "Abrir")
                                }
                                IconButton(onClick = {
                                    runCatching {
                                        context.startActivity(
                                            Intent.createChooser(
                                                DownloadCenter.shareIntent(item), "Compartir"
                                            )
                                        )
                                    }
                                }) {
                                    Icon(Icons.Filled.Share, contentDescription = "Compartir")
                                }
                                IconButton(onClick = { DownloadCenter.delete(item) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Eliminar",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

private fun humanSize(bytes: Long): String = when {
    bytes >= 1 shl 20 -> "%.1f MB".format(bytes / 1048576.0)
    bytes >= 1 shl 10 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

// =====================================================================================
// AJUSTES
// =====================================================================================

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    Column(modifier.padding(16.dp).fillMaxSize()) {
        Text("Ajustes", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        Text("Tema", style = MaterialTheme.typography.titleMedium)
        Prefs.ThemeMode.entries.forEach { mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        Prefs.applyTheme(mode)
                        Engine.applyTheme()
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = Prefs.theme == mode, onClick = {
                    Prefs.applyTheme(mode)
                    Engine.applyTheme()
                })
                Text(mode.label)
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Cookies", style = MaterialTheme.typography.titleMedium)
        Text(
            "Se aplica al reiniciar la app (el motor lee la política al arrancar).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Prefs.CookieMode.entries.forEach { mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { Prefs.applyCookies(mode) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = Prefs.cookies == mode, onClick = {
                    Prefs.applyCookies(mode)
                })
                Column {
                    Text(mode.label, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        mode.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Identidad del navegador (UA)", style = MaterialTheme.typography.titleMedium)
        Text(
            "El UA de Chrome real es el que mejor pasa los desafíos anti-bot de " +
                "Cloudflare. Se aplica a las páginas que abras a partir de ahora.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Prefs.UaMode.entries.forEach { mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { Prefs.applyUa(mode) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = Prefs.ua == mode, onClick = { Prefs.applyUa(mode) })
                Column {
                    Text(mode.label, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        mode.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Motor", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Firefox real (GeckoView 141): WebExtensions nativas, cookies " +
                        "por política, Cloudflare sin bloqueos añadidos y CORS según " +
                        "el estándar. Para desbloquear CORS por sitio puedes instalar " +
                        "una extensión como CORS Unblock desde la tienda.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "SiteHub 1.0.0 · arquitectura ligera · APK arm64",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline
        )
    }
}
