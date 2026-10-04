package com.ebad.flightfilters.data

data class Airport(
    val code: String,
    val city: String,
    val name: String,
    /** Typical door-to-terminal minutes from central Basel; editable in Settings. */
    val groundMinutes: Int,
)

object Airports {
    /** Airports within ground range of Basel, in rough order of convenience. */
    val nearby = listOf(
        Airport("BSL", "Basel", "EuroAirport Basel-Mulhouse", 25),
        Airport("ZRH", "Zürich", "Zürich Airport", 75),
        Airport("SXB", "Strasbourg", "Strasbourg-Entzheim", 95),
        Airport("BRN", "Bern", "Bern-Belp", 80),
        Airport("FKB", "Karlsruhe", "Karlsruhe/Baden-Baden", 110),
        Airport("STR", "Stuttgart", "Stuttgart Airport", 160),
        Airport("GVA", "Geneva", "Geneva Airport", 170),
        Airport("FRA", "Frankfurt", "Frankfurt Airport", 190),
        Airport("FMM", "Memmingen", "Memmingen (Allgäu)", 170),
    )

    /** Frequent leisure destinations from the region, for one-tap picking. City codes where Aviasales uses them. */
    val destinations = listOf(
        Airport("LIS", "Lisbon", "Lisbon", 0),
        Airport("BCN", "Barcelona", "Barcelona", 0),
        Airport("OPO", "Porto", "Porto", 0),
        Airport("ATH", "Athens", "Athens", 0),
        Airport("LON", "London", "London (all)", 0),
        Airport("NAP", "Naples", "Naples", 0),
        Airport("PMI", "Palma", "Palma de Mallorca", 0),
        Airport("AGP", "Málaga", "Málaga", 0),
        Airport("CPH", "Copenhagen", "Copenhagen", 0),
        Airport("BER", "Berlin", "Berlin", 0),
        Airport("MAD", "Madrid", "Madrid", 0),
        Airport("RAK", "Marrakesh", "Marrakesh", 0),
    )

    val defaultGroundMinutes: Map<String, Int> = nearby.associate { it.code to it.groundMinutes }

    fun byCode(code: String): Airport? =
        nearby.firstOrNull { it.code == code } ?: destinations.firstOrNull { it.code == code }

    /** Worldwide fallback, wired to [AirportDb] at app start. */
    @Volatile var worldCity: ((String) -> String?)? = null

    fun cityOf(code: String): String = byCode(code)?.city ?: worldCity?.invoke(code)?.takeIf { it.isNotBlank() } ?: code
}

object Airlines {
    private val names = mapOf(
        "U2" to "easyJet", "EC" to "easyJet Europe", "DS" to "easyJet Switzerland",
        "LX" to "SWISS", "WK" to "Edelweiss", "2L" to "Helvetic",
        "FR" to "Ryanair", "W6" to "Wizz Air", "W4" to "Wizz Air Malta", "VY" to "Vueling",
        "LH" to "Lufthansa", "EW" to "Eurowings", "DE" to "Condor", "X3" to "TUIfly",
        "AF" to "Air France", "KL" to "KLM", "HV" to "Transavia", "TO" to "Transavia France",
        "TP" to "TAP Air Portugal", "IB" to "Iberia", "UX" to "Air Europa", "BA" to "British Airways",
        "EI" to "Aer Lingus", "SN" to "Brussels Airlines", "OS" to "Austrian", "AZ" to "ITA Airways",
        "A3" to "Aegean", "TK" to "Turkish Airlines", "PC" to "Pegasus", "XQ" to "SunExpress",
        "SK" to "SAS", "AY" to "Finnair", "BT" to "airBaltic", "LO" to "LOT", "V7" to "Volotea",
        "EK" to "Emirates", "QR" to "Qatar Airways", "AT" to "Royal Air Maroc", "BJ" to "Nouvelair",
    )

    /** Carriers commonly seen from the Basel region, for the include/exclude picker. */
    val common = listOf("U2", "LX", "FR", "W6", "VY", "LH", "EW", "AF", "KL", "TP", "BA", "TK", "PC", "HV", "WK", "V7")

    fun name(code: String): String = names[code] ?: code

    /** Google names carriers in words; map known ones back to codes so filters and names line up. */
    fun codeFor(name: String): String =
        names.entries.firstOrNull { it.value.equals(name, ignoreCase = true) }?.key ?: name
}
