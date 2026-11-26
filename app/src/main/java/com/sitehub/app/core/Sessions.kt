package com.sitehub.app.core

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebResponse

object Sessions {
    private val sessions = LinkedHashMap<Long, GeckoSession>()
    private val urls = HashMap<Long, String>()
    private val backState = HashMap<Long, Boolean>()
    private val forwardState = HashMap<Long, Boolean>()
    private val loadingState = HashMap<Long, Boolean>()
    private val pendingUrl = HashMap<Long, String>()
    private val crashCount = HashMap<Long, Int>()
    private var nextId = 1L

    var activeId by mutableStateOf(-1L)
        private set
    var currentUrl by mutableStateOf(Prefs.defaultSites().first().url)
        private set
    var canGoBack by mutableStateOf(false)
        private set
    var canGoForward by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set

    private fun syncActiveState() {
        currentUrl = urls[activeId] ?: Prefs.homeUrl.ifEmpty { Prefs.defaultSites().first().url }
        canGoBack = backState[activeId] ?: false
        canGoForward = forwardState[activeId] ?: false
        loading = loadingState[activeId] ?: false
    }

    private fun createSession(id: Long): GeckoSession {
        val settings = GeckoSessionSettings.Builder().usePrivateMode(false)
        Prefs.userAgentOverride()?.let { settings.userAgentOverride(it) }
        val session = GeckoSession(settings.build())
        val runtime = Engine.runtime ?: error("El motor aún no está listo")
        installDelegates(session, id)
        session.open(runtime)
        sessions[id] = session
        return session
    }

    fun open(url: String): Long {
        if (Engine.runtime == null) return -1L
        val id = nextId++
        createSession(id)
        urls[id] = url
        pendingUrl[id] = url
        activeId = id
        syncActiveState()
        return id
    }

    fun openSite(site: Prefs.Site) {
        val existing = urls.entries.firstOrNull { it.value == site.url }
        if (existing != null) {
            activeId = existing.key
            syncActiveState()
        } else {
            open(site.url)
        }
    }

    fun close(id: Long) {
        sessions.remove(id)?.let { runCatching { it.stop() }; runCatching { it.close() } }
        urls.remove(id); backState.remove(id); forwardState.remove(id)
        loadingState.remove(id); pendingUrl.remove(id); crashCount.remove(id)
        if (activeId == id) {
            activeId = sessions.keys.firstOrNull() ?: -1L
            syncActiveState()
        }
    }

    fun closeAll() {
        sessions.values.forEach { runCatching { it.stop() }; runCatching { it.close() } }
        sessions.clear(); urls.clear(); backState.clear(); forwardState.clear()
        loadingState.clear(); pendingUrl.clear(); crashCount.clear()
        activeId = -1L
        currentUrl = Prefs.homeUrl.ifEmpty { Prefs.defaultSites().first().url }
        canGoBack = false; canGoForward = false; loading = false
    }

    fun closeBackground() {
        sessions.entries.filter { it.key != activeId }.forEach { (id, s) ->
            runCatching { s.stop() }; runCatching { s.close() }
            sessions.remove(id); urls.remove(id); backState.remove(id)
            forwardState.remove(id); loadingState.remove(id); pendingUrl.remove(id); crashCount.remove(id)
        }
    }

    fun activeSession(): GeckoSession? = sessions[activeId]
    fun goBack() { runCatching { activeSession()?.goBack() } }
    fun goForward() { runCatching { activeSession()?.goForward() } }
    fun reload() { runCatching { activeSession()?.reload() } }
    fun stopLoading() { runCatching { activeSession()?.stop() } }

    fun load(url: String) {
        if (Engine.runtime == null) return   // sin motor: no dejar estado inconsistente
        val normalized = Prefs.normalizeUrl(url)
        if (activeSession() == null) {
            open(normalized)
        } else {
            urls[activeId] = normalized
            currentUrl = normalized
            runCatching { activeSession()?.loadUri(normalized) }
        }
    }

    fun attachActive(view: GeckoView) {
        val session = activeSession() ?: return
        if (view.session !== session) {
            runCatching { view.releaseSession() }
            runCatching { view.setSession(session) }
        }
        val pending = pendingUrl.remove(activeId)
        if (pending != null) runCatching { session.loadUri(pending) }
    }

    private fun openExternal(uri: String): Boolean {
        val ctx = Engine.appContext ?: return false
        return runCatching {
            val intent = if (uri.startsWith("intent:"))
                Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    component = null
                }
            else Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(ctx.packageManager) != null) {
                ctx.startActivity(intent); true
            } else false
        }.getOrDefault(false)
    }

    private fun installDelegates(session: GeckoSession, id: Long) {
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                loadingState[id] = true
                if (id == activeId) loading = true
            }
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                loadingState[id] = false
                if (success) crashCount[id] = 0
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
                backState[id] = canGoBack
                if (id == activeId) Sessions.canGoBack = canGoBack
            }
            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                forwardState[id] = canGoForward
                if (id == activeId) Sessions.canGoForward = canGoForward
            }
            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny>? {
                val scheme = request.uri.substringBefore(':', "").lowercase()
                if (scheme in setOf("http", "https", "about", "data", "blob", "javascript")) return null
                openExternal(request.uri)
                return GeckoResult.fromValue(AllowOrDeny.DENY)
            }
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                if (Engine.runtime == null) return null
                val scheme = uri.substringBefore(':', "").lowercase()
                if (scheme !in setOf("http", "https", "about", "blob", "data")) {
                    openExternal(uri)
                    return null
                }
                val newId = nextId++
                val created = createSession(newId)
                urls[newId] = uri
                activeId = newId
                syncActiveState()
                return GeckoResult.fromValue(created)
            }
        }
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                DownloadCenter.save(response)
            }
            override fun onCloseRequest(session: GeckoSession) {
                val entry = sessions.entries.firstOrNull { it.value == session } ?: return
                close(entry.key)
            }
            override fun onCrash(session: GeckoSession) {
                val n = (crashCount[id] ?: 0) + 1
                crashCount[id] = n
                if (n <= 3) {
                    val url = urls[id]
                    if (url != null) runCatching { session.loadUri(url) }
                }
            }
        }
    }
}
