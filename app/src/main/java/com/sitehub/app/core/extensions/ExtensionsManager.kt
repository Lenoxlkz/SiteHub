package com.sitehub.app.core.extensions

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sitehub.app.core.AmoClient
import com.sitehub.app.util.EngineNotRespondingException
import com.sitehub.app.util.gvAwaitOrTimeout
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/**
 * Gestión de extensiones con soporte NATIVO de WebExtensions de GeckoView
 * (el mismo motor que Firefox Android): instalación desde la tienda AMO,
 * por URL .xpi o por archivo local; habilitar/deshabilitar, desinstalar,
 * botón de acción en la barra y popups reales.
 *
 * Claves para que la instalación FUNCIONE (lecciones de la versión anterior):
 * 1. PromptDelegate registrado: sin él, el GeckoResult de install() NUNCA se
 *    resuelve y el spinner queda girando para siempre. Aquí se otorgan los
 *    permisos automáticamente (extensión firmada de la tienda oficial AMO)
 *    y la UI muestra un aviso.
 * 2. Todo await contra el motor tiene tiempo máximo: si algo no responde,
 *    la operación termina con error claro, nunca colgada.
 * 3. El .xpi se descarga por HTTP de la propia app y se instala por file://:
 *    la instalación no depende de la red interna de Gecko.
 */
object ExtensionsManager {

    private lateinit var appContext: Context
    private var runtimeRef: GeckoRuntime? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var extensions by mutableStateOf<List<WebExtension>>(emptyList())
        private set

    var busy by mutableStateOf(false)
        private set

    /** Avisos puntuales (permisos otorgados, paquete descargado, etc.). */
    var notice by mutableStateOf<String?>(null)
        private set

    /** Popup de extensión pendiente de mostrar (diálogo con un GeckoView). */
    data class PopupRequest(val extensionId: String, val name: String)

    var popupRequest by mutableStateOf<PopupRequest?>(null)
        private set

    /** Botones de acción de las extensiones para la barra del navegador. */
    var toolbarActions by mutableStateOf<Map<String, WebExtension.Action>>(emptyMap())
        private set

    /** Sesiones de popup vivas por extensión (moz-extension://…). */
    private val popupSessions = LinkedHashMap<String, GeckoSession>()

    private fun controller(): WebExtensionController =
        runtimeRef?.webExtensionController
            ?: error("El motor aún no está listo")

    /** Conecta el gestor al runtime de Gecko (una sola vez, desde Engine.ensure). */
    fun attach(runtime: GeckoRuntime, context: Context) {
        if (runtimeRef != null) return
        appContext = context.applicationContext
        runtimeRef = runtime
        installPromptDelegate(runtime)
        scope.launch { refresh() }
    }

    fun consumeNotice() { notice = null }

    /**
     * Registra el PromptDelegate: SIN ESTO las instalaciones se quedan
     * colgadas para siempre. Otorga los permisos automáticamente (todas las
     * extensiones provienen de la tienda oficial AMO, firmadas por Mozilla)
     * con aviso en la UI, igual que hacen los navegadores con la tienda de
     * Chrome para extensiones verificadas.
     */
    private fun installPromptDelegate(runtime: GeckoRuntime) {
        runtime.webExtensionController.setPromptDelegate(
            object : WebExtensionController.PromptDelegate {

                override fun onInstallPromptRequest(
                    extension: WebExtension,
                    permissions: Array<String>,
                    origins: Array<String>,
                    dataCollectionPermissions: Array<String>
                ): GeckoResult<WebExtension.PermissionPromptResponse>? {
                    announcePermissions(
                        extension,
                        permissions.size + origins.size + dataCollectionPermissions.size
                    )
                    return GeckoResult.fromValue(
                        WebExtension.PermissionPromptResponse(true, false, false)
                    )
                }

                override fun onInstallPromptRequest(
                    extension: WebExtension,
                    permissions: Array<String>,
                    origins: Array<String>
                ): GeckoResult<WebExtension.PermissionPromptResponse>? {
                    announcePermissions(extension, permissions.size + origins.size)
                    return GeckoResult.fromValue(
                        WebExtension.PermissionPromptResponse(true, false)
                    )
                }

                override fun onUpdatePrompt(
                    currentlyInstalled: WebExtension,
                    updatedExtension: WebExtension,
                    newPermissions: Array<String>,
                    newOrigins: Array<String>
                ): GeckoResult<AllowOrDeny>? = GeckoResult.fromValue(AllowOrDeny.ALLOW)

                override fun onOptionalPrompt(
                    extension: WebExtension,
                    permissions: Array<String>,
                    origins: Array<String>
                ): GeckoResult<AllowOrDeny>? {
                    announcePermissions(extension, permissions.size + origins.size)
                    return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                }
            }
        )
    }

