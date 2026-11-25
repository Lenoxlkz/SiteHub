package com.sitehub.app.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sitehub.app.core.extensions.ExtensionsManager
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * Motor Firefox real (GeckoView). Se crea UNA VEZ y vive con el proceso.
 *
 * Config anti-bloqueos (Cloudflare / CORS / anti-bot):
 * - AntiTracking.NONE: los retos de Cloudflare no se rompen por filtros.
 * - Cookies según ajuste del usuario (ACCEPT_ALL por defecto), aplicables EN VIVO.
 * - JavaScript activado y UA de Chrome real por sesión (Prefs.userAgentOverride).
 */
object Engine {

    private var runtimeRef: GeckoRuntime? = null

    val runtime: GeckoRuntime?
        get() = runtimeRef

    /** Contexto de aplicación (intents externos, etc.). */
    var appContext: Context? = null
        private set

    /** Estado Compose: la UI reacciona cuando el motor termina de cargar. */
    var ready by mutableStateOf(false)
        private set

    /** Error de arranque visible en la UI (antes se tragaba en silencio). */
    var initError by mutableStateOf<String?>(null)
        private set

    /** Crea el runtime si no existe. DEBE llamarse en el hilo principal. */
    fun ensure(context: Context) {
        if (runtimeRef != null) return
        val app = context.applicationContext
        appContext = app

        runtimeRef = GeckoRuntime.create(
            app,
            GeckoRuntimeSettings.Builder()
                .javaScriptEnabled(true)
                .consoleOutput(false)
                .contentBlocking(
                    ContentBlocking.Settings.Builder()
                        .antiTracking(ContentBlocking.AntiTracking.NONE)
                        .cookieBehavior(Prefs.cookies.geckoValue)
                        .build()
                )
                .extensionsProcessEnabled(true)
                .extensionsWebAPIEnabled(true)
                .preferredColorScheme(preferredColorScheme())
                .build()
        )

        ExtensionsManager.attach(runtimeRef!!, app)
        ready = true
        initError = null
    }

    /**
     * Arranca el motor DESPUÉS del primer fotograma REAL: el callback de
     * Choreographer corre en el primer frame y el post al Handler se ejecuta
     * justo después de que ese frame se dibuje. (Handler.post a secas desde
     * Application.onCreate corría ANTES de cualquier frame.)
     */
    fun warmUpAfterFirstFrame(context: Context) {
        val app = context.applicationContext
        appContext = app
        Choreographer.getInstance().postFrameCallback {
            Handler(Looper.getMainLooper()).post {
                try {
                    ensure(app)
                } catch (t: Throwable) {
                    initError = t.message ?: t.javaClass.simpleName
                }
            }
        }
    }

    /** Reintento manual desde la UI si el motor falló al arrancar. */
    fun retryInit() {
        val ctx = appContext ?: return
        initError = null
        try {
            ensure(ctx)
        } catch (t: Throwable) {
            initError = t.message ?: t.javaClass.simpleName
        }
    }

    /** Política de cookies EN VIVO (ContentBlocking.Settings.setCookieBehavior). */
    fun applyCookies(mode: Prefs.CookieMode) {
        runCatching {
            runtimeRef?.settings?.contentBlocking?.setCookieBehavior(mode.geckoValue)
        }
    }

    /** ColorScheme del contenido según el tema elegido. */
    fun preferredColorScheme(): Int = when (Prefs.theme) {
        Prefs.ThemeMode.LIGHT -> GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
        Prefs.ThemeMode.DARK -> GeckoRuntimeSettings.COLOR_SCHEME_DARK
        Prefs.ThemeMode.SYSTEM -> GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM
    }

    /** Aplica el tema del contenido al motor en vivo (Ajustes → Tema). */
    fun applyTheme() {
        runCatching { runtimeRef?.settings?.setPreferredColorScheme(preferredColorScheme()) }
    }
}
