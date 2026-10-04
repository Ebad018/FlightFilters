package com.ebad.flightfilters.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class LoopRequest(
    /** Where the loop may start and end (home, plus nearby airports when allowed). */
    val homes: List<String>,
    val startFrom: LocalDate,
    val startTo: LocalDate,
    /** Cities to visit when choosing automatically. */
    val stops: Int,
    val nightsMin: Int,
    val nightsMax: Int,
    /** The owner's own cities, visited in this order; empty lets the finder choose. */
    val cities: List<String> = emptyList(),
    val maxTotalChf: Int? = null,
)

/** A circle trip: home → city → city → … → home, priced leg by leg. */
data class LoopTrip(val legs: List<Fare>) {
    val totalChf: Double get() = legs.sumOf { it.priceChf }
    val start: LocalDate get() = legs.first().departAt.toLocalDate()
    val end: LocalDate get() = arrival(legs.last())
    val days: Int get() = ChronoUnit.DAYS.between(start, end).toInt() + 1
    /** Nights spent in each visited city, in order. */
    val nights: List<Int> get() = legs.zipWithNext { a, b -> ChronoUnit.DAYS.between(arrival(a), b.departAt.toLocalDate()).toInt() }
    val cities: List<String> get() = legs.dropLast(1).map { it.destinationAirport }

    companion object {
        fun arrival(f: Fare): LocalDate = f.departAt.plusMinutes(f.durationMin.toLong()).toLocalDate()
    }
}

/**
 * Builds loops the way a planner would: from home, take the cheapest flights out; from each city,
 * the cheapest onward flights after a few nights; finally, the cheapest way back home.
 * Ryanair's "cheapest to everywhere" answers each step in one request; Google fills legs Ryanair
 * doesn't fly (Basel itself is mostly easyJet). A small beam keeps the request count bounded.
 */
class LoopFinder(private val ryanair: Ryanair, private val google: GoogleFlights) {
    private val gate = Semaphore(3)

    suspend fun find(req: LoopRequest, progress: (String) -> Unit = {}): List<LoopTrip> =
        if (req.cities.isEmpty()) auto(req, progress) else manual(req, progress)

    private suspend fun auto(req: LoopRequest, progress: (String) -> Unit): List<LoopTrip> = coroutineScope {
        val homes = req.homes.toSet()
        progress("Finding the cheapest first flights from ${req.homes.joinToString(" · ")}…")
        var first = req.homes.map { o -> async { safe { ryanair.cheapest(o, req.startFrom, req.startTo) } } }.awaitAll().flatten()
            .filter { it.destinationAirport !in homes }
        // Few Ryanair routes from home (Basel is mostly easyJet): probe hubs with good onward networks on Google.
        if (first.map { it.destinationAirport }.distinct().size < BEAM) {
            val probes = HUBS.filter { h -> first.none { it.destinationAirport == h } }
            first = first + req.homes.take(1).flatMap { o ->
                probes.map { h ->
                    async { gate.withPermit { safe { google.search(o, h, req.startFrom).fares.filter { it.transfers == 0 }.minByOrNull { it.priceChf }?.let(::listOf).orEmpty() } } }
                }
            }.awaitAll().flatten()
        }
        var beam = first.groupBy { it.destinationAirport }.values.map { it.minBy { f -> f.priceChf } }
            .sortedBy { it.priceChf }.take(BEAM).map { listOf(it) }

        for (level in 2..req.stops) {
            progress("Adding city $level of ${req.stops} from ${beam.size} candidate routes…")
            beam = beam.map { path ->
                async {
                    val last = path.last()
                    val a = LoopTrip.arrival(last)
                    val seen = path.map { it.destinationAirport }.toSet() + homes
                    safe { ryanair.cheapest(last.destinationAirport, a.plusDays(req.nightsMin.toLong()), a.plusDays(req.nightsMax.toLong())) }
                        .filter { it.destinationAirport !in seen }
                        .sortedBy { it.priceChf }.take(PER_NODE)
                        .map { path + it }
                }
            }.awaitAll().flatten()
                .sortedBy { p -> p.sumOf { it.priceChf } }
                .distinctBy { p -> p.map { it.destinationAirport }.toSet() }
                .let(::varied)
                .take(BEAM)
            if (beam.isEmpty()) return@coroutineScope emptyList()
        }
        progress("Finding the way back home for ${beam.size} loops…")
        close(req, beam, homes)
    }

