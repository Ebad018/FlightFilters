package com.ebad.flightfilters.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.time.LocalDate

/**
 * Parses real responses captured on 2026-10-02 (Google Flights BSL→LIS, Ryanair from FKB),
 * served through CachedHttp's disk cache so no network is touched.
 */
class ScraperParsingTest {

    private fun httpWith(url: String, resource: String): CachedHttp {
        val dir = Files.createTempDirectory("ff").toFile()
        val name = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        val text = javaClass.classLoader!!.getResource(resource)!!.readText()
        File(dir, "$name.json").writeText(text)
        return CachedHttp(dir)
    }

    @Test
    fun googleFlightsLabelsBecomeFares() = runBlocking {
        val date = LocalDate.of(2026, 11, 12)
        val probe = GoogleFlights(CachedHttp(Files.createTempDirectory("x").toFile()))
        val url = probe.url("BSL", "LIS", date, null)
        val r = GoogleFlights(httpWith(url, "google_bsl_lis.html")).search("BSL", "LIS", date)
        println("google: ${r.fares.size} fares, level=${r.priceLevel}")
        r.fares.take(5).forEach { println("  ${it.departAt} ${it.airline} ${it.transfers} stops ${it.durationMin} min CHF ${it.priceChf} via ${it.layovers}") }
        assertEquals("all 9 distinct flights on the page", 9, r.fares.size)
        assertEquals("high", r.priceLevel)
        val easy = r.fares.first { it.airline == "U2" && it.transfers == 0 }
        assertEquals(123.0, easy.priceChf, 0.01)
        assertEquals(12, easy.departAt.hour)
        assertEquals(185, easy.durationMin)
        assertTrue(r.fares.any { it.layovers.isNotEmpty() })
    }

    @Test
    fun ryanairReturnsBecomeRoundTrips() = runBlocking {
        val from = LocalDate.of(2026, 10, 10)
        val to = LocalDate.of(2026, 11, 10)
        val url = "https://www.ryanair.com/api/farfnd/v4/roundTripFares?market=en-gb&currency=CHF&adultPaxCount=1" +
            "&departureAirportIataCode=BSL&outboundDepartureDateFrom=$from&outboundDepartureDateTo=$to" +
            "&inboundDepartureDateFrom=2026-10-12&inboundDepartureDateTo=2026-11-17&durationFrom=2&durationTo=7"
        val fares = Ryanair(httpWith(url, "ryanair_bsl_rt.json")).cheapestReturn("BSL", from, to, 2..7)
        println("ryanair return: ${fares.map { "${it.destinationAirport} ${it.departAt.toLocalDate()}→${it.returnAt?.toLocalDate()} CHF ${it.priceChf}" }}")
        assertEquals(3, fares.size)
        assertTrue(fares.all { it.returnAt != null && it.returnAt!!.isAfter(it.departAt) && it.bookingUrl!!.contains("isReturn=true") })
    }

    @Test
    fun destinationFilterKeepsOnlyThePlannedPlaces() {
        fun fare(dest: String, city: String = dest) = Fare(
            "BSL", city, "BSL", dest, java.time.OffsetDateTime.now(), null, 50.0, "FR", "1", 0, 0, 90, null, FareSource.RYANAIR, 0,
        )
        val q = Query(origins = emptyList(), destinations = listOf("ARN"))
        assertTrue(q.goesTo(fare("ARN", "STO")))
        assertTrue(!q.goesTo(fare("NYO", "STO")))
        assertTrue(!q.goesTo(fare("BCN")))
        assertTrue(Query(origins = emptyList(), destinations = listOf("STO")).goesTo(fare("NYO", "STO")))
        assertTrue(Query(origins = emptyList()).goesTo(fare("BCN")))
        assertEquals(listOf("LIS"), Query(origins = emptyList(), destination = "LIS").dests)
    }

    @Test
    fun ryanairFinderBecomesFares() = runBlocking {
        val from = LocalDate.of(2026, 10, 3)
        val to = LocalDate.of(2026, 12, 31)
        val url = "https://www.ryanair.com/api/farfnd/v4/oneWayFares?market=en-gb&currency=CHF&adultPaxCount=1" +
            "&departureAirportIataCode=FKB&outboundDepartureDateFrom=$from&outboundDepartureDateTo=$to"
        val fares = Ryanair(httpWith(url, "ryanair_fkb.json")).cheapest("FKB", from, to)
        println("ryanair: ${fares.size} fares; cheapest ${fares.minBy { it.priceChf }.let { "${it.destinationCity}, ${it.country} CHF ${it.priceChf}" }}")
        assertEquals(32, fares.size)
        assertTrue(fares.all { it.priceChf > 0 && it.durationMin in 30..600 && it.country != null })
    }
}
