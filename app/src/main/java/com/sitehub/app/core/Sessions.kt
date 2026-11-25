package com.sitehub.app.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebResponse

/**
 * Sesiones de navegación: una GeckoSession por sitio (el audio de un sitio
 * sigue vivo mientras navegas otro), solo la activa está adjunta a la vista.
 * Los delegados (descargas, progreso, historial) se conectan aquí.
 */
object Sessions {

    private val sessions = LinkedHashMap<Long, GeckoSession>()
    private val urls = HashMap<Long, String>()
    private var nextId = 1L

    /** Pestaña activa (la que se muestra en Inicio). */
    var activeId by mutableStateOf(-1L)
        private set

    /** URL visible actual (para la barra del navegador). */
    var currentUrl by mutableStateOf(Prefs.defaultSites().first().url)
        private set

    /** Estado de navegación para los botones atrás/adelante. */
    var canGoBack by mutableStateOf(false)
        private set

    var canGoForward by mutableStateOf(false)
        private set

    /** Cargando (indicador fino bajo la barra). */
    var loading by mutableStateOf(false)
        private set

    fun open(url: String): Long {
        val id = nextId++
        val settings = GeckoSessionSettings.Builder().usePrivateMode(false)
        // UA Chrome real (o el modo elegido); null = UA nativo de Gecko.
        Prefs.userAgentOverride()?.let { settings.userAgentOverride(it) }
        val session = GeckoSession(settings.build())
        val runtime = Engine.runtime
            ?: error("El motor aún no está listo; espera a Engine.ensure(context)")
        session.open(runtime)
        session.loadUri(url)

        installDelegates(session, id)
        sessions[id] = session
        urls[id] = url
        activeId = id
        currentUrl = url
        return id
    }

    /** Abre (o reusa) el sitio embebido indicado. */
    fun openSite(site: Prefs.Site) {
        val existing = urls.entries.firstOrNull { it.value == site.url }
        if (existing != null) {
            activeId = existing.key
            currentUrl = site.url
        } else {
            open(site.url)
        }
    }

    fun close(id: Long) {
        sessions.remove(id)?.let { session ->
            runCatching { session.stop() }
            runCatching { session.close() }
        }
        urls.remove(id)
        if (activeId == id) {
            activeId = sessions.keys.firstOrNull() ?: -1L
            currentUrl = urls[activeId]
                ?: Prefs.homeUrl.ifEmpty { Prefs.defaultSites().first().url }
        }
    }

    fun closeAll() {
        sessions.values.forEach { session ->
            runCatching { session.stop() }
            runCatching { session.close() }
        }
        sessions.clear()
        urls.clear()
        activeId = -1L
        currentUrl = Prefs.homeUrl.ifEmpty { Prefs.defaultSites().first().url }
        canGoBack = false
        canGoForward = false
        loading = false
    }

    /** Libera las sesiones de fondo bajo presión de memoria. */
    fun closeBackground() {
        sessions.entries.filter { it.key != activeId }.forEach { (id, session) ->
            runCatching { session.stop() }
            runCatching { session.close() }
            sessions.remove(id)
            urls.remove(id)
        }
    }

    fun activeSession(): GeckoSession? = sessions[activeId]

    // ----- Navegación desde la UI -----

    fun goBack() { runCatching { activeSession()?.goBack() } }
    fun goForward() { runCatching { activeSession()?.goForward() } }
    fun reload() { runCatching { activeSession()?.reload() } }
    fun stopLoading() { runCatching { activeSession()?.stop() } }

    fun load(url: String) {
        val normalized = Prefs.normalizeUrl(url)
        if (activeSession() == null) {
            open(normalized)
        } else {
            urls[activeId] = normalized
            currentUrl = normalized
            runCatching { activeSession()?.loadUri(normalized) }
        }
        Prefs.setHome(normalized)
    }

    /** Adjunta la sesión activa a la vista del navegador (sin duplicar attachments). */
    fun attachActive(view: GeckoView) {
        val session = activeSession() ?: return
        if (view.session === session) return
        runCatching { view.releaseSession() }
        runCatching { view.setSession(session) }
    }

    // ----- Delegados -----

    private fun installDelegates(session: GeckoSession, id: Long) {
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                if (id == activeId) loading = true
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                if (id == activeId) loading = false
            }
        }

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean
            ) {
                val shown = url ?: return
                urls[id] = shown
                if (id == activeId) currentUrl = shown
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                if (id == activeId) Sessions.canGoBack = canGoBack
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                if (id == activeId) Sessions.canGoForward = canGoForward
            }
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onExternalResponse(
                session: GeckoSession,
                response: WebResponse
            ) {
                DownloadCenter.save(response)
            }
        }
    }
}
