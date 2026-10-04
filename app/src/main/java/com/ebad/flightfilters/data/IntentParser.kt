package com.ebad.flightfilters.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Understands a request without any AI: places, dates, budget, stops, trip length, and deal-hunting
 * phrasing. Free AI models are best-effort, so this is the floor the Ask tab always stands on;
 * [mergeAi] then lets a model's answer fill whatever this couldn't.
 *
 * [resolve] maps a place name or code to an IATA code (the app passes the worldwide airport list).
 */
class IntentParser(private val resolve: (String) -> String?, private val today: () -> LocalDate = { LocalDate.now() }) {

    fun parse(text: String): Assistant.Intent {
        val t = " " + text.lowercase(Locale.ENGLISH).replace(Regex("\\s+"), " ").trim() + " "
        val dates = dates(t)
        val dest = place(t, Regex("\\b(?:to|into|in|visit|visiting|fly to)\\s+([\\p{L} .'-]{2,40}?)(?=\\s(?:on|in|for|next|this|under|below|around|from|between|by|with|at|during|before|after|over|max|cheap|direct|nonstop|return|and)\\b|\\s\\d|\\s-|[,.!?]|\\s*$)"))
            ?: codeIn(text)
        val origin = place(t, Regex("\\bfrom\\s+([\\p{L} .'-]{2,40}?)(?=\\s(?:to|on|in|for|next|this|under|below|around|between|by|with|at|during)\\b|\\s\\d|[,.!?]|\\s*$)"))
            ?: place(t, Regex("^\\s([\\p{L}]{3,30}?)\\s(?:to|->|→)\\s"))?.takeIf { it != dest }
        val nights = Regex("\\bfor (\\d{1,2}) (?:nights?|days?)\\b").find(t)?.groupValues?.get(1)?.toIntOrNull()
        val roundTrip = nights != null || Regex("\\b(return|round[ -]?trip|and back)\\b").containsMatchIn(t)
        val dealsWords = Regex("\\b(deals?|error fares?|mistake fares?|promos?|price drops?|anything cheap|cheapest (?:place|destination|countr)|where can i go|bargains?|surprise me|anywhere)\\b")
        val action = when {
            dest == null && dealsWords.containsMatchIn(t) -> "deals"
            dest != null -> "search"
            dates != null || origin != null -> "search"
            else -> "none"
        }
        val (hf, ht) = when {
            "early morning" in t -> 4 to 9
            "morning" in t -> 5 to 12
            "afternoon" in t -> 12 to 18
            "evening" in t || "after work" in t -> 17 to 24
            else -> null to null
        }
        return Assistant.Intent(
            action = action,
            origins = origin?.let { listOf(it) },
            destination = dest,
            dateFrom = dates?.first?.toString(),
            dateTo = dates?.second?.toString(),
            hourFrom = hf,
            hourTo = ht,
            oneWay = if (roundTrip) false else null,
            nightsMin = nights?.let { (it - 1).coerceAtLeast(1) },
            nightsMax = nights?.let { it + 1 },
            flexDays = when {
                Regex("±\\s?(\\d)").find(t) != null -> Regex("±\\s?(\\d)").find(t)!!.groupValues[1].toInt()
                Regex("\\bflexible|give or take|roughly|around\\b").containsMatchIn(t) -> 3
                else -> null
            },
            maxPriceChf = Regex("\\b(?:under|below|less than|max(?:imum)?|up to|budget(?: of)?|cheaper than)\\s*(?:chf|€|eur|fr\\.?)?\\s*(\\d{2,5})").find(t)?.groupValues?.get(1)?.toIntOrNull(),
            stops = if (Regex("\\b(direct|non-?stop)\\b").containsMatchIn(t)) "DIRECT" else null,
            horizonDays = when {
                Regex("\\b(half a year|six months|6 months|next year)\\b").containsMatchIn(t) -> 180
                Regex("\\b(this week|next few days|soon|last[- ]minute)\\b").containsMatchIn(t) -> 14
                Regex("\\b(this month|next month|weeks?)\\b").containsMatchIn(t) -> 30
                Regex("\\b(months|season|spring|summer|autumn|winter)\\b").containsMatchIn(t) -> 90
                else -> null
            },
        )
    }

