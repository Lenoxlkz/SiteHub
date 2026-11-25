package com.sitehub.app.core

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

/**
 * Cliente de la tienda oficial de extensiones de Firefox (addons.mozilla.org):
 * API pública v4 de búsqueda, descargable por HTTP estándar de la app (sin
 * depender de la red interna del motor). Es una API real documentada.
 */
object AmoClient {

    private const val SEARCH_BASE =
        "https://services.addons.mozilla.org/api/v4/addons/search/"

    const val UA = "Mozilla/5.0 (Android; Mobile; rv:141.0) Gecko/141.0 Firefox/141.0"

    /** Resultado de búsqueda de la tienda AMO. */
    data class AmoAddon(
        val guid: String,
        val slug: String,
        val name: String,
        val iconUrl: String,
        val rating: Double,
        val users: Long
    )

    fun searchUrl(query: String): String =
        SEARCH_BASE + "?q=" + URLEncoder.encode(query, "UTF-8") +
            "&app=android&type=extension&sort=relevance&page_size=12"

    /** URL de descarga estable del paquete .xpi más reciente compatible. */
    fun installUrl(slug: String): String =
        "https://addons.mozilla.org/firefox/downloads/latest/" +
            URLEncoder.encode(slug, "UTF-8") + "/addon.xpi"

    /** Parser puro del JSON de la API v4 (probado con tests unitarios). */
    fun parseSearchResults(json: String): List<AmoAddon> = runCatching {
        val root = JSONObject(json)
        val results = root.optJSONArray("results") ?: return emptyList()
        val out = mutableListOf<AmoAddon>()
        for (i in 0 until results.length()) {
            val obj = results.optJSONObject(i) ?: continue
            val guid = obj.optString("guid", "").trim()
            val slug = obj.optString("slug", "").trim()
            if (guid.isEmpty() || slug.isEmpty()) continue
            out.add(
                AmoAddon(
                    guid = guid,
                    slug = slug,
                    name = localizedString(obj.opt("name")).ifBlank { slug },
                    iconUrl = obj.optString("icon_url", ""),
                    // Campos REALES de la API v4 (verificados en vivo):
                    // ratings.average y average_daily_users. Los anteriores
                    // (average_rating / daily_users) no existen -> rating 0 y
                    // "usuarios" mostraba las descargas semanales.
                    rating = obj.optJSONObject("ratings")?.optDouble("average", 0.0) ?: 0.0,
                    users = obj.optLong("average_daily_users", 0L)
                )
            )
        }
        out.take(12)
    }.getOrDefault(emptyList())

    /** La API v4 devuelve textos localizados como {"en-US": "..."} — se aplana. */
    private fun localizedString(value: Any?): String = when (value) {
        is String -> value
        is JSONObject -> {
            val preferred = listOf("es", "es-ES", "en-US", "en")
            preferred.firstOrNull { !value.optString(it, "").isBlank() }
                ?.let { value.optString(it) }
                ?: value.keys().asSequence().firstOrNull()?.let { value.optString(it) } ?: ""
        }
        else -> ""
    }

    /** Busca extensiones en la tienda por texto (hasta 12 resultados). */
    fun search(query: String): Result<List<AmoAddon>> = runCatching {
        val clean = query.trim()
        if (clean.isEmpty()) error("Escribe el nombre de la extensión a buscar")
        val conn = URL(searchUrl(clean)).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Accept", "application/json")
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("La tienda respondió HTTP $code al buscar")
            val body = conn.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)
            val items = parseSearchResults(body)
            if (items.isEmpty()) error("La tienda no devolvió resultados para «$clean»")
            items
        } finally {
            conn.disconnect()
        }
    }
}
