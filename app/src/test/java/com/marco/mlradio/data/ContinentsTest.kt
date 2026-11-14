package com.marco.mlradio.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinentsTest {
    @Test
    fun everyCodeInOneContinentOnly() {
        val seen = HashMap<String, String>()
        for ((c, codes) in Continents.table) {
            for (code in codes.split(' ').filter { it.isNotBlank() }) {
                assertEquals("codice non valido: $code", 2, code.length)
                val prev = seen.put(code, c.name)
                assertTrue("$code è sia in $prev sia in ${c.name}", prev == null)
            }
        }
    }

    @Test
    fun knownCountries() {
        assertEquals(Continents.EUROPE, Continents.of("IT"))
        assertEquals(Continents.EUROPE, Continents.of("de"))
        assertEquals(Continents.SOUTH_AMERICA, Continents.of("BR"))
        assertEquals(Continents.NORTH_AMERICA, Continents.of("US"))
        assertEquals(Continents.ASIA, Continents.of("JP"))
        assertEquals(Continents.OCEANIA, Continents.of("AU"))
        assertEquals(Continents.AFRICA, Continents.of("EG"))
        assertEquals(Continents.OTHER, Continents.of("AQ"))
    }

    @Test
    fun duplicateCountryCodesAreMerged() {
        val json = """[
            {"name":"Italy","iso_3166_1":"IT","stationcount":900},
            {"name":"Italia","iso_3166_1":"it","stationcount":12},
            {"name":"Germany","iso_3166_1":"DE","stationcount":1500},
            {"name":"Nowhere","iso_3166_1":"","stationcount":5},
            {"name":"Empty","iso_3166_1":"FR","stationcount":0}
        ]"""
        val list = RadioApi.parseCountries(json, "IT")
        assertEquals(listOf("IT", "DE"), list.map { it.code })
        val it = list.first()
        assertEquals(912, it.count)
        assertEquals("Italy", it.name)
        assertEquals(listOf("Italy", "Italia"), it.names)
        assertEquals(list.size, list.map { it.code }.toSet().size)
    }
}
