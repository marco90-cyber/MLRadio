package com.marco.mlradio.data

import java.text.Normalizer
import java.util.Locale

/**
 * Pulizia delle regioni di Radio Browser: il campo «state» è scritto a mano da chi inserisce la radio,
 * quindi contiene doppioni (Lombardia / Lombardy / lombardia), città (Milano, Roma) e il nome del paese.
 * Per l'Italia ogni voce viene ricondotta a una delle 20 regioni; per gli altri paesi si uniscono
 * le scritture uguali a meno di maiuscole, accenti e punteggiatura.
 */
object Regions {

    fun key(s: String): String =
        Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]+"), "")

    private val italy: Map<String, String> by lazy {
        val table = mapOf(
            "Abruzzo" to "abruzzi, l'aquila, aquila, chieti, pescara, teramo, lanciano, avezzano, vasto, sulmona, ortona",
            "Basilicata" to "lucania, potenza, matera, melfi, policoro",
            "Calabria" to "kalabrien, catanzaro, cosenza, crotone, reggio calabria, vibo valentia, lamezia terme, rossano, corigliano",
            "Campania" to "kampanien, napoli, naples, neapel, salerno, caserta, avellino, benevento, torre del greco, pozzuoli, " +
                "ischia, capri, sorrento, castellammare di stabia, battipaglia, nola, aversa, giugliano",
            "Emilia-Romagna" to "emilia, romagna, bologna, modena, parma, reggio emilia, reggio nell'emilia, piacenza, ferrara, " +
                "ravenna, forli, forli cesena, forlì-cesena, cesena, rimini, imola, carpi, riccione, faenza, sassuolo",
            "Friuli-Venezia Giulia" to "friuli, fvg, friaul, friuli venezia giulia, trieste, udine, pordenone, gorizia, monfalcone",
            "Lazio" to "latium, roma, rome, rom, latina, frosinone, viterbo, rieti, fiumicino, civitavecchia, tivoli, ostia, " +
                "guidonia, aprilia, pomezia, velletri, anzio, cassino",
            "Liguria" to "ligurien, genova, genoa, genua, la spezia, spezia, savona, imperia, sanremo, chiavari, rapallo",
            "Lombardia" to "lombardy, lombardei, lombardie, milano, milan, mailand, bergamo, brescia, como, cremona, lecco, lodi, " +
                "mantova, mantua, monza, monza e brianza, monza brianza, brianza, pavia, sondrio, varese, busto arsizio, " +
                "legnano, gallarate, vigevano, sesto san giovanni, cinisello balsamo, crema, saronno",
            "Marche" to "the marches, marken, ancona, pesaro, urbino, pesaro e urbino, pesaro urbino, macerata, fermo, " +
                "ascoli piceno, ascoli, san benedetto del tronto, senigallia, civitanova marche, fano, jesi",
            "Molise" to "campobasso, isernia, termoli",
            "Piemonte" to "piedmont, piemont, torino, turin, alessandria, asti, biella, cuneo, novara, verbania, " +
                "verbano cusio ossola, vco, vercelli, alba, ivrea, moncalieri, pinerolo",
            "Puglia" to "apulia, apulien, pouilles, bari, barletta, andria, trani, barletta andria trani, bat, brindisi, foggia, " +
                "lecce, taranto, salento, gargano, altamura, molfetta, monopoli, bitonto, manfredonia, cerignola",
            "Sardegna" to "sardinia, sardinien, sardaigne, cagliari, sassari, nuoro, oristano, olbia, alghero, carbonia, " +
                "sud sardegna, gallura, ogliastra, olbia tempio, iglesias, quartu",
            "Sicilia" to "sicily, sizilien, sicile, palermo, catania, messina, siracusa, syracuse, ragusa, trapani, agrigento, " +
                "caltanissetta, enna, marsala, gela, vittoria, acireale, modica, bagheria, mazara del vallo",
            "Toscana" to "tuscany, toskana, toscane, firenze, florence, florenz, pisa, livorno, lucca, arezzo, siena, grosseto, " +
                "massa, carrara, massa carrara, pistoia, prato, viareggio, empoli, versilia, piombino",
            "Trentino-Alto Adige" to "trentino alto adige, trentino südtirol, trentino sudtirol, trentino, alto adige, südtirol, " +
                "sudtirol, south tyrol, bolzano, bozen, trento, merano, meran, bressanone, brixen, rovereto",
            "Umbria" to "umbrien, perugia, terni, foligno, assisi, spoleto, citta di castello, orvieto, gubbio",
            "Valle d'Aosta" to "valle d aosta, val d'aosta, valdaosta, aosta valley, aostatal, vallee d'aoste, aosta, aoste",
            "Veneto" to "venetien, venezia, venice, venedig, verona, padova, padua, vicenza, treviso, rovigo, belluno, mestre, " +
                "bassano del grappa, jesolo, chioggia, san dona di piave, schio, castelfranco veneto",
        )
        val m = HashMap<String, String>()
        for ((region, aliases) in table) {
            m[key(region)] = region
            for (a in aliases.split(',')) m[key(a.trim())] = region
        }
        m
    }

    private val countryWords = setOf("italy", "italia", "italien", "italie", "nazionale", "national", "nationwide")

    /** Nome della regione «pulito», oppure null se la voce non è una regione utilizzabile. */
    fun canonical(raw: String, countryCode: String, countryName: String): String? {
        val t = raw.trim()
        if (t.length < 2) return null
        val parts = t.split('/', ',', ';', '(', ')', '-', '|').map { it.trim() }.filter { it.length >= 2 }
        if (countryCode.equals("IT", ignoreCase = true)) {
            italy[key(t)]?.let { return it }
            for (p in parts) italy[key(p)]?.let { return it }
            // le città minori e le voci non riconosciute restano solo in «Tutto il paese»
            return null
        }
        val first = parts.firstOrNull() ?: return null
        val k = key(first)
        if (k.isEmpty() || k == key(countryName) || k in countryWords) return null
        return first.replaceFirstChar { it.titlecase(Locale.ROOT) }
    }

    /**
     * Unisce le voci grezze (nome, numero di radio) in regioni senza doppioni. Ogni regione ricorda le
     * scritture originali, che servono per cercarne le radio sul server.
     */
    fun merge(raw: List<Pair<String, Int>>, countryCode: String, countryName: String): List<RegionInfo> {
        class Acc(var count: Int = 0, val spellings: HashMap<String, Int> = HashMap(), val variants: LinkedHashMap<String, Int> = LinkedHashMap())
        val groups = LinkedHashMap<String, Acc>()
        for ((name, count) in raw) {
            if (count <= 0) continue
            val c = canonical(name, countryCode, countryName) ?: continue
            val acc = groups.getOrPut(key(c)) { Acc() }
            acc.count += count
            acc.spellings[c] = (acc.spellings[c] ?: 0) + count
            acc.variants[name] = (acc.variants[name] ?: 0) + count
        }
        val collator = java.text.Collator.getInstance(Locale.ITALIAN)
        return groups.values.map { a ->
            RegionInfo(
                name = a.spellings.maxByOrNull { it.value }!!.key,
                count = a.count,
                variants = a.variants.entries.sortedByDescending { it.value }.map { it.key },
            )
        }.sortedWith { x, y -> collator.compare(x.name, y.name) }
    }
}
