package com.ebad.flightfilters.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Synthetic fares so the app can be explored before API keys are added.
 * Every fare is tagged [FareSource.SAMPLE] and the UI labels it as sample data wherever it appears.
 */
object SampleData {
    private val basePrice = mapOf(
        "LIS" to 118, "BCN" to 78, "OPO" to 96, "ATH" to 139, "LON" to 72, "NAP" to 88,
        "PMI" to 84, "AGP" to 102, "CPH" to 108, "BER" to 74, "MAD" to 94, "RAK" to 148,
    )
    private val baseMinutes = mapOf(
        "LIS" to 175, "BCN" to 95, "OPO" to 150, "ATH" to 165, "LON" to 95, "NAP" to 110,
        "PMI" to 100, "AGP" to 160, "CPH" to 110, "BER" to 85, "MAD" to 130, "RAK" to 205,
    )
    private val originFactor = mapOf(
        "BSL" to 1.0, "ZRH" to 1.18, "SXB" to 0.97, "BRN" to 1.45, "FKB" to 0.82,
        "STR" to 0.9, "GVA" to 0.96, "FRA" to 1.04,
    )
    private val carriers = mapOf(
        "BSL" to listOf("U2", "U2", "W6", "VY", "LX"), "ZRH" to listOf("LX", "LX", "U2", "EW", "TK"),
        "SXB" to listOf("HV", "V7", "AF"), "BRN" to listOf("2L"), "FKB" to listOf("FR", "FR", "W6"),
        "STR" to listOf("EW", "FR", "LH"), "GVA" to listOf("U2", "LX", "BA"), "FRA" to listOf("LH", "FR", "TP"),
    )

    fun fares(origins: List<String>, destination: String?, from: LocalDate, to: LocalDate, oneWay: Boolean, nights: IntRange): List<Fare> {
        val dests = if (destination != null) listOf(destination) else basePrice.keys.toList()
        val now = System.currentTimeMillis() - 95 * 60 * 1000L // "seen" ~1.5 h ago, like a warm cache
        val out = mutableListOf<Fare>()
        var day = from
        while (!day.isAfter(to)) {
            for (o in origins) for (d in dests) {
                val rnd = Random((o + d + day).hashCode())
                val count = rnd.nextInt(0, 3)
                repeat(count) { i -> out += fare(o, d, day, i, rnd, oneWay, nights, now) }
            }
            day = day.plusDays(1)
        }
        return out
    }

    private fun fare(o: String, d: String, day: LocalDate, i: Int, rnd: Random, oneWay: Boolean, nights: IntRange, seenAt: Long): Fare {
        val dow = when (day.dayOfWeek) {
            DayOfWeek.FRIDAY, DayOfWeek.SUNDAY -> 1.28
            DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY -> 0.84
            else -> 1.0
        }
        val stops = if (rnd.nextInt(100) < 22) 1 else 0
        val base = (basePrice[d] ?: 110) * (originFactor[o] ?: 1.0) * dow * (0.78 + rnd.nextDouble() * 0.5)
        val price = (base * (if (stops == 1) 0.82 else 1.0) * (if (oneWay) 1.0 else 1.85)).roundToInt().toDouble()
        val minutes = (baseMinutes[d] ?: 140) + stops * (95 + rnd.nextInt(0, 120)) + rnd.nextInt(-10, 15)
        val hour = listOf(6, 7, 9, 11, 13, 15, 17, 19, 21)[(rnd.nextInt(9) + i * 3) % 9]
        val depart = OffsetDateTime.of(day.atTime(hour, listOf(0, 5, 15, 25, 40, 50)[rnd.nextInt(6)]), ZoneOffset.ofHours(2))
        val airline = carriers[o].orEmpty().ifEmpty { listOf("U2") }.let { it[rnd.nextInt(it.size)] }
        val returnAt = if (oneWay) null else depart.plusDays(rnd.nextInt(nights.first, nights.last + 1).toLong())
        return Fare(
            origin = o, destination = d, originAirport = o, destinationAirport = d,
            departAt = depart, returnAt = returnAt, priceChf = price, airline = airline,
            flightNumber = (1000 + rnd.nextInt(8999)).toString(),
            transfers = stops, returnTransfers = if (oneWay) 0 else stops,
            durationMin = minutes, bookingUrl = null, source = FareSource.SAMPLE, seenAt = seenAt,
        )
    }

}
