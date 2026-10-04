package com.ebad.flightfilters.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.time.LocalDate

/** Live loop searches from Basel. Skipped unless -Dlive=true. */
class LoopLiveTest {
    private fun finder(): LoopFinder {
        val http = CachedHttp(Files.createTempDirectory("loop").toFile())
        return LoopFinder(Ryanair(http), GoogleFlights(http))
    }

    private fun show(label: String, loops: List<LoopTrip>) {
        println("$label: ${loops.size} loops")
        loops.take(3).forEach { l ->
            println("  CHF ${"%.0f".format(l.totalChf)}, ${l.days} days, nights ${l.nights}: " +
                l.legs.joinToString(" | ") { "${it.originAirport}→${it.destinationAirport} ${it.departAt.toLocalDate()} ${it.airline} ${it.priceChf.toInt()}" })
        }
    }

    @Test
    fun autoLoopFromBasel() = runBlocking {
        assumeTrue(System.getProperty("live") == "true")
        val start = LocalDate.now().plusDays(30)
        val req = LoopRequest(listOf("BSL"), start, start.plusDays(3), stops = 3, nightsMin = 2, nightsMax = 4)
        val loops = finder().find(req) { println("  … $it") }
        show("auto BSL x3", loops)
        assertTrue(loops.isNotEmpty())
        loops.forEach { l ->
            assertTrue(l.legs.first().originAirport == "BSL" && l.legs.last().destinationAirport == "BSL")
            assertTrue(l.legs.zipWithNext().all { (a, b) -> a.destinationAirport == b.originAirport && !b.departAt.isBefore(a.departAt) })
            assertTrue(l.nights.all { it in 2..4 })
        }
    }

    @Test
    fun manualLoop() = runBlocking {
        assumeTrue(System.getProperty("live") == "true")
        val start = LocalDate.now().plusDays(30)
        val req = LoopRequest(listOf("BSL"), start, start.plusDays(2), stops = 2, nightsMin = 2, nightsMax = 3, cities = listOf("BCN", "LIS"))
        val loops = finder().find(req)
        show("manual BCN,LIS", loops)
        assertTrue(loops.isNotEmpty())
    }
}
