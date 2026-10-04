package com.ebad.flightfilters.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class IntentParserTest {
    // Friday 2 October 2026, the day these were written.
    private val today = LocalDate.of(2026, 10, 2)
    private val places = mapOf(
        "lisbon" to "LIS", "tokyo" to "TYO", "basel" to "BSL", "zurich" to "ZRH", "iceland" to "KEF",
        "marrakesh" to "RAK", "portugal" to "LIS", "LIS" to "LIS", "BSL" to "BSL", "CMB" to "CMB", "colombo" to "CMB",
    )
    private val p = IntentParser({ places[it] ?: places[it.lowercase()] ?: places[it.uppercase()] }, { today })

    @Test fun destinationAndNextFridayAndBudget() {
        val i = p.parse("Cheap flight to Lisbon next Friday under 150 francs")
        assertEquals("search", i.action)
        assertEquals("LIS", i.destination)
        assertEquals("2026-10-09", i.dateFrom)
        assertEquals(150, i.maxPriceChf)
    }

    @Test fun monthWithFlexAndOrigin() {
        val i = p.parse("Cheapest way from Zurich to Tokyo in April, I'm flexible")
        assertEquals("TYO", i.destination)
        assertEquals(listOf("ZRH"), i.origins)
        assertEquals("2027-04-01", i.dateFrom)
        assertEquals("2027-04-30", i.dateTo)
        assertEquals(3, i.flexDays)
    }

    @Test fun dealsWithoutDestination() {
        val i = p.parse("Any error fares or big price drops this month?")
        assertEquals("deals", i.action)
        assertNull(i.destination)
        assertEquals(30, i.horizonDays)
    }

    @Test fun weekendReturnDirectMorning() {
        val i = p.parse("Direct flights to Marrakesh next weekend for 3 nights, leaving in the morning")
        assertEquals("RAK", i.destination)
        assertEquals("DIRECT", i.stops)
        assertEquals(false, i.oneWay)
        assertEquals(2, i.nightsMin); assertEquals(4, i.nightsMax)
        assertEquals(5, i.hourFrom); assertEquals(12, i.hourTo)
        assertEquals("2026-10-09", i.dateFrom)
    }

    @Test fun explicitDatesAndCode() {
        val i = p.parse("BSL to CMB 12 nov - 20 nov")
        assertEquals("CMB", i.destination)
        assertEquals(listOf("BSL"), i.origins)
        assertEquals("2026-11-12", i.dateFrom)
        assertEquals("2026-11-20", i.dateTo)
    }

    @Test fun mayAsVerbIsNotAMonth() {
        val i = p.parse("I may want to visit Iceland")
        assertEquals("KEF", i.destination)
        assertNull(i.dateFrom)
    }

    @Test fun aiSynonymsFillGaps() {
        val local = p.parse("something nice and warm")
        val merged = p.mergeAi(local, """```json
            {"departure_airport":"BSL","arrival_airport":"Lisbon","departure_date":"2026-10-23","max_price":200}
            ```""")
        assertEquals("search", merged.action)
        assertEquals("LIS", merged.destination)
        assertEquals("2026-10-23", merged.dateFrom)
        assertEquals(200, merged.maxPriceChf)
    }

    @Test fun localWinsOverAi() {
        val local = p.parse("to Lisbon on 12 nov")
        val merged = p.mergeAi(local, """{"reply":"On it!","action":"search","destination":"Tokyo","dateFrom":"2026-12-01"}""")
        assertEquals("LIS", merged.destination)
        assertEquals("2026-11-12", merged.dateFrom)
        assertEquals("On it!", merged.reply)
    }
}