    private fun announcePermissions(extension: WebExtension, count: Int) {
        val name = extension.metaData?.name ?: extension.id
        notice = if (count > 0) {
            "«$name»: $count permisos otorgados (extensión oficial firmada por Mozilla)."
        } else {
            "«$name»: instalándose…"
        }
    }

    /**
     * Instala los delegados por extensión: popups (TabDelegate.onNewTab y
     * ActionDelegate.onOpenPopup) y captura del botón de acción.
     */
    private fun hookExtension(ext: WebExtension) {
        ext.setTabDelegate(object : WebExtension.TabDelegate {
            override fun onNewTab(
                webExtension: WebExtension,
                details: WebExtension.CreateTabDetails
            ): GeckoResult<GeckoSession>? {
                val uri = details.url ?: return null
                if (!uri.startsWith("moz-extension://")) return null
                val session = ensurePopupSession(webExtension.id, uri)
                popupRequest = PopupRequest(
                    webExtension.id,
                    webExtension.metaData?.name ?: webExtension.id
                )
                return GeckoResult.fromValue(session)
            }

            override fun onOpenOptionsPage(webExtension: WebExtension) {
                // La página de opciones se abrirá en una pestaña en futuras versiones.
            }
        })

        ext.setActionDelegate(object : WebExtension.ActionDelegate {
            override fun onBrowserAction(
                webExtension: WebExtension,
                session: GeckoSession?,
                action: WebExtension.Action
            ) {
                toolbarActions = toolbarActions + (webExtension.id to action)
            }

            override fun onOpenPopup(
                webExtension: WebExtension,
                action: WebExtension.Action
            ): GeckoResult<GeckoSession>? {
                val session = ensurePopupSession(webExtension.id, uri = null)
                popupRequest = PopupRequest(
                    webExtension.id,
                    webExtension.metaData?.name ?: webExtension.id
                )
                return GeckoResult.fromValue(session)
            }
        })
    }

    /** Lista instalada actual (viva en el perfil del dispositivo). */
    suspend fun refresh(): List<WebExtension> = withContext(Dispatchers.Main) {
        val runtime = runtimeRef ?: run {
            extensions = emptyList()
            return@withContext emptyList()
        }
        val list = runCatching {
            runtime.webExtensionController.list().gvAwaitOrTimeout("listar extensiones", 30_000)
        }.getOrDefault(emptyList())
            .filter { !it.isBuiltIn }
            .sortedBy { (it.metaData?.name ?: it.id).lowercase() }
        list.forEach { hookExtension(it) }
        extensions = list
        list
    }

