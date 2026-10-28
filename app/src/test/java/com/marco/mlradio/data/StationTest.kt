package com.marco.mlradio.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StationTest {
    private fun st(uuid: String, name: String, url: String, cc: String = "IT", votes: Int = 0, online: Boolean = true) =
        Station(uuid = uuid, name = name, url = url, countryCode = cc, votes = votes, online = online)

    @Test
    fun parsesApiJson() {
        val json = """[
            {"stationuuid":"a1","name":"  Radio  Uno ","url":"http://x/1","url_resolved":"http://x/r1",
             "countrycode":"it","bitrate":128,"codec":"MP3","hls":0,"lastcheckok":1,"votes":5},
            {"stationuuid":"","name":"Senza id","url":"http://x/2"},
            {"stationuuid":"b2","name":"Offline","url":"http://x/3","lastcheckok":0}
        ]"""
        val list = StationParser.parseList(json)
        assertEquals(2, list.size)
        assertEquals("Radio Uno", list[0].name)
        assertEquals("http://x/r1", list[0].url)
        assertEquals("IT", list[0].countryCode)
        assertTrue(list[0].online)
        assertTrue(!list[1].online)
    }

    @Test
    fun missingEssentialsGiveNull() {
        assertNull(StationParser.fromApi(org.json.JSONObject("""{"stationuuid":"a","name":"x"}""")))
    }

    @Test
    fun dedupeGroupsSameNameAndCountry() {
        val list = listOf(
            st("1", "Radio Uno", "http://a", votes = 1),
            st("2", "radio  uno!", "http://b", votes = 9),
            st("3", "Radio Uno", "http://c", cc = "FR"),
        )
        val out = StationParser.dedupe(list)
        assertEquals(2, out.size)
        assertEquals("2", out[0].uuid)
        assertEquals(listOf("http://a"), out[0].alts)
        assertEquals("3", out[1].uuid)
    }

    @Test
    fun dedupePrefersOnline() {
        val out = StationParser.dedupe(listOf(st("1", "R", "http://a", votes = 99, online = false), st("2", "R", "http://b")))
        assertEquals(1, out.size)
        assertEquals("2", out[0].uuid)
    }

    @Test
    fun offlineGoLast() {
        val out = StationParser.sortOnlineFirst(
            listOf(st("1", "A", "u", online = false), st("2", "B", "u"), st("3", "C", "u", online = false), st("4", "D", "u"))
        )
        assertEquals(listOf("2", "4", "1", "3"), out.map { it.uuid })
    }

    @Test
    fun jsonRoundTrip() {
        val s = st("x", "Nome", "http://a").copy(alts = listOf("http://b"), custom = true, favicon = "http://l.png", bitrate = 64)
        val back = Station.fromJsonString(s.toJson().toString())
        assertNotNull(back)
        assertEquals(s, back)
    }
}
