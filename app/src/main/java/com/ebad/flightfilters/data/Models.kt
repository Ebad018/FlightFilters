package com.ebad.flightfilters.data

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.OffsetDateTime

/** A departure window for one origin line: calendar days plus an hour-of-day band (local time at the origin). */
@Serializable
data class TimeWindow(
    val dateFrom: String,
    val dateTo: String,
    val hourFrom: Int = 0,
    val hourTo: Int = 24,
) {
    val from: LocalDate get() = LocalDate.parse(dateFrom)
    val to: LocalDate get() = LocalDate.parse(dateTo)

    fun contains(at: OffsetDateTime): Boolean {
        val day = at.toLocalDate()
        if (day.isBefore(from) || day.isAfter(to)) return false
        val h = at.hour
        return h >= hourFrom && h < hourTo
    }

    fun containsHour(at: OffsetDateTime): Boolean = at.hour >= hourFrom && at.hour < hourTo
}

@Serializable
data class OriginLine(
    val iata: String,
    val window: TimeWindow,
    val enabled: Boolean = true,
)

@Serializable
enum class Stops(val label: String, val maxTransfers: Int) {
    DIRECT("Direct", 0), ONE("Up to 1", 1), ANY("Any", 9)
}

@Serializable
enum class Cabin(val label: String, val serpClass: Int) {
    ECONOMY("Economy", 1), PREMIUM("Premium", 2), BUSINESS("Business", 3), FIRST("First", 4)
}

@Serializable
data class Filters(
    val maxPriceChf: Int? = null,
    val stops: Stops = Stops.ANY,
    val airlinesInclude: Set<String> = emptySet(),
    val airlinesExclude: Set<String> = emptySet(),
    val maxDurationHours: Int? = null,
    val cabin: Cabin = Cabin.ECONOMY,
)

@Serializable
data class Query(
    val origins: List<OriginLine>,
    /** Legacy single destination from older saved plans; read through [dests]. */
    val destination: String? = null,
    /** City or airport IATA codes; empty scans anywhere. */
    val destinations: List<String> = emptyList(),
    val oneWay: Boolean = true,
    val nightsMin: Int = 2,
    val nightsMax: Int = 7,
    /** Days scanned either side of each origin's window, to find cheaper nearby dates. */
    val flexDays: Int = 3,
    val filters: Filters = Filters(),
) {
    val activeOrigins: List<OriginLine> get() = origins.filter { it.enabled }

    /** Every destination this plan targets; empty means anywhere. */
    val dests: List<String> get() = destinations.ifEmpty { listOfNotNull(destination) }

    fun withDests(codes: List<String>): Query = copy(destination = null, destinations = codes.distinct())

    /** True when a fare lands where this plan is going (by airport, or by city code like STO). */
    fun goesTo(fare: Fare): Boolean = dests.isEmpty() || fare.destinationAirport in dests || fare.destination in dests
}

@Serializable
data class Preset(
    val id: String,
    val name: String,
    val query: Query,
)

@Serializable
enum class ThemeMode { MIDNIGHT, PORCELAIN, SYSTEM }

@Serializable
data class AppSettings(
    val homeIata: String = "BSL",
    /** Minutes of ground travel from home to each airport, one way. */
    val groundMinutes: Map<String, Int> = Airports.defaultGroundMinutes,
    /** How much an hour of the owner's travel time is worth, used to put time and money on one scale. */
    val valueOfTimeChfPerHour: Int = 25,
    val showEur: Boolean = true,
    val travelpayoutsToken: String = "",
    val serpApiKey: String = "",
    val themeMode: ThemeMode = ThemeMode.MIDNIGHT,
    val lastMinuteDays: Int = 4,
    val forceSampleData: Boolean = false,
    val aiProvider: AiProvider = AiProvider.LLM7,
    /** API key per provider name; keys stay on the device. */
    val aiKeys: Map<String, String> = emptyMap(),
    /** Model override per provider name; blank means the provider's default. */
    val aiModels: Map<String, String> = emptyMap(),
    val aiCustomUrl: String = "",
    /** Deals and Departures also scan airports near home, not just [homeIata]. */
    val includeNearby: Boolean = false,
    /** Deals and Departures only show fares to the Plan's destinations when it has any. */
    val dealsFollowPlan: Boolean = true,
    /** Deals look for return tickets (nights from the Plan) instead of one-ways. */
    val dealsReturn: Boolean = false,
    /** One-time cleanup of the old two-airport default plan has run. */
    val migratedHomeOnly: Boolean = false,
) {
    fun aiKey(p: AiProvider): String = aiKeys[p.name].orEmpty()
    fun aiModel(p: AiProvider): String = aiModels[p.name]?.takeIf { it.isNotBlank() } ?: p.defaultModel
    /** Real fares are the default (Google Flights + Ryanair need no key); sample data is an explicit offline demo. */
    val usesSampleData: Boolean get() = forceSampleData
    fun ground(iata: String): Int = groundMinutes[iata] ?: Airports.byCode(iata)?.groundMinutes ?: 0
}

