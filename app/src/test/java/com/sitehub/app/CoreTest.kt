package com.sitehub.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.sitehub.app.core.AmoClient
import com.sitehub.app.core.Prefs

/**
 * Pruebas unitarias del parser de la tienda AMO y de la normalización de
 * sitios/URLs (lógica pura, sin Android).
 */
class CoreTest {

    @Test
    fun `parseSearchResults extrae campos clave`() {
        val json = """
        {"results":[
          {"guid":"uBlock0@raymondhill.net","slug":"ublock-origin",
           "name":{"en-US":"uBlock Origin","es":"uBlock Origin"},
           "icon_url":"https://amo/icon.png",
           "ratings":{"average":4.7,"count":1000},
           "average_daily_users":6000000},
          {"guid":"sin-slug@x","slug":"",
           "name":{"en-US":"roto"},"icon_url":"",
           "ratings":{"average":0.0},"average_daily_users":1}
        ]}
        """.trimIndent()
        val out = AmoClient.parseSearchResults(json)
        assertEquals(1, out.size)
        val addon = out.first()
        assertEquals("uBlock0@raymondhill.net", addon.guid)
        assertEquals("ublock-origin", addon.slug)
        assertEquals("uBlock Origin", addon.name)
        assertEquals(4.7, addon.rating, 0.001)
        assertEquals(6000000L, addon.users)
    }

    @Test
    fun `parseSearchResults con JSON roto devuelve vacio`() {
        assertTrue(AmoClient.parseSearchResults("{roto").isEmpty())
        assertTrue(AmoClient.parseSearchResults("").isEmpty())
    }

    @Test
    fun `installUrl usa el slug y el canal estable de AMO`() {
        assertEquals(
            "https://addons.mozilla.org/firefox/downloads/latest/ublock-origin/addon.xpi",
            AmoClient.installUrl("ublock-origin")
        )
    }

    @Test
    fun `parseSites completa esquemas y descarta entradas rotas`() {
        val raw = """[{"name":"Mangalovers","url":"mangalovers.josenunez.cl"},
                      {"name":"roto","url":""},
                      {"name":"Google","url":"https://www.google.com/"}]"""
        val sites = Prefs.parseSites(raw)
        assertEquals(2, sites.size)
        assertEquals("https://mangalovers.josenunez.cl", sites[0].url)
        assertEquals("https://www.google.com/", sites[1].url)
    }

    @Test
    fun `normalizeUrl detecta busquedas y completa https`() {
        assertEquals(
            "https://mangalovers.josenunez.cl",
            Prefs.normalizeUrl("mangalovers.josenunez.cl")
        )
        assertTrue(Prefs.normalizeUrl("manga de gatos").startsWith(
            "https://www.google.com/search?q="))
        assertEquals(
            "https://ejemplo.com/x",
            Prefs.normalizeUrl("https://ejemplo.com/x")
        )
    }
}
