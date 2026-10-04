package com.ebad.flightfilters.data

import android.content.Context
import java.text.Normalizer

/**
 * Every airport in the world with scheduled service and an IATA code (OurAirports, public domain,
 * bundled as assets/airports.tsv), plus metro codes like LON and TYO. Loaded once, searched in memory.
 */
class AirportDb(context: Context) {
    data class Entry(val code: String, val name: String, val city: String, val country: String, val rank: Int) {
        val label: String get() = if (city.isNotBlank() && !name.contains(city, ignoreCase = true)) "$city, $name" else name
        internal val folded = fold("$code $name $city $country")
        internal val foldedCity = fold(city)
        internal val foldedCountry = fold(country)
    }

    private val entries: List<Entry> by lazy {
        context.assets.open("airports.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.mapNotNull { line ->
                val p = line.split('\t')
                if (p.size < 5) null else Entry(p[0], p[1], p[2], p[3], p[4].toIntOrNull() ?: 2)
            }.toList()
        }
    }

    private val byCode: Map<String, Entry> by lazy { entries.associateBy { it.code } }

    fun get(code: String): Entry? = byCode[code.uppercase()]

    /** Code match first, then city, then airport name or country; bigger airports first within each tier. */
    fun search(query: String, limit: Int = 12): List<Entry> {
        val q = fold(query.trim())
        if (q.length < 2) return emptyList()
        val up = query.trim().uppercase()
        return entries.asSequence()
            .mapNotNull { e ->
                val tier = when {
                    e.code == up -> 0
                    e.foldedCity.startsWith(q) -> 1
                    e.folded.contains(" $q") || e.folded.startsWith(q) -> 2
                    e.foldedCountry.startsWith(q) -> 3
                    e.folded.contains(q) -> 4
                    else -> return@mapNotNull null
                }
                Triple(tier, e.rank, e)
            }
            .sortedWith(compareBy({ it.first }, { it.second }))
            .take(limit)
            .map { it.third }
            .toList()
    }

    /**
     * A confident match for free text: an exact code, a city or name starting with it, or a country
     * (its biggest airport). Loose substring hits return null so "sun" never becomes Sundsvall.
     */
    fun bestMatch(text: String): String? {
        val t = text.trim()
        if (t.length == 3 && t.all { it.isLetter() }) get(t)?.let { return it.code }
        val q = fold(t)
        if (q.length < 3) return null
        val hits = search(t, 20)
        return (hits.firstOrNull { it.foldedCity == q }
            ?: hits.firstOrNull { it.foldedCity.startsWith(q) }
            ?: hits.firstOrNull { it.folded.contains(" $q") }
            ?: hits.firstOrNull { it.foldedCountry == q })?.code
    }

    companion object {
        private val marks = Regex("\\p{Mn}+")
        fun fold(s: String): String = marks.replace(Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD), "")

        @Volatile private var instance: AirportDb? = null
        fun get(context: Context): AirportDb = instance ?: synchronized(this) {
            instance ?: AirportDb(context.applicationContext).also { instance = it }
        }
    }
}
