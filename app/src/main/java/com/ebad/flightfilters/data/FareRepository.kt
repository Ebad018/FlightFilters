package com.ebad.flightfilters.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth

/**
 * Where fares come from, in order of trust:
 *  - Google Flights (live, every airline) for a named destination on specific days;
 *  - Ryanair's fare finder (live, Ryanair only) for "anywhere", calendars and price drops;
 *  - Aviasales cache (optional free token) for extra breadth.
 * Every source fails independently; a scan only errors when all of them do.
 */
class FareRepository(
    private val travelpayouts: TravelpayoutsApi,
    private val serp: SerpApi,
    private val google: GoogleFlights,
    private val ryanair: Ryanair,
    private val feed: DealsFeed,
    private val http: CachedHttp,
) {
    val loops = LoopFinder(ryanair, google)

    /** Google pages are ~2 MB each; a few at a time keeps a phone responsive and Google polite. */
    private val googleGate = Semaphore(3)

    suspend fun scan(query: Query, settings: AppSettings): ScanResult = coroutineScope {
        val origins = query.activeOrigins
        val failed = mutableSetOf<String>()
        var level: String? = null
        val fares: List<Fare> = if (settings.usesSampleData) {
            origins.flatMap { line ->
                query.dests.ifEmpty { listOf(null) }.flatMap { d ->
                    SampleData.fares(
                        listOf(line.iata), d,
                        line.window.from.minusDays(query.flexDays.toLong()),
                        line.window.to.plusDays(query.flexDays.toLong()),
                        query.oneWay, query.nightsMin..query.nightsMax,
                    )
                }
            }
        } else {
            val jobs = mutableListOf<kotlinx.coroutines.Deferred<List<Fare>>>()
            val dests = query.dests.take(MAX_DESTS)
            val nights = query.nightsMin..maxOf(query.nightsMin, query.nightsMax)
            // Google page budget shared by every origin × destination pair, so 3 × 4 can't fire 80 loads.
            val daysPerPair = (GOOGLE_BUDGET / maxOf(1, origins.size * dests.size)).coerceIn(1, MAX_GOOGLE_DAYS)
            for (line in origins) {
                val spanFrom = maxOf(LocalDate.now(), line.window.from.minusDays(query.flexDays.toLong()))
                val spanTo = line.window.to.plusDays(query.flexDays.toLong())
                for (dest in dests) {
                    // Live, all airlines, on days inside the window itself.
                    val days = daysIn(maxOf(LocalDate.now(), line.window.from), line.window.to).let { d ->
                        if (d.size <= daysPerPair) d else d.filterIndexed { i, _ -> i % ((d.size + daysPerPair - 1) / daysPerPair) == 0 }
                    }
                    for (day in days) jobs += async {
                        googleGate.withPermit {
                            runCatching {
                                val ret = if (query.oneWay) null else day.plusDays(((nights.first + nights.last) / 2).toLong())
                                google.search(line.iata, dest, day, ret).also { r -> if (r.priceLevel != null) level = r.priceLevel }.fares
                            }.getOrElse { synchronized(failed) { failed += "${line.iata}→$dest (Google)" }; emptyList() }
                        }
                    }
                    // Ryanair across the whole flex span: day-by-day for one-ways, best combinations for returns.
                    jobs += async {
                        runCatching {
                            if (query.oneWay) monthsBetween(spanFrom, spanTo).flatMap { m -> ryanair.calendar(line.iata, dest, m) }
                            else ryanair.cheapestReturn(line.iata, spanFrom, spanTo, nights, dest)
                        }.getOrDefault(emptyList())
                    }
                }
                if (dests.isEmpty()) jobs += async {
                    runCatching {
                        if (query.oneWay) ryanair.cheapest(line.iata, spanFrom, spanTo)
                        else ryanair.cheapestReturn(line.iata, spanFrom, spanTo, nights)
                    }.getOrElse { synchronized(failed) { failed += line.iata + " (Ryanair)" }; emptyList() }
                }
                if (settings.travelpayoutsToken.isNotBlank()) jobs += async {
                    runCatching {
                        dests.ifEmpty { listOf(null) }.flatMap { d ->
                            monthsBetween(spanFrom, spanTo).flatMap { m ->
                                travelpayouts.pricesForMonth(settings.travelpayoutsToken, line.iata, d, m, query.oneWay, query.filters.stops == Stops.DIRECT)
                            }
                        }
                    }.getOrElse { synchronized(failed) { failed += line.iata + " (Aviasales)" }; emptyList() }
                }
            }
            jobs.awaitAll().flatten()
        }
        if (fares.isEmpty() && failed.isNotEmpty()) {
            throw HttpException(0, "No source answered: ${failed.joinToString()}. Check the connection and try again.")
        }
        val items = Recommender.place(fares, query, settings)
        ScanResult(
            query = query,
            itineraries = items.sortedBy { it.effectiveCostChf },
            notices = Recommender.notices(items, query, settings),
            dayPrices = Recommender.dayPrices(items),
            sources = items.map { it.fare.source }.toSet().ifEmpty { setOf(if (settings.usesSampleData) FareSource.SAMPLE else FareSource.GOOGLE_LIVE) },
            oldestSeenAt = items.minOfOrNull { it.fare.seenAt } ?: System.currentTimeMillis(),
            failedOrigins = failed.toList(),
            priceLevel = level,
        )
    }

    /** Cheapest one-way departures anywhere in the next [days] days from each origin. */
    suspend fun lastMinute(origins: List<String>, dests: List<String>, settings: AppSettings, days: Int): List<Itinerary> = coroutineScope {
        val now = OffsetDateTime.now()
        val until = now.plusDays(days.toLong())
        val targets = dests.take(MAX_DESTS)
        val fares = if (settings.usesSampleData) {
            targets.ifEmpty { listOf(null) }.flatMap { d -> SampleData.fares(origins, d, LocalDate.now(), LocalDate.now().plusDays(days.toLong()), true, 0..0) }
        } else {
            val jobs = mutableListOf<kotlinx.coroutines.Deferred<List<Fare>>>()
            for (o in origins) {
                for (d in targets.ifEmpty { listOf(null) }) jobs += async {
                    runCatching { ryanair.cheapest(o, now.toLocalDate(), until.toLocalDate(), d) }.getOrDefault(emptyList())
                }
                // A named destination may not be a Ryanair route at all: ask Google for every day of the horizon.
                if (targets.isNotEmpty()) {
                    val perPair = (GOOGLE_BUDGET / maxOf(1, origins.size * targets.size)).coerceIn(1, days + 1)
                    for (d in targets) for (day in daysIn(now.toLocalDate(), until.toLocalDate()).take(perPair)) jobs += async {
                        googleGate.withPermit { runCatching { google.search(o, d, day).fares }.getOrDefault(emptyList()) }
                    }
                }
                if (settings.travelpayoutsToken.isNotBlank()) jobs += async {
                    runCatching {
                        targets.ifEmpty { listOf(null) }.flatMap { d ->
                            monthsBetween(now.toLocalDate(), until.toLocalDate()).flatMap { m ->
                                travelpayouts.pricesForMonth(settings.travelpayoutsToken, o, d, m, oneWay = true, directOnly = false)
                            }
                        }
                    }.getOrDefault(emptyList())
                }
            }
            jobs.awaitAll().flatten()
        }
        val target = Query(origins = emptyList(), destinations = targets)
        fares.filter { it.departAt.isAfter(now) && it.departAt.isBefore(until) && target.goesTo(it) }
            .map { Recommender.toItinerary(it, settings, oneWay = true, inWindow = true) }
            .distinctBy { it.fare.key }
            .sortedBy { it.fare.departAt }
    }

    /**
     * Deals: the cheapest Ryanair fare to every destination from every airport in reach, each checked
     * against that route's usual daily price for the month, plus hand-picked promos from Fly4free.
     */
    suspend fun deals(
        origins: List<String>,
        dests: List<String>,
        oneWay: Boolean,
        nights: IntRange,
        settings: AppSettings,
        /** Departure window: any span, from a week to many months ahead. */
        from: LocalDate,
        until: LocalDate,
        /** Words that identify a destination in a promo headline: city and airport names. */
        placeWords: (String) -> List<String>,
    ): DealsResult = coroutineScope {
        val today = maxOf(LocalDate.now(), from)
        val targets = dests.take(MAX_DESTS)
        val promosJob = async { runCatching { feed.europe() } }
        val fares: List<Fare> = if (settings.usesSampleData) {
            targets.ifEmpty { listOf(null) }.flatMap { d -> SampleData.fares(origins, d, today.plusDays(1), until, oneWay, nights) }
        } else {
            val jobs = mutableListOf<kotlinx.coroutines.Deferred<List<Fare>>>()
            for (o in origins) for (d in targets.ifEmpty { listOf(null) }) jobs += async {
                runCatching {
                    if (oneWay) ryanair.cheapest(o, today, until, d) else ryanair.cheapestReturn(o, today, until, nights, d)
                }.getOrDefault(emptyList())
            }
            // Named destinations Ryanair doesn't serve still deserve prices: sample a few dates on Google.
            if (targets.isNotEmpty()) {
                // Probe dates spread evenly across the window, however long it is.
                val span = maxOf(0L, java.time.temporal.ChronoUnit.DAYS.between(today, until))
                val perPair = (GOOGLE_BUDGET / maxOf(1, origins.size * targets.size)).coerceIn(1, 4)
                val probes = (0 until perPair).map { i -> span * (2 * i + 1) / (2 * perPair) }.distinct()
                for (o in origins) for (d in targets) for (off in probes) jobs += async {
                    googleGate.withPermit {
                        runCatching {
                            val day = today.plusDays(off)
                            google.search(o, d, day, if (oneWay) null else day.plusDays(((nights.first + nights.last) / 2).toLong())).fares
                        }.getOrDefault(emptyList())
                    }
                }
            }
            jobs.awaitAll().flatten()
        }
        val target = Query(origins = emptyList(), destinations = targets)
        val items = fares.filter { target.goesTo(it) }.map { Recommender.toItinerary(it, settings, oneWay = oneWay, inWindow = true) }
        // Best fare to each destination across all origins, by trip cost so a cheap fare 3 hours away doesn't always win.
        val perDest = items.groupBy { it.fare.destinationAirport }.mapValues { (_, v) -> v.minBy { it.effectiveCostChf } }.values
        val shortlist = perDest.sortedBy { it.fare.priceChf }.take(DROP_CHECKS)
        val drops = shortlist.map { item ->
            async {
                val f = item.fare
                val typical = if (f.source != FareSource.RYANAIR) null else runCatching {
                    val out = median(ryanair.calendar(f.originAirport, f.destinationAirport, YearMonth.from(f.departAt)).map { it.priceChf })
                    // A return's usual price is a usual day out plus a usual day back.
                    if (f.returnAt == null) out
                    else out?.let { o -> median(ryanair.calendar(f.destinationAirport, f.originAirport, YearMonth.from(f.returnAt)).map { it.priceChf })?.let { o + it } }
                }.getOrNull()
                PriceDrop(item, typical)
            }
        }.awaitAll()
            .sortedWith(compareByDescending<PriceDrop> { it.dropPct }.thenBy { it.itinerary.fare.priceChf })
        // With named destinations there's nothing to compare across countries.
        val countries = if (targets.isNotEmpty()) emptyList() else items.filter { !it.fare.country.isNullOrBlank() }
            .groupBy { it.fare.country!! }
            .map { (c, v) -> CountryLow(c, v.minBy { it.fare.priceChf }, v.map { it.fare.destinationAirport }.distinct().size) }
            .sortedBy { it.cheapest.fare.priceChf }
        val promos = promosJob.await()
        val words = targets.flatMap(placeWords).map { AirportDb.fold(it) }.filter { it.length >= 4 }.distinct()
        DealsResult(
            drops = drops,
            countries = countries,
            promos = promos.getOrDefault(emptyList())
                .filter { p -> words.isEmpty() || AirportDb.fold(p.title + " " + p.summary).let { h -> words.any { it in h } } }
                .sortedWith(compareByDescending<PromoDeal> { it.nearYou }.thenByDescending { it.publishedAt }),
            scannedOrigins = origins,
            fetchedAt = items.minOfOrNull { it.fare.seenAt } ?: System.currentTimeMillis(),
            sources = items.map { it.fare.source }.toSet(),
            promoError = promos.exceptionOrNull()?.let { "Couldn't load promo deals (${it.message ?: "network"})" },
        ).also { if (items.isEmpty() && promos.isFailure) throw HttpException(0, "Neither Ryanair nor Fly4free answered. Check the connection.") }
    }

    /** Live price for one fare: Google Flights first (free), SerpApi only as a fallback when a key is set. */
    suspend fun liveCheck(fare: Fare, settings: AppSettings, cabin: Cabin): LiveCheck {
        val date = fare.departAt.toLocalDate()
        val g = runCatching { google.search(fare.originAirport, fare.destinationAirport, date, fare.returnAt?.toLocalDate()) }
        g.getOrNull()?.let { r ->
            val same = r.fares.filter { it.departAt.toLocalTime() == fare.departAt.toLocalTime() }
            val pick = same.minByOrNull { it.priceChf } ?: r.fares.minByOrNull { it.priceChf }
            if (pick != null) return LiveCheck(
                priceChf = pick.priceChf, airline = Airlines.name(pick.airline), layovers = pick.layovers,
                totalDurationMin = pick.durationMin, priceLevel = r.priceLevel, matched = same.isNotEmpty(),
                url = r.url, checkedAt = pick.seenAt,
            )
        }
        if (settings.serpApiKey.isNotBlank()) {
            return runCatching { serp.check(settings.serpApiKey, fare, cabin) }.getOrElse {
                LiveCheck(null, null, emptyList(), null, null, false, null, System.currentTimeMillis(), it.message ?: "Live check failed")
            }
        }
        return LiveCheck(
            null, null, emptyList(), null, null, false, google.url(fare.originAirport, fare.destinationAirport, date, fare.returnAt?.toLocalDate()),
            System.currentTimeMillis(), g.exceptionOrNull()?.message ?: "Google Flights showed no fares for that day",
        )
    }

    fun clearCache() = http.clear()

    private fun daysIn(from: LocalDate, to: LocalDate): List<LocalDate> =
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()

    private fun monthsBetween(from: LocalDate, to: LocalDate): List<YearMonth> {
        val out = mutableListOf<YearMonth>()
        var m = YearMonth.from(from)
        val end = YearMonth.from(to)
        while (!m.isAfter(end)) { out += m; m = m.plusMonths(1) }
        return out
    }

    private fun median(v: List<Double>): Double? {
        if (v.size < 5) return null
        val s = v.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    companion object {
        private const val MAX_GOOGLE_DAYS = 7
        private const val GOOGLE_BUDGET = 16
        private const val MAX_DESTS = 6
        private const val DROP_CHECKS = 16
    }
}
