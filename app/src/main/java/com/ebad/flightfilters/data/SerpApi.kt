package com.ebad.flightfilters.data

import kotlinx.serialization.Serializable
import java.net.URLEncoder
import java.time.format.DateTimeFormatter

/**
 * Google Flights via SerpApi. Live prices, but the free plan allows a small number of searches
 * per month, so this only runs when the owner taps "Check live" on one itinerary.
 */
class SerpApi(private val http: CachedHttp) {

    @Serializable
    private data class Response(
        val best_flights: List<Option> = emptyList(),
        val other_flights: List<Option> = emptyList(),
        val price_insights: Insights? = null,
        val search_metadata: Metadata? = null,
        val error: String? = null,
    )

    @Serializable private data class Metadata(val google_flights_url: String? = null)
    @Serializable private data class Insights(val lowest_price: Double? = null, val price_level: String? = null)

    @Serializable
    private data class Option(
        val flights: List<Leg> = emptyList(),
        val layovers: List<Layover> = emptyList(),
        val total_duration: Int? = null,
        val price: Double? = null,
    )

    @Serializable private data class Leg(val departure_airport: Point? = null, val airline: String? = null, val flight_number: String? = null)
    @Serializable private data class Point(val id: String? = null, val time: String? = null)
    @Serializable private data class Layover(val id: String? = null, val name: String? = null)

    suspend fun check(apiKey: String, fare: Fare, cabin: Cabin): LiveCheck {
        val date = DateTimeFormatter.ISO_LOCAL_DATE
        val url = buildString {
            append("https://serpapi.com/search.json?engine=google_flights&hl=en&gl=ch&currency=CHF")
            append("&departure_id=").append(fare.originAirport)
            append("&arrival_id=").append(fare.destinationAirport)
            append("&outbound_date=").append(fare.departAt.toLocalDate().format(date))
            if (fare.returnAt != null) {
                append("&type=1&return_date=").append(fare.returnAt.toLocalDate().format(date))
            } else {
                append("&type=2")
            }
            append("&travel_class=").append(cabin.serpClass)
            append("&api_key=").append(URLEncoder.encode(apiKey, "UTF-8"))
        }
        // A live check is worth repeating only after a while; 20 minutes keeps the quota safe.
        val body = http.get(url, ttlMillis = 20 * 60 * 1000L)
        val r = CachedHttp.json.decodeFromString(Response.serializer(), body.text)
        if (r.error != null) {
            return LiveCheck(null, null, emptyList(), null, null, false, null, body.fetchedAt, r.error)
        }
        val options = r.best_flights + r.other_flights
        // Prefer the same flight (carrier + number on the first leg); otherwise the cheapest.
        val wanted = (fare.airline + fare.flightNumber).replace(" ", "")
        val match = options.firstOrNull { opt ->
            opt.flights.firstOrNull()?.flight_number?.replace(" ", "")?.equals(wanted, ignoreCase = true) == true
        }
        val pick = match ?: options.filter { it.price != null }.minByOrNull { it.price!! }
        return LiveCheck(
            priceChf = pick?.price,
            airline = pick?.flights?.firstOrNull()?.airline,
            layovers = pick?.layovers?.mapNotNull { it.id } ?: emptyList(),
            totalDurationMin = pick?.total_duration,
            priceLevel = r.price_insights?.price_level,
            matched = match != null,
            url = r.search_metadata?.google_flights_url,
            checkedAt = body.fetchedAt,
            error = if (pick == null) "No live fares on that date" else null,
        )
    }
}

/** CHF→EUR from the ECB reference rate (frankfurter, no key). Cached for a day. */
class FxRates(private val http: CachedHttp) {
    @Serializable private data class Response(val rates: Map<String, Double> = emptyMap())

    /** The ECB rate, or null when it can't be fetched (callers then fall back to [FALLBACK] and say so). */
    suspend fun chfToEur(): Double? = runCatching {
        val body = http.get("https://api.frankfurter.dev/v1/latest?base=CHF&symbols=EUR", ttlMillis = 24 * 60 * 60 * 1000L)
        CachedHttp.json.decodeFromString(Response.serializer(), body.text).rates["EUR"]
    }.getOrNull()

    companion object {
        const val FALLBACK = 1.07
    }
}
