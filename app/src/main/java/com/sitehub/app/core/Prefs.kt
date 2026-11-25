package com.sitehub.app.core

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.ContentBlocking

/**
 * Ajustes del usuario: tema, cookies, UA, sitios y descargas.
 * Estado Compose reactivo: la UI cambia en vivo sin reiniciar.
 */
object Prefs {

    enum class ThemeMode(val label: String) {
        SYSTEM("Sistema"), LIGHT("Claro"), DARK("Oscuro")
    }

    /** Política de cookies (valores oficiales de Gecko, network.cookie.cookieBehavior). */
    enum class CookieMode(val label: String, val geckoValue: Int, val detail: String) {
        ALL(
            "Todas (recomendado)",
            ContentBlocking.CookieBehavior.ACCEPT_ALL,
            "Acepta cookies del sitio y de terceros: máxima compatibilidad con " +
                "Cloudflare, logins y carritos de compra."
        ),
        FIRST_PARTY(
            "Solo del sitio",
            ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY,
            "Bloquea las cookies de terceros (anunciantes) y acepta las del " +
                "sitio que visitas. Puede pedir re-login en algún servicio embebido."
        ),
        NONE(
            "Ninguna",
            ContentBlocking.CookieBehavior.ACCEPT_NONE,
            "No se guarda ninguna cookie. Cloudflare y los logins NO funcionarán."
        )
    }

    /** Identidad del navegador frente a los sitios. */
    enum class UaMode(val label: String, val detail: String) {
        CHROME_MOBILE(
            "Chrome móvil real (recomendado)",
            "UA de Chrome para Android: pasa los desafíos anti-bot de Cloudflare " +
                "y evita la versión simplificada de los sitios."
        ),
        FIREFOX_NATIVE(
            "Firefox nativo (Gecko)",
            "El UA que envía GeckoView por defecto: Firefox para Android real."
        ),
        CHROME_DESKTOP(
            "Chrome de escritorio",
            "UA de escritorio: los sitios muestran su versión completa."
        )
    }

    private lateinit var prefs: SharedPreferences

    var theme by mutableStateOf(ThemeMode.SYSTEM)
        private set
    var cookies by mutableStateOf(CookieMode.ALL)
        private set
    var ua by mutableStateOf(UaMode.CHROME_MOBILE)
        private set
    var sites by mutableStateOf(listOf<Site>())
        private set
    var homeUrl by mutableStateOf("")
        private set

    private const val FILE = "sitehub_prefs"
    private const val K_THEME = "theme"
    private const val K_COOKIES = "cookies"
    private const val K_UA = "ua"
    private const val K_SITES = "sites_json"
    private const val K_HOME = "home_url"

    fun init(context: Context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        theme = enum(prefs.getString(K_THEME, null), ThemeMode.SYSTEM)
        cookies = enum(prefs.getString(K_COOKIES, null), CookieMode.ALL)
        ua = enum(prefs.getString(K_UA, null), UaMode.CHROME_MOBILE)
        homeUrl = prefs.getString(K_HOME, null).orEmpty()
        sites = runCatching { parseSites(prefs.getString(K_SITES, null)) }
            .getOrDefault(defaultSites())
    }

    private inline fun <reified E : Enum<E>> enum(raw: String?, fallback: E): E =
        raw?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: fallback

    /** UA efectivo según el modo; null = dejar el nativo de Gecko. */
    fun userAgentOverride(): String? = when (ua) {
        UaMode.CHROME_MOBILE ->
            "Mozilla/5.0 (Linux; Android 17; SM-A102U) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/154.0.8037.58 Mobile Safari/537.36"
        UaMode.FIREFOX_NATIVE -> null
        UaMode.CHROME_DESKTOP ->
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/154.0.8037.58 Safari/537.36"
    }

    fun applyTheme(mode: ThemeMode) {
        theme = mode
        prefs.edit().putString(K_THEME, mode.name).apply()
    }

    /** Las cookies se aplican al reiniciar: Gecko lee su política al crear el runtime. */
    fun applyCookies(mode: CookieMode) {
        cookies = mode
        prefs.edit().putString(K_COOKIES, mode.name).apply()
    }

    fun applyUa(mode: UaMode) {
        ua = mode
        prefs.edit().putString(K_UA, mode.name).apply()
    }

    fun setHome(url: String) {
        homeUrl = url
        prefs.edit().putString(K_HOME, url).apply()
    }

    fun saveSites(list: List<Site>) {
        sites = list
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("name", it.name).put("url", it.url))
        }
        prefs.edit().putString(K_SITES, arr.toString()).apply()
    }

    // ----- Sitios -----

    data class Site(val name: String, val url: String) {
        fun host(): String = runCatching { java.net.URI(url).host ?: url }
            .getOrDefault(url).removePrefix("www.")
    }

    /** Sitios embebidos de fábrica (el primero es la página de inicio). */
    fun defaultSites(): List<Site> = listOf(
        Site("Mangalovers", "https://mangalovers.josenunez.cl/"),
        Site("Google", "https://www.google.com/"),
        Site("YouTube", "https://www.youtube.com/"),
        Site("Wikipedia", "https://es.wikipedia.org/"),
        Site("GitHub", "https://github.com/")
    )

    fun parseSites(raw: String?): List<Site> {
        if (raw.isNullOrBlank()) return defaultSites()
        val arr = JSONArray(raw)
        val out = mutableListOf<Site>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val name = obj.optString("name").trim()
            val url = obj.optString("url").trim()
            if (name.isEmpty() || url.isEmpty()) continue
            val withScheme = if (url.startsWith("http")) url else "https://$url"
            out.add(Site(name, withScheme))
        }
        return out.ifEmpty { defaultSites() }
    }

    fun normalizeUrl(input: String): String {
        val raw = input.trim()
        if (raw.isEmpty()) return homeUrl.ifEmpty { defaultSites().first().url }
        if (raw.startsWith("http://") || raw.startsWith("https://")) return raw
        if (!raw.contains('.') && !raw.contains(':')) {
            // Texto suelto: búsqueda en lugar de URL rota.
            return "https://www.google.com/search?q=" +
                java.net.URLEncoder.encode(raw, "UTF-8")
        }
        return "https://$raw"
    }
}
