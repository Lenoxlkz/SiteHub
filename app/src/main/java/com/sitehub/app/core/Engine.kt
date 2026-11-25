package com.sitehub.app.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sitehub.app.core.extensions.ExtensionsManager
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

/**
 * Motor Firefox real (GeckoView). Se crea UNA VEZ, justo después del primer
 * fotograma (nunca bloqueando el arranque: la UI aparece al instante y Gecko
 * termina de cargar por detrás, como en Fenix).
 *
 * Config anti-bloqueos (Cloudflare / CORS / anti-bot):
 * - Sin rastreo de contenido (AntiTracking.NONE): los retos de Cloudflare no
 *   se rompen por filtros que interfieren con su JavaScript.
 * - Cookies según ajuste del usuario (ACCEPT_ALL por defecto).
 * - JavaScript activado (imprescindible para retos y SPAs).
 * - UA de Chrome real por sesión (ver Prefs.userAgentOverride): es la pieza
 *   que evita las páginas "verificando su navegador" degradadas o captchas
 *   innecesarios.
 */
object Engine {

    private var runtimeRef: GeckoRuntime? = null

    val runtime: GeckoRuntime?
        get() = runtimeRef

    /** Estado Compose: la UI reacciona cuando el motor termina de cargar. */
    var ready by mutableStateOf(false)
        private set

    /** Crea el runtime si no existe. DEBE llamarse en el hilo principal. */
    fun ensure(context: Context) {
        if (runtimeRef != null) return
        val appContext = context.applicationContext

        runtimeRef = GeckoRuntime.create(
            appContext,
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
                .build()
        )

        // Extensiones: prompt de permisos + listado + hooks, en cuanto hay motor.
        ExtensionsManager.attach(runtimeRef!!, appContext)
        ready = true
    }

    /**
     * Arranca el motor DESPUÉS del primer fotograma: la app ya es visible
     * cuando Gecko carga (evita el "app no responde" en arranques lentos).
     */
    fun warmUpAfterFirstFrame(context: Context) {
        Handler(Looper.getMainLooper()).post {
            runCatching { ensure(context) }
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