enum class FareSource(val label: String, val live: Boolean) {
    GOOGLE_LIVE("Google Flights", true),
    RYANAIR("Ryanair fare finder", true),
    AVIASALES_CACHE("Aviasales cache", false),
    SAMPLE("Sample data", false),
}

/** One priced ticket, normalised from whichever provider returned it. Prices are always CHF. */
data class Fare(
    val origin: String,
    val destination: String,
    val originAirport: String,
    val destinationAirport: String,
    val departAt: OffsetDateTime,
    val returnAt: OffsetDateTime?,
    val priceChf: Double,
    val airline: String,
    val flightNumber: String,
    val transfers: Int,
    val returnTransfers: Int,
    val durationMin: Int,
    val bookingUrl: String?,
    val source: FareSource,
    /** Epoch millis when this price was fetched from the provider. */
    val seenAt: Long,
    val destinationCity: String? = null,
    val country: String? = null,
    /** Change airports, when the provider names them (Google does). */
    val layovers: List<String> = emptyList(),
) {
    /** Duration is part of identity: Google lists different routings that leave at the same minute on the same carrier. */
    val key: String get() = "$originAirport-$destinationAirport-$departAt-$airline$flightNumber-$durationMin"
}

/** A fare placed in the owner's world: ground time added, and money and time combined into one cost. */
data class Itinerary(
    val fare: Fare,
    val groundMin: Int,
    val effectiveCostChf: Double,
    val inWindow: Boolean,
) {
    val totalMin: Int get() = fare.durationMin + groundMin
}

enum class NoticeKind { CHEAPER_DATE, OTHER_ORIGIN, FASTER }

data class Notice(
    val kind: NoticeKind,
    val title: String,
    val detail: String,
    val itinerary: Itinerary,
)

data class LiveCheck(
    val priceChf: Double?,
    val airline: String?,
    val layovers: List<String>,
    val totalDurationMin: Int?,
    val priceLevel: String?,
    val matched: Boolean,
    val url: String?,
    val checkedAt: Long,
    val error: String? = null,
)

data class ScanResult(
    val query: Query,
    val itineraries: List<Itinerary>,
    val notices: List<Notice>,
    /** Cheapest in-filter price per departure day across the scanned span. */
    val dayPrices: Map<LocalDate, Double>,
    val sources: Set<FareSource>,
    val oldestSeenAt: Long,
    val failedOrigins: List<String>,
    /** Google's own read of the route: "low", "typical" or "high". */
    val priceLevel: String? = null,
)

/** A fare that is unusually cheap for its route: compared against the route's usual day that month. */
data class PriceDrop(
    val itinerary: Itinerary,
    /** Median daily price on this route in the fare's month, when the calendar could be read. */
    val typicalChf: Double?,
) {
    val dropPct: Int
        get() = typicalChf?.takeIf { it > 0 }?.let { ((1 - itinerary.fare.priceChf / it) * 100).toInt().coerceAtLeast(0) } ?: 0
}

data class CountryLow(val country: String, val cheapest: Itinerary, val destinations: Int)

/** A hand-picked promo or error fare from a deals site's public feed. */
data class PromoDeal(
    val title: String,
    val link: String,
    val publishedAt: Long,
    val priceEur: Int?,
    val summary: String,
    val nearYou: Boolean,
    val errorFare: Boolean,
    val site: String,
)

data class DealsResult(
    val drops: List<PriceDrop>,
    val countries: List<CountryLow>,
    val promos: List<PromoDeal>,
    val scannedOrigins: List<String>,
    val fetchedAt: Long,
    val sources: Set<FareSource>,
    val promoError: String?,
)
