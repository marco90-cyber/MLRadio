package com.marco.mlradio.data

/** Continente in cui raggruppare i paesi nella schermata Esplora. */
data class Continent(val id: String, val name: String, val emoji: String)

/**
 * Suddivisione dei paesi (codice ISO 3166-1 alpha-2) per continente.
 * Le Americhe sono divise in Nord/Centro (con i Caraibi) e Sud, così gli elenchi restano corti.
 * Cipro sta in Europa (Unione europea), Turchia e Caucaso in Asia.
 */
object Continents {
    val EUROPE = Continent("eu", "Europa", "🌍")
    val ASIA = Continent("as", "Asia", "🌏")
    val AFRICA = Continent("af", "Africa", "🌍")
    val NORTH_AMERICA = Continent("na", "America del Nord e Centrale", "🌎")
    val SOUTH_AMERICA = Continent("sa", "America del Sud", "🌎")
    val OCEANIA = Continent("oc", "Oceania", "🌏")
    val OTHER = Continent("xx", "Altri territori", "🏳️")

    /** Ordine di visualizzazione. */
    val all = listOf(EUROPE, ASIA, AFRICA, NORTH_AMERICA, SOUTH_AMERICA, OCEANIA, OTHER)

    internal val table: Map<Continent, String> = mapOf(
        EUROPE to "AD AL AT AX BA BE BG BY CH CY CZ DE DK EE ES FI FO FR GB GG GI GR HR HU IE IM IS IT JE LI LT LU LV " +
            "MC MD ME MK MT NL NO PL PT RO RS RU SE SI SJ SK SM UA VA XK",
        ASIA to "AE AF AM AZ BD BH BN BT CN GE HK ID IL IN IO IQ IR JO JP KG KH KP KR KW KZ LA LB LK MM MN MO MV MY " +
            "NP OM PH PK PS QA SA SG SY TH TJ TL TM TR TW UZ VN YE",
        AFRICA to "AO BF BI BJ BW CD CF CG CI CM CV DJ DZ EG EH ER ET GA GH GM GN GQ GW KE KM LR LS LY MA MG ML MR MU " +
            "MW MZ NA NE NG RE RW SC SD SH SL SN SO SS ST SZ TD TG TN TZ UG YT ZA ZM ZW",
        NORTH_AMERICA to "AG AI AW BB BL BM BQ BS BZ CA CR CU CW DM DO GD GL GP GT HN HT JM KN KY LC MF MQ MS MX NI PA " +
            "PM PR SV SX TC TT UM US VC VG VI",
        SOUTH_AMERICA to "AR BO BR CL CO EC FK GF GS GY PE PY SR UY VE",
        OCEANIA to "AS AU CK FJ FM GU KI MH MP NC NF NR NU NZ PF PG PN PW SB TK TO TV VU WF",
    )

    private val byCode: Map<String, Continent> by lazy {
        val m = HashMap<String, Continent>()
        for ((c, codes) in table) for (code in codes.split(' ').filter { it.isNotBlank() }) m[code] = c
        m
    }

    fun of(code: String): Continent = byCode[code.uppercase()] ?: OTHER

    fun byId(id: String): Continent? = all.firstOrNull { it.id == id }
}