    /**
     * Combines the built-in reading with a model's JSON, tolerating the field names free models
     * actually use (arrival_airport, departure_date, max_price…). The built-in values win where
     * both exist, because they come from deterministic rules rather than a guess.
     */
    fun mergeAi(local: Assistant.Intent, aiRaw: String?): Assistant.Intent {
        val obj = aiRaw?.let { raw ->
            val s = raw.indexOf('{'); val e = raw.lastIndexOf('}')
            if (s < 0 || e <= s) null else runCatching { json.parseToJsonElement(raw.substring(s, e + 1)) as? JsonObject }.getOrNull()
        }
        val prose = aiRaw?.let { raw -> if (obj == null) raw.trim().removePrefix("```").trim() else null }
        fun str(vararg keys: String): String? = keys.firstNotNullOfOrNull { k ->
            when (val v = obj?.get(k)) {
                is JsonPrimitive -> v.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
                is JsonArray -> (v.firstOrNull() as? JsonPrimitive)?.contentOrNull
                else -> null
            }
        }
        fun int(vararg keys: String): Int? = keys.firstNotNullOfOrNull { k -> (obj?.get(k) as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toInt() } }

        val aiDest = str("destination", "arrival_airport", "arrival", "to", "destination_iata", "arrival_city")?.let { resolve(it) }
        val aiOrigin = str("origins", "origin", "departure_airport", "from", "departure_city")?.let { resolve(it) }
        val aiFrom = str("dateFrom", "departure_date", "date", "start_date", "outbound_date")?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
        val aiTo = str("dateTo", "end_date", "latest_departure", "date_to")?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
        val dest = local.destination ?: aiDest
        val aiAction = str("action")?.lowercase()
        val action = when {
            local.action == "deals" -> "deals"
            dest != null -> "search"
            aiAction in setOf("search", "deals") -> aiAction!!
            local.action == "search" -> "search"
            else -> "none"
        }
        return local.copy(
            reply = str("reply", "message", "answer", "response") ?: prose ?: local.reply,
            action = action,
            destination = dest,
            origins = local.origins ?: aiOrigin?.let { listOf(it) },
            dateFrom = local.dateFrom ?: aiFrom?.takeIf { !it.isBefore(today()) }?.toString(),
            dateTo = local.dateTo ?: aiTo?.toString() ?: if (local.dateFrom == null) aiFrom?.toString() else null,
            maxPriceChf = local.maxPriceChf ?: int("maxPriceChf", "max_price", "budget", "price_max", "maxPrice")?.takeIf { it > 0 },
            stops = local.stops ?: str("stops")?.uppercase(),
            horizonDays = local.horizonDays ?: int("horizonDays"),
        )
    }

    private fun place(t: String, re: Regex): String? {
        for (m in re.findAll(t)) {
            var name = m.groupValues[1].trim()
            // Try the longest phrase first, then trim words off the end ("lisbon portugal" → "lisbon"),
            // then off the front ("visit iceland" → "iceland").
            val tried = mutableListOf<String>()
            while (name.isNotEmpty()) { tried += name; name = name.substringBeforeLast(' ', "") }
            name = m.groupValues[1].trim()
            while (name.contains(' ')) { name = name.substringAfter(' '); tried += name }
            for (n in tried) if (n !in stopPlaces) resolve(n)?.let { return it }
        }
        return null
    }

    private fun codeIn(original: String): String? =
        Regex("\\b([A-Z]{3})\\b").findAll(original).map { it.groupValues[1] }
            .firstOrNull { it !in setOf("CHF", "EUR", "USD") && resolve(it) == it }

