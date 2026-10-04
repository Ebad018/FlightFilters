package com.ebad.flightfilters.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.time.LocalDate

/** Live: Basel → Stockholm Arlanda, one way and return, must only ever show BSL→ARN. Skipped unless -Dlive=true. */
class ScanLiveTest {
    private fun repo(): FareRepository {
        val http = CachedHttp(Files.createTempDirectory("live").toFile())
        return FareRepository(TravelpayoutsApi(http), SerpApi(http), GoogleFlights(http), Ryanair(http), DealsFeed(http), http)
    }

    @Test
    fun baselToArlandaShowsOnlyThatRoute() = runBlocking {
        assumeTrue(System.getProperty("live") == "true")
        val day = LocalDate.now().plusDays(20)
        val window = TimeWindow(day.toString(), day.plusDays(1).toString())
        for (oneWay in listOf(true, false)) {
            val q = Query(origins = listOf(OriginLine("BSL", window)), destinations = listOf("ARN"), oneWay = oneWay, nightsMin = 3, nightsMax = 5)
            val r = repo().scan(q, AppSettings())
            println("oneWay=$oneWay: ${r.itineraries.size} itineraries, sources ${r.sources}, failed ${r.failedOrigins}")
            r.itineraries.take(4).forEach { println("  ${it.fare.originAirport}→${it.fare.destinationAirport} ${it.fare.departAt} back ${it.fare.returnAt} ${it.fare.airline} CHF ${it.fare.priceChf}") }
            assertTrue(r.itineraries.isNotEmpty())
            assertTrue(r.itineraries.all { it.fare.originAirport == "BSL" && it.fare.destinationAirport == "ARN" })
            assertTrue(r.itineraries.all { (it.fare.returnAt != null) == !oneWay })
        }
    }
}