    /** The owner's cities, in their order and reversed; each leg is the cheapest in its window. */
    private suspend fun manual(req: LoopRequest, progress: (String) -> Unit): List<LoopTrip> = coroutineScope {
        val orders = listOf(req.cities, req.cities.reversed()).distinct()
        orders.map { order ->
            async {
                val legs = mutableListOf<Fare>()
                var from = req.homes.first()
                var winFrom = req.startFrom
                var winTo = req.startTo
                for ((i, city) in order.withIndex()) {
                    progress("Pricing ${Airports.cityOf(from)} → ${Airports.cityOf(city)} (${i + 1}/${order.size + 1})…")
                    val leg = cheapestLeg(from, city, winFrom, winTo) ?: return@async null
                    legs += leg
                    val a = LoopTrip.arrival(leg)
                    from = city; winFrom = a.plusDays(req.nightsMin.toLong()); winTo = a.plusDays(req.nightsMax.toLong())
                }
                val back = req.homes.mapNotNull { h -> cheapestLeg(from, h, winFrom, winTo) }.minByOrNull { it.priceChf } ?: return@async null
                LoopTrip(legs + back)
            }
        }.awaitAll().filterNotNull()
            .filter { req.maxTotalChf == null || it.totalChf <= req.maxTotalChf }
            .sortedBy { it.totalChf }
    }

    private suspend fun close(req: LoopRequest, beam: List<List<Fare>>, homes: Set<String>): List<LoopTrip> = coroutineScope {
        beam.map { path ->
            async {
                val last = path.last()
                val a = LoopTrip.arrival(last)
                val from = a.plusDays(req.nightsMin.toLong())
                val to = a.plusDays(req.nightsMax.toLong())
                val start = path.first().originAirport
                val targets = if (req.homes.size > 1) homes else setOf(start)
                val back = targets.mapNotNull { h -> cheapestLeg(last.destinationAirport, h, from, to) }.minByOrNull { it.priceChf }
                back?.let { LoopTrip(path + it) }
            }
        }.awaitAll().filterNotNull()
            .filter { req.maxTotalChf == null || it.totalChf <= req.maxTotalChf }
            .sortedBy { it.totalChf }
    }

    /**
     * At most two candidates per shared route-so-far, so the cheapest opening pair can't crowd out
     * every other loop. Order (cheapest first) is kept.
     */
    private fun varied(paths: List<List<Fare>>): List<List<Fare>> {
        val seen = HashMap<List<String>, Int>()
        return paths.filter { p ->
            val prefix = p.dropLast(1).map { it.destinationAirport }
            val n = seen.getOrDefault(prefix, 0)
            seen[prefix] = n + 1
            n < 2
        }
    }

    /** Ryanair across the whole window; if it doesn't fly the route, Google on the window's middle day. */
    private suspend fun cheapestLeg(from: String, to: String, winFrom: LocalDate, winTo: LocalDate): Fare? {
        safe { ryanair.cheapest(from, winFrom, winTo, to) }.minByOrNull { it.priceChf }?.let { return it }
        val mid = winFrom.plusDays(ChronoUnit.DAYS.between(winFrom, winTo) / 2)
        return gate.withPermit {
            safe { google.search(from, to, mid).fares }.sortedWith(compareBy({ it.transfers }, { it.priceChf })).firstOrNull()
        }
    }

    private inline fun <T> safe(block: () -> List<T>): List<T> = try { block() } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        emptyList()
    }

    companion object {
        private const val BEAM = 8
        private const val PER_NODE = 4
        /** Airports with dense low-cost onward networks, used to start loops from homes Ryanair barely serves. */
        private val HUBS = listOf("BCN", "BGY", "STN", "LIS", "NAP", "MAD")
    }
}
