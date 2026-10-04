package com.ebad.flightfilters.data

import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import java.time.YearMonth

/**
 * Aviasales Data API (Travelpayouts). Free with an affiliate token. Prices are cached from
 * recent searches by other travellers, so they are indicative, never guaranteed.
 * Docs: https://travelpayouts.github.io/slate/#flight_data_api
 */
class TravelpayoutsApi(private val http: CachedHttp) {

    @Serializable
    private data class Response(val success: Boolean = false, val data: List<Item> = emptyList(), val error: String? = null)

    @Serializable
    private data class Item(
        val origin: String = "",
        val destination: String = "",
        val origin_airport: String? = null,
        val destination_airport: String? = null,
        val price: Double = 0.0,
        val airline: String = "",
        val flight_number: String = "",
        val departure_at: String = "",
        val return_at: String? = null,
        val transfers: Int = 0,
        val return_transfers: Int = 0,
        val duration: Int = 0,
        val duration_to: Int? = null,
        val link: String? = null,
    )

    /** Cheapest cached fares for one origin across a calendar month; [destination] null means anywhere. */
    suspend fun pricesForMonth(
        token: String,
        origin: String,
        destination: String?,
        month: YearMonth,
        oneWay: Boolean,
        directOnly: Boolean,
    ): List<Fare> {
        val url = buildString {
            append("https://api.travelpayouts.com/aviasales/v3/prices_for_dates?currency=chf&sorting=price&limit=500&page=1")
            append("&origin=").append(origin)
            if (destination != null) append("&destination=").append(destination)
            append("&departure_at=").append(month.toString())
            append("&one_way=").append(oneWay)
            append("&direct=").append(directOnly)
        }
        val body = http.get(url, mapOf("X-Access-Token" to token), ttlMillis = 3 * HOUR)
        val parsed = CachedHttp.json.decodeFromString(Response.serializer(), body.text)
        if (!parsed.success) throw HttpException(200, parsed.error ?: "Aviasales returned no data")
        return parsed.data.mapNotNull { it.toFare(body.fetchedAt) }
    }

    private fun Item.toFare(fetchedAt: Long): Fare? {
        val depart = runCatching { OffsetDateTime.parse(departure_at) }.getOrNull() ?: return null
        return Fare(
            origin = origin,
            destination = destination,
            originAirport = origin_airport ?: origin,
            destinationAirport = destination_airport ?: destination,
            departAt = depart,
            returnAt = return_at?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() },
            priceChf = price,
            airline = airline,
            flightNumber = flight_number,
            transfers = transfers,
            returnTransfers = return_transfers,
            durationMin = duration_to ?: duration,
            bookingUrl = link?.let { "https://www.aviasales.com$it" },
            source = FareSource.AVIASALES_CACHE,
            seenAt = fetchedAt,
        )
    }

    companion object {
        const val HOUR = 60 * 60 * 1000L
    }
}