    /** A departure window from relative or absolute date phrases, or null when none is mentioned. */
    fun dates(t: String): Pair<LocalDate, LocalDate>? {
        val d = today()
        Regex("(\\d{4})-(\\d{2})-(\\d{2})").findAll(t).map { LocalDate.of(it.groupValues[1].toInt(), it.groupValues[2].toInt(), it.groupValues[3].toInt()) }.toList()
            .takeIf { it.isNotEmpty() }?.let { return it.first() to (it.getOrNull(1) ?: it.first()) }
        dayMonth(t)?.let { return it }
        when {
            " today " in t || "tonight" in t -> return d to d
            "tomorrow" in t -> return d.plusDays(1) to d.plusDays(1)
            "next weekend" in t -> { val f = d.with(TemporalAdjusters.next(DayOfWeek.FRIDAY)).plusWeeks(if (d.dayOfWeek >= DayOfWeek.FRIDAY || d.dayOfWeek == DayOfWeek.MONDAY) 0 else 1); return f to f.plusDays(2) }
            "weekend" in t -> { val f = if (d.dayOfWeek in setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)) d else d.with(TemporalAdjusters.next(DayOfWeek.FRIDAY)); return f to f.plusDays(2).coerceAtLeast(f) }
            "next week" in t -> { val m = d.with(TemporalAdjusters.next(DayOfWeek.MONDAY)); return m to m.plusDays(6) }
            "this week" in t -> return d to d.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            "next month" in t -> { val m = d.plusMonths(1).withDayOfMonth(1); return m to m.with(TemporalAdjusters.lastDayOfMonth()) }
            "this month" in t -> return d to d.with(TemporalAdjusters.lastDayOfMonth())
        }
        DayOfWeek.entries.forEach { dow ->
            val name = dow.name.lowercase()
            if (Regex("\\b$name\\b").containsMatchIn(t)) {
                val base = d.with(TemporalAdjusters.next(dow))
                return if (Regex("\\bnext $name\\b").containsMatchIn(t) && base.isBefore(d.plusDays(7)) && d.dayOfWeek < dow) base.plusWeeks(1) to base.plusWeeks(1)
                else base to base
            }
        }
        Month.entries.forEach { mo ->
            val full = mo.name.lowercase()
            // "may" is also a verb, so it only counts with a preposition or a day number.
            val mentioned = if (mo == Month.MAY) Regex("\\b(?:in|during|early|mid|late|of|\\d{1,2}) may\\b|\\bmay \\d").containsMatchIn(t)
            else Regex("\\b(${full}|${full.take(3)})\\b").containsMatchIn(t)
            if (mentioned) {
                var start = LocalDate.of(d.year, mo, 1)
                if (start.with(TemporalAdjusters.lastDayOfMonth()).isBefore(d)) start = start.plusYears(1)
                val end = start.with(TemporalAdjusters.lastDayOfMonth())
                val from = maxOf(start, d)
                return when {
                    Regex("\\bearly ${full}").containsMatchIn(t) -> from to start.plusDays(9)
                    Regex("\\bmid[- ]${full}").containsMatchIn(t) -> maxOf(start.plusDays(9), d) to start.plusDays(19)
                    Regex("\\blate ${full}").containsMatchIn(t) -> maxOf(start.plusDays(19), d) to end
                    else -> from to end
                }
            }
        }
        listOf("spring" to (3 to 5), "summer" to (6 to 8), "autumn" to (9 to 11), "fall" to (9 to 11), "winter" to (12 to 14)).forEach { (w, r) ->
            if (Regex("\\b$w\\b").containsMatchIn(t)) {
                var s = LocalDate.of(d.year, r.first, 1)
                var e = s.plusMonths((r.second - r.first).toLong()).with(TemporalAdjusters.lastDayOfMonth())
                if (e.isBefore(d)) { s = s.plusYears(1); e = e.plusYears(1) }
                return maxOf(s, d) to e
            }
        }
        return null
    }

    /** "12 nov", "nov 12", "12.11." and "12/11", taken as the next such date. */
    private fun dayMonth(t: String): Pair<LocalDate, LocalDate>? {
        val d = today()
        val months = Month.entries.associateBy { it.name.lowercase().take(3) }
        fun at(day: Int, month: Month): LocalDate? = runCatching {
            var x = LocalDate.of(d.year, month, day); if (x.isBefore(d)) x = x.plusYears(1); x
        }.getOrNull()
        val found = mutableListOf<LocalDate>()
        Regex("\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*").findAll(t).forEach { m ->
            at(m.groupValues[1].toInt(), months.getValue(m.groupValues[2]))?.let(found::add)
        }
        Regex("\\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b").findAll(t).forEach { m ->
            at(m.groupValues[2].toInt(), months.getValue(m.groupValues[1]))?.let(found::add)
        }
        Regex("\\b(\\d{1,2})[./](\\d{1,2})\\.?(?!\\d)").findAll(t).forEach { m ->
            val mo = m.groupValues[2].toInt()
            if (mo in 1..12) at(m.groupValues[1].toInt(), Month.of(mo))?.let(found::add)
        }
        if (found.isEmpty()) return null
        val sorted = found.distinct().sorted()
        return sorted.first() to sorted.last()
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
        private val stopPlaces: Set<String> = setOf(
            "the", "a", "an", "somewhere", "anywhere", "europe", "sun", "the sun", "warm", "a warm place", "sunny", "my", "me",
            "weekend", "the weekend", "spring", "summer", "autumn", "fall", "winter", "the morning", "the evening", "the afternoon",
            "today", "tomorrow", "week", "month", "the week", "the month", "march", "may", "early", "mid", "late",
        ) + Month.entries.map { it.name.lowercase() } + DayOfWeek.entries.map { it.name.lowercase() }
    }
}
