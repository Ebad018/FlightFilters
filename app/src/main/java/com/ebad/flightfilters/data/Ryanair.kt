package com.ebad.flightfilters.data

import kotlinx.serialization.Serializable
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * Ryanair's public fare finder, the same endpoint its "cheap flights" pages use. No key.
 * Live prices, Ryanair only. Good at "cheapest to everywhere" and per-day calendars, which is
 * what lets the app tell an ordinary fare from a genuine drop.
 */
class Ryanair(private val http: CachedHttp) {

    @Serializable private data class Finder(val fares: List<Wrap> = emptyList())
    @Serializable private data class Wrap(val outbound: Leg)
    @Serializable private data class Leg(
        val departureAirport: Port,
        val arrivalAirport: Port,
        val departureDate: String,
        val arrivalDate: String,
        val price: Price? = null,
        val flightNumber: String? = null,
    )
    @Serializable private data class Port(val iataCode: String, val name: String = "", val countryName: String = "", val city: City? = null)
    @Serializable private data class City(val name: String = "")
    @Serializable private data class Price(val value: Double = 0.0, val currencyCode: String = "CHF")

    @Serializable private data class Calendar(val outbound: CalOut = CalOut())
    @Serializable private data class CalOut(val fares: List<Day> = emptyList())
    @Serializable private data class Day(
        val day: String,
        val departureDate: String? = null,
        val arrivalDate: String? = null,
        val price: Price? = null,
        val soldOut: Boolean = false,
        val unavailable: Boolean = false,
    )

    private val headers = mapOf("User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/128 Mobile Safari/537.36")

    /** Cheapest fare to each destination (or to [destination]) departing between [from] and [to]. */
    suspend fun cheapest(origin: String, from: LocalDate, to: LocalDate, destination: String? = null): List<Fare> {
        val url = buildString {
            append("https://www.ryanair.com/api/farfnd/v4/oneWayFares?market=en-gb&currency=CHF&adultPaxCount=1")
            append("&departureAirportIataCode=").append(origin)
            if (destination != null) append("&arrivalAirportIataCode=").append(destination)
            append("&outboundDepartureDateFrom=").append(from)
            append("&outboundDepartureDateTo=").append(to)
        }
        val body = http.get(url, headers, ttlMillis = 3 * HOUR)
        return CachedHttp.json.decodeFromString(Finder.serializer(), body.text).fares.mapNotNull { w ->
            val o = w.outbound
            val price = o.price?.value ?: return@mapNotNull null
            fare(origin, o.arrivalAirport.iataCode, o.departureDate, o.arrivalDate, price, o.flightNumber.orEmpty(), body.fetchedAt,
                o.arrivalAirport.city?.name?.takeIf { it.isNotBlank() } ?: o.arrivalAirport.name, o.arrivalAirport.countryName)
        }
    }

    @Serializable private data class RtFinder(val fares: List<RtWrap> = emptyList())
    @Serializable private data class RtWrap(val outbound: Leg, val inbound: Leg, val summary: Summary? = null)
    @Serializable private data class Summary(val price: Price? = null)