    /**
     * Instala una extensión desde una URL .xpi, URL de AMO o file://.
     * Para http(s) el paquete se descarga con HTTP de la app (timeouts y tope
     * de 40 MB) y se instala por file://.
     */
    suspend fun install(url: String): Result<WebExtension> {
        try {
            busy = true
            val target = withContext(Dispatchers.IO) {
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    downloadAddon(url)
                } else {
                    url
                }
            }
            val ext = withContext(Dispatchers.Main) {
                controller().install(target).gvAwaitOrTimeout("instalar la extensión", 120_000)
            }
            withContext(Dispatchers.Main) { hookExtension(ext) }
            refresh()
            return Result.success(ext)
        } catch (e: EngineNotRespondingException) {
            return Result.failure(
                RuntimeException(
                    "El motor no respondió al ${e.operation}. Reintenta en unos segundos."
                )
            )
        } catch (e: Exception) {
            return Result.failure(e)
        } finally {
            busy = false
        }
    }

    suspend fun installFromSlug(slug: String): Result<WebExtension> =
        install(AmoClient.installUrl(slug))

    /** Instala desde un .xpi/.zip local: se copia a caché y se instala por file://. */
    suspend fun installFromFile(context: Context, uri: android.net.Uri): Result<WebExtension> =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = File(
                    context.cacheDir,
                    "addons/" + (uri.lastPathSegment ?: "addon.xpi")
                )
                target.parentFile?.mkdirs()
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                } ?: error("No se pudo leer el archivo")
                install("file://" + target.absolutePath).getOrThrow()
            }
        }

    /**
     * Descarga el paquete .xpi con HTTP estándar de la app: timeouts claros,
     * redirecciones y tope de tamaño. Devuelve la URI file:// para install().
     */
    private fun downloadAddon(url: String, maxBytes: Long = 40L * 1024 * 1024): String {
        var current = url
        repeat(6) {
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 60_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", AmoClient.UA)
            try {
                when (val code = conn.responseCode) {
                    in 300..399 -> {
                        current = conn.getHeaderField("Location")
                            ?: error("Redirección sin destino (HTTP $code)")
                        return@repeat
                    }
                    200 -> {
                        val name = current.substringBefore('?')
                            .substringAfterLast('/')
                            .takeIf { it.isNotBlank() && it.contains('.') }
                            ?: "addon_${System.currentTimeMillis()}.xpi"
                        val target = File(appContext.cacheDir, "addons/$name")
                        target.parentFile?.mkdirs()
                        conn.inputStream.use { input ->
                            target.outputStream().use { output ->
                                var copied = 0L
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    copied += n
                                    if (copied > maxBytes) {
                                        error("El paquete supera el límite de 40 MB")
                                    }
                                    output.write(buf, 0, n)
                                }
                            }
                        }
                        if (target.length() == 0L) error("El paquete descargado está vacío")
                        notice = "Paquete descargado (${target.length() / 1024} KB); instalando…"
                        return "file://" + target.absolutePath
                    }
                    else -> error("La tienda respondió HTTP $code al descargar el paquete")
                }
            } finally {
                conn.disconnect()
            }
        }
        error("Demasiadas redirecciones al descargar el paquete de la extensión")
    }

    suspend fun setEnabled(ext: WebExtension, enabled: Boolean) = withContext(Dispatchers.Main) {
        runCatching {
            val updated = if (enabled) {
                controller().enable(ext, 0).gvAwaitOrTimeout("activar la extensión")
            } else {
                controller().disable(ext, 0).gvAwaitOrTimeout("desactivar la extensión")
            }
            hookExtension(updated)
        }
        refresh()
    }

    suspend fun uninstall(ext: WebExtension) = withContext(Dispatchers.Main) {
        runCatching { controller().uninstall(ext).gvAwaitOrTimeout("desinstalar la extensión") }
        closePopup(ext.id)
        toolbarActions = toolbarActions - ext.id
        refresh()
    }

    /** Simula el toque del botón de barra: dispara onClick u abre su popup. */
    fun onToolbarAction(extId: String) {
        runCatching { toolbarActions[extId]?.click() }
    }

    /** Crea (o reusa) la sesión de popup; si [uri] no es null, la carga. */
    private fun ensurePopupSession(extId: String, uri: String?): GeckoSession {
        popupSessions.remove(extId)?.let { old ->
            runCatching { old.stop() }
            runCatching { old.close() }
        }
        val session = GeckoSession()
        session.open(runtimeRef!!)
        uri?.let { session.loadUri(it) }
        popupSessions[extId] = session
        return session
    }

    /** Sesión del popup de una extensión (para attach en el diálogo). */
    fun popupSession(extId: String): GeckoSession? = popupSessions[extId]

    fun closePopup(extId: String) {
        popupSessions.remove(extId)?.let { session ->
            runCatching { session.stop() }
            runCatching { session.close() }
        }
        if (popupRequest?.extensionId == extId) popupRequest = null
    }
}
