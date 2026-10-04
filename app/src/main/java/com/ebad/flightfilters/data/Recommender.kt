package com.ebad.flightfilters.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/** Turns raw fares into ranked itineraries and the "this would save you…" notices. */
object Recommender {
    private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

    fun place(fares: List<Fare>, query: Query, settings: AppSettings): List<Itinerary> {
        val byOrigin = query.activeOrigins.associateBy { it.iata }
        return fares.mapNotNull { fare ->
            val line = byOrigin[fare.origin] ?: byOrigin[fare.originAirport] ?: return@mapNotNull null
            val day = fare.departAt.toLocalDate()
            val w = line.window
            if (day.isBefore(w.from.minusDays(query.flexDays.toLong())) || day.isAfter(w.to.plusDays(query.flexDays.toLong()))) return@mapNotNull null
            if (!w.containsHour(fare.departAt)) return@mapNotNull null
            // Only where the plan is going: a Stockholm search never shows a Barcelona fare.
            if (!query.goesTo(fare)) return@mapNotNull null
            if (!passes(fare, query)) return@mapNotNull null
            toItinerary(fare, settings, query.oneWay, w.contains(fare.departAt))
        }.distinctBy { it.fare.key }
    }

    fun passes(fare: Fare, query: Query): Boolean {
        val f = query.filters
        if (fare.transfers > f.stops.maxTransfers || fare.returnTransfers > f.stops.maxTransfers) return false
        if (f.maxPriceChf != null && fare.priceChf > f.maxPriceChf) return false
        if (f.airlinesInclude.isNotEmpty() && fare.airline !in f.airlinesInclude) return false
        if (fare.airline in f.airlinesExclude) return false
        if (f.maxDurationHours != null && fare.durationMin > f.maxDurationHours * 60) return false
        if (!query.oneWay) {
            val ret = fare.returnAt ?: return false
            val nights = java.time.temporal.ChronoUnit.DAYS.between(fare.departAt.toLocalDate(), ret.toLocalDate())
            if (nights < query.nightsMin || nights > query.nightsMax) return false
        }
        return true
    }

    fun toItinerary(fare: Fare, settings: AppSettings, oneWay: Boolean, inWindow: Boolean): Itinerary {
        val legs = if (oneWay) 1 else 2
        val ground = settings.ground(fare.originAirport) * legs
        val minutes = fare.durationMin * legs + ground
        val cost = fare.priceChf + settings.valueOfTimeChfPerHour * minutes / 60.0
        return Itinerary(fare, ground, cost, inWindow)
    }

    fun notices(items: List<Itinerary>, query: Query, settings: AppSettings): List<Notice> {
        val inWindow = items.filter { it.inWindow }
        val base = inWindow.minByOrNull { it.effectiveCostChf }
        if (base == null) {
            val nearest = items.minByOrNull { it.effectiveCostChf } ?: return emptyList()
            return listOf(
                Notice(
                    NoticeKind.CHEAPER_DATE,
                    "Nothing in your window. Nearest: ${nearest.fare.departAt.format(dayFmt)}",
                    "${chf(nearest.fare.priceChf)} from ${nearest.fare.originAirport}, ${query.flexDays} days of flex scanned",
                    nearest,
                )
            )
        }
        val dest = base.fare.destination
        val sameDest = items.filter { it.fare.destination == dest }
        val threshold = max(10.0, base.fare.priceChf * 0.05)
        val out = mutableListOf<Notice>()

        // 1. A day outside the window that is genuinely cheaper once time is counted too.
        sameDest.filter { !it.inWindow && it.effectiveCostChf < base.effectiveCostChf - threshold }
            .minByOrNull { it.effectiveCostChf }?.let { alt ->
                val save = base.fare.priceChf - alt.fare.priceChf
                val via = if (alt.fare.originAirport != base.fare.originAirport) " from ${alt.fare.originAirport}" else ""
                out += Notice(
                    NoticeKind.CHEAPER_DATE,
                    "Leave ${alt.fare.departAt.format(dayFmt)}$via instead",
                    if (save > 0) "Saves ${chf(save)} on the ticket compared with your best in-window fare"
                    else "Ticket costs about the same, but you'd spend ${hm(base.totalMin - alt.totalMin)} less travelling",
                    alt,
                )
            }

        // 2. The winner is not the home airport: say what the trip to it costs and why it still wins.
        val home = settings.homeIata
        if (base.fare.originAirport != home) {
            inWindow.filter { it.fare.originAirport == home && it.fare.destination == dest }
                .minByOrNull { it.effectiveCostChf }?.let { homeBest ->
                    val save = homeBest.effectiveCostChf - base.effectiveCostChf
                    out += Notice(
                        NoticeKind.OTHER_ORIGIN,
                        "${Airports.cityOf(base.fare.originAirport)} beats $home by ${chf(save)}",
                        "That already includes ${hm(base.groundMin)} getting there, at CHF ${settings.valueOfTimeChfPerHour}/h. $home's best is ${chf(homeBest.fare.priceChf)}.",
                        base,
                    )
                }
        }

        // 3. Faster for a premium that the owner's own time value would pay for.
        sameDest.filter { it.inWindow && it.fare.key != base.fare.key }
            .filter { base.totalMin - it.totalMin >= 60 }
            .filter { it.fare.priceChf - base.fare.priceChf <= settings.valueOfTimeChfPerHour * (base.totalMin - it.totalMin) / 60.0 }
            .maxByOrNull { base.totalMin - it.totalMin }?.let { fast ->
                val premium = fast.fare.priceChf - base.fare.priceChf
                out += Notice(
                    NoticeKind.FASTER,
                    "${hm(base.totalMin - fast.totalMin)} faster for ${if (premium >= 0) "+" else "−"}${chf(kotlin.math.abs(premium))}",
                    "${Airlines.name(fast.fare.airline)}, ${stopsLabel(fast.fare.transfers)}, from ${fast.fare.originAirport}",
                    fast,
                )
            }
        return out
    }

    fun dayPrices(items: List<Itinerary>): Map<LocalDate, Double> =
        items.groupBy { it.fare.departAt.toLocalDate() }
            .mapValues { (_, v) -> v.minOf { it.fare.priceChf } }
            .toSortedMap()

    fun chf(v: Double): String = "CHF ${v.roundToInt()}"

    fun hm(minutes: Int): String {
        val m = max(0, minutes)
        return if (m >= 60) "${m / 60} h ${"%02d".format(m % 60)}" else "$m min"
    }

    fun stopsLabel(n: Int): String = when (n) { 0 -> "direct"; 1 -> "1 change"; else -> "$n changes" }
}
