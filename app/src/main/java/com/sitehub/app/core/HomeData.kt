package com.sitehub.app.core

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** Favoritos + historial reciente del Home (persistente, con datos semilla). */
object HomeData {
    data class Entry(val url: String, val title: String, val ts: Long = 0L)

    private const val FILE = "home_data"
    private const val K_FAV = "favorites"
    private const val K_REC = "recent"
    private lateinit var prefs: SharedPreferences

    var favorites by mutableStateOf(listOf<Entry>())
        private set
    var recent by mutableStateOf(listOf<Entry>())
        private set

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        favorites = load(K_FAV).ifEmpty { seedFavorites() }
        recent = load(K_REC).ifEmpty { seedRecent() }
        persist()
    }

    private fun seedFavorites() = listOf(
        Entry("https://www.google.com", "Google"),
        Entry("https://es.wikipedia.org", "Wikipedia"),
        Entry("https://www.youtube.com", "YouTube"),
        Entry("https://github.com", "GitHub"),
        Entry("https://addons.mozilla.org", "Extensiones AMO")
    )
    private fun seedRecent() = listOf(
        Entry("https://mangalovers.com", "Mangalovers"),
        Entry("https://es.wikipedia.org", "Wikipedia"),
        Entry("https://news.ycombinator.com", "Hacker News")
    )

    private fun load(key: String): List<Entry> = runCatching {
        val arr = JSONArray(prefs.getString(key, "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Entry(o.getString("url"), o.optString("title", o.getString("url")), o.optLong("ts"))
        }
    }.getOrDefault(emptyList())

    private fun dump(list: List<Entry>): String {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("url", it.url).put("title", it.title).put("ts", it.ts)) }
        return arr.toString()
    }
    private fun persist() {
        prefs.edit().putString(K_FAV, dump(favorites)).putString(K_REC, dump(recent)).apply()
    }

    fun prettyHost(url: String): String = runCatching {
        val h = Uri.parse(url).host ?: url
        h.removePrefix("www.").substringBefore('.').replaceFirstChar { it.uppercase() }
    }.getOrDefault(url)

    fun addRecent(url: String, title: String? = null) {
        if (!url.startsWith("http")) return
        val t = title?.takeIf { it.isNotBlank() } ?: prettyHost(url)
        recent = (listOf(Entry(url, t, System.currentTimeMillis())) + recent.filter { it.url != url }).take(24)
        persist()
    }
    fun removeRecent(url: String) { recent = recent.filter { it.url != url }; persist() }
    fun clearRecent() { recent = emptyList(); persist() }

    fun isFavorite(url: String) = favorites.any { it.url == url }
    fun toggleFavorite(url: String, title: String? = null) {
        favorites = if (isFavorite(url)) favorites.filter { it.url != url }
        else listOf(Entry(url, title?.takeIf { it.isNotBlank() } ?: prettyHost(url))) + favorites
        persist()
    }
    fun removeFavorite(url: String) { favorites = favorites.filter { it.url != url }; persist() }
}
