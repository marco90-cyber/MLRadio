package com.marco.mlradio.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegionsTest {
    @Test
    fun italyCitiesAndSpellingsMerge() {
        val raw = listOf(
            "Lombardia" to 10, "Lombardy" to 3, "lombardia" to 2, "Milano" to 5, "Lombardia/Italy" to 1,
            "Roma" to 4, "Lazio" to 6, "Italy" to 12, "Paesino Sconosciuto" to 1, "Sicilia" to 2, "Sicily" to 1,
        )
        val out = Regions.merge(raw, "IT", "Italy")
        assertEquals(listOf("Lazio", "Lombardia", "Sicilia"), out.map { it.name })
        assertEquals(21, out.first { it.name == "Lombardia" }.count)
        assertEquals(true, out.first { it.name == "Lombardia" }.variants.containsAll(listOf("Milano", "Lombardy", "Lombardia/Italy")))
    }

    @Test
    fun otherCountriesDedupeSpelling() {
        val out = Regions.merge(listOf("Bavaria" to 5, "bavaria" to 1, "Germany" to 3, "Berlin" to 2), "DE", "Germany")
        assertEquals(listOf("Bavaria", "Berlin"), out.map { it.name })
        assertEquals(6, out[0].count)
    }

    @Test
    fun countryNameIsNotARegion() {
        assertNull(Regions.canonical("Italia", "IT", "Italy"))
        assertNull(Regions.canonical("France", "FR", "France"))
    }
}