    /**
     * Cheapest return trip to each destination (or to [destination]): leave between [from] and [to],
     * stay [nights] nights. The price is the whole round trip.
     */
    suspend fun cheapestReturn(origin: String, from: LocalDate, to: LocalDate, nights: IntRange, destination: String? = null): List<Fare> {
        val url = buildString {
            append("https://www.ryanair.com/api/farfnd/v4/roundTripFares?market=en-gb&currency=CHF&adultPaxCount=1")
            append("&departureAirportIataCode=").append(origin)
            if (destination != null) append("&arrivalAirportIataCode=").append(destination)
            append("&outboundDepartureDateFrom=").append(from)
            append("&outboundDepartureDateTo=").append(to)
            append("&inboundDepartureDateFrom=").append(from.plusDays(nights.first.toLong()))
            append("&inboundDepartureDateTo=").append(to.plusDays(nights.last.toLong()))
            append("&durationFrom=").append(nights.first)
            append("&durationTo=").append(nights.last)
        }
        val body = http.get(url, headers, ttlMillis = 3 * HOUR)
        return CachedHttp.json.decodeFromString(RtFinder.serializer(), body.text).fares.mapNotNull { w ->
            val o = w.outbound
            val total = w.summary?.price?.value ?: ((o.price?.value ?: return@mapNotNull null) + (w.inbound.price?.value ?: return@mapNotNull null))
            val back = runCatching { LocalDateTime.parse(w.inbound.departureDate) }.getOrNull() ?: return@mapNotNull null
            fare(origin, o.arrivalAirport.iataCode, o.departureDate, o.arrivalDate, total, o.flightNumber.orEmpty(), body.fetchedAt,
                o.arrivalAirport.city?.name?.takeIf { it.isNotBlank() } ?: o.arrivalAirport.name, o.arrivalAirport.countryName)
                ?.let { f ->
                    f.copy(
                        returnAt = back.atZone(ZURICH).toOffsetDateTime(),
                        bookingUrl = "https://www.ryanair.com/gb/en/trip/flights/select?adults=1&teens=0&children=0&infants=0&isReturn=true&discount=0" +
                            "&dateOut=${f.departAt.toLocalDate()}&dateIn=${back.toLocalDate()}&originIata=$origin&destinationIata=${f.destinationAirport}",
                    )
                }
        }
    }

    /** Cheapest Ryanair fare on every day of [month] for one route; days without flights are skipped. */
    suspend fun calendar(origin: String, destination: String, month: YearMonth): List<Fare> {
        val url = "https://www.ryanair.com/api/farfnd/v4/oneWayFares/$origin/$destination/cheapestPerDay?currency=CHF&outboundMonthOfDate=${month.atDay(1)}"
        val body = http.get(url, headers, ttlMillis = 6 * HOUR)
        return CachedHttp.json.decodeFromString(Calendar.serializer(), body.text).outbound.fares.mapNotNull { d ->
            if (d.soldOut || d.unavailable) return@mapNotNull null
            val price = d.price?.value ?: return@mapNotNull null
            fare(origin, destination, d.departureDate ?: return@mapNotNull null, d.arrivalDate ?: return@mapNotNull null, price, "", body.fetchedAt, null, null)
        }
    }

    private fun fare(
        origin: String, dest: String, dep: String, arr: String, price: Double, flight: String,
        seenAt: Long, city: String?, country: String?,
    ): Fare? {
        val d = runCatching { LocalDateTime.parse(dep) }.getOrNull() ?: return null
        val a = runCatching { LocalDateTime.parse(arr) }.getOrNull() ?: return null
        // Both times are local; across one time-zone boundary this is off by up to an hour, which is fine for ranking.
        val minutes = Duration.between(d, a).toMinutes().toInt().let { if (it <= 0) it + 24 * 60 else it }
        return Fare(
            origin = origin,
            destination = dest,
            originAirport = origin,
            destinationAirport = dest,
            departAt = d.atZone(ZURICH).toOffsetDateTime(),
            returnAt = null,
            priceChf = price,
            airline = "FR",
            flightNumber = flight.removePrefix("FR"),
            transfers = 0,
            returnTransfers = 0,
            durationMin = minutes,
            bookingUrl = "https://www.ryanair.com/gb/en/trip/flights/select?adults=1&teens=0&children=0&infants=0&isReturn=false&discount=0" +
                "&dateOut=${d.toLocalDate()}&originIata=$origin&destinationIata=$dest",
            source = FareSource.RYANAIR,
            seenAt = seenAt,
            destinationCity = city,
            country = country,
        )
    }

    companion object {
        private const val HOUR = 60 * 60 * 1000L
        private val ZURICH: ZoneId = ZoneId.of("Europe/Zurich")
    }
}
