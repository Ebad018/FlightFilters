package com.ebad.flightfilters.data

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Turns a plain-language request into an app action, then explains the results.
 * Step 1: the model returns a small JSON intent (search / deals / chat).
 * Step 2: the app runs the real scan itself, so every price comes from live sources, never from the model.
 * Step 3: the model writes a short recommendation from a digest of those results.
 */
class Assistant(private val ai: AiClient, private val airports: AirportDb) {

    @Serializable
    data class Intent(
        val reply: String = "",
        val action: String = "none",
        val origins: List<String>? = null,
        val destination: String? = null,
        val dateFrom: String? = null,
        val dateTo: String? = null,
        val hourFrom: Int? = null,
        val hourTo: Int? = null,
        val oneWay: Boolean? = null,
        val nightsMin: Int? = null,
        val nightsMax: Int? = null,
        val flexDays: Int? = null,
        val maxPriceChf: Int? = null,
        val stops: String? = null,
        val horizonDays: Int? = null,
    )

    private val parser = IntentParser({ airports.bestMatch(it) })

    /** True when the last [understand] had to work without the AI. */
    var lastAiFailed: String? = null
        private set

    /**
     * The built-in parser reads the request first; the AI (if it answers) fills the gaps and writes
     * the friendly reply. A failing AI never blocks a search the parser already understood.
     */
    suspend fun understand(settings: AppSettings, history: List<ChatTurn>): Intent {
        val question = history.lastOrNull { it.role == "user" }?.text.orEmpty()
        val local = parser.parse(question)
        val raw = try {
            lastAiFailed = null
            ai.complete(settings, intentSystem(settings), history)
        } catch (e: AiClient.AiException) {
            lastAiFailed = e.message
            if (local.action == "none") throw e
            null
        }
        val merged = parser.mergeAi(local, raw)
        return if (merged.reply.isBlank()) merged.copy(reply = describe(merged)) else merged
    }

    private fun describe(i: Intent): String = when (i.action) {
        "deals" -> "Looking for price drops, cheap countries and promo fares from every airport near Basel."
        "search" -> buildString {
            append("Searching ").append(i.destination?.let { Airports.cityOf(it) } ?: "everywhere")
            i.dateFrom?.let { append(" from ").append(it).append(if (i.dateTo != null && i.dateTo != it) " to ${i.dateTo}" else "") }
            i.maxPriceChf?.let { append(", under CHF ").append(it) }
            append(".")
        }
        else -> ""
    }

    suspend fun explain(settings: AppSettings, question: String, digest: String): String =
        ai.complete(
            settings,
            "You are a concise flight-deal advisor for a traveller based in Basel, Switzerland. " +
                "Use ONLY the fares in the data below; never invent prices, airlines or dates. " +
                "Recommend the best 1-3 options in plain language, say why (price, time, nearby day or airport), " +
                "mention any 'worth a look' tips, and keep it under 120 words. Prices are CHF. No markdown tables.",
            listOf(ChatTurn("user", "My request: $question\n\nResults from the app's live scan:\n$digest")),
        )

    /** IATA from whatever the model wrote: a code, a city or an airport name. Null means anywhere. */
    fun resolvePlace(s: String?): String? {
        val t = s?.trim().orEmpty()
        if (t.isEmpty() || t.equals("ANY", true) || t.equals("anywhere", true)) return null
        return airports.bestMatch(t)
    }

    fun toQuery(i: Intent, base: Query): Query {
        val today = LocalDate.now()
        val from = i.dateFrom?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.coerceAtLeast(today)
        val to = i.dateTo?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val baseWindow = base.origins.firstOrNull()?.window ?: TimeWindow(today.plusDays(1).toString(), today.plusDays(3).toString())
        val window = TimeWindow(
            dateFrom = (from ?: baseWindow.from).toString(),
            dateTo = (to?.takeIf { f -> !f.isBefore(from ?: baseWindow.from) } ?: from ?: baseWindow.to).toString(),
            hourFrom = (i.hourFrom ?: baseWindow.hourFrom).coerceIn(0, 23),
            hourTo = (i.hourTo ?: baseWindow.hourTo).coerceIn(1, 24),
        )
        val codes = i.origins?.mapNotNull { resolvePlace(it) }?.distinct()?.takeIf { it.isNotEmpty() }
            ?: base.origins.map { it.iata }.ifEmpty { listOf("BSL") }
        return base.withDests(listOfNotNull(resolvePlace(i.destination))).copy(
            origins = codes.map { OriginLine(it, window) },
            oneWay = i.oneWay ?: base.oneWay,
            nightsMin = i.nightsMin ?: base.nightsMin,
            nightsMax = i.nightsMax ?: base.nightsMax,
            flexDays = (i.flexDays ?: base.flexDays).coerceIn(0, 7),
            filters = base.filters.copy(
                maxPriceChf = i.maxPriceChf ?: base.filters.maxPriceChf,
                stops = when (i.stops?.uppercase()) {
                    "DIRECT" -> Stops.DIRECT
                    "ONE" -> Stops.ONE
                    "ANY" -> Stops.ANY
                    else -> base.filters.stops
                },
            ),
        )
    }

    /** Written by the app itself when the AI can't be reached: the facts, no prose flourish. */
    fun plainScanSummary(r: ScanResult): String = buildString {
        if (r.itineraries.isEmpty()) { append("No fares matched. Try wider dates or a higher price cap."); return@buildString }
        append("Best options:\n")
        r.itineraries.take(3).forEachIndexed { i, it -> append("${i + 1}. ").append(line(it)).append('\n') }
        r.notices.firstOrNull()?.let { append("\nTip: ${it.title}. ${it.detail}") }
        r.priceLevel?.let { append("\nGoogle rates prices on this route as $it right now.") }
    }

    fun plainDealsSummary(d: DealsResult): String = buildString {
        append("Biggest drops right now:\n")
        d.drops.take(3).forEachIndexed { i, pd ->
            append("${i + 1}. ").append(line(pd.itinerary))
            if (pd.dropPct > 0) append(" (${pd.dropPct}% below usual)")
            append('\n')
        }
        d.countries.firstOrNull()?.let { append("\nCheapest country: ${it.country}, from CHF ${it.cheapest.fare.priceChf.roundToInt()}.") }
    }

    fun scanDigest(r: ScanResult): String = buildString {
        r.priceLevel?.let { appendLine("Google says prices on this route are currently $it.") }
        appendLine("Top itineraries (best trip cost first):")
        r.itineraries.take(8).forEach { appendLine("- " + line(it)) }
        if (r.notices.isNotEmpty()) {
            appendLine("Worth a look:")
            r.notices.forEach { appendLine("- ${it.title}: ${it.detail}") }
        }
        if (r.itineraries.isEmpty()) appendLine("No fares matched.")
    }

    fun dealsDigest(d: DealsResult): String = buildString {
        appendLine("Unusually cheap fares (drop vs the route's usual day):")
        d.drops.take(10).forEach { pd ->
            append("- ").append(line(pd.itinerary))
            if (pd.dropPct > 0) append(", ${pd.dropPct}% below usual ${pd.typicalChf?.roundToInt()} CHF")
            appendLine()
        }
        appendLine("Cheapest countries: " + d.countries.take(6).joinToString { "${it.country} from ${it.cheapest.fare.priceChf.roundToInt()} CHF" })
        val promos = d.promos.filter { it.nearYou }.take(5)
        if (promos.isNotEmpty()) {
            appendLine("Promo fares near home:")
            promos.forEach { appendLine("- ${it.title}") }
        }
    }

    private fun line(it: Itinerary): String {
        val f = it.fare
        return "${f.originAirport}→${f.destinationCity ?: Airports.cityOf(f.destinationAirport)} (${f.destinationAirport}) " +
            "${f.departAt.format(day)} ${"%02d:%02d".format(f.departAt.hour, f.departAt.minute)}, ${Airlines.name(f.airline)}, " +
            "${Recommender.stopsLabel(f.transfers)}, ${Recommender.hm(f.durationMin)}, ${f.priceChf.roundToInt()} CHF" +
            (if (!it.inWindow) " (outside requested dates)" else "")
    }

    private fun intentSystem(s: AppSettings): String {
        val today = LocalDate.now()
        val nearby = Airports.nearby.joinToString { "${it.code} ${it.city} (${s.ground(it.code)} min)" }
        return """
            You help a traveller based in ${Airports.cityOf(s.homeIata)}, Switzerland (home airport ${s.homeIata}) find cheap flights.
            Today is $today (${today.dayOfWeek.name.lowercase()}). Prices are in CHF.
            Airports within reach of home, with ground time: $nearby.
            The app can run two actions with live data from Google Flights, Ryanair and deal feeds:
            - "search": flights from one or more origins to one destination (or anywhere) within a date window.
            - "deals": scan every nearby airport for unusually cheap fares, the cheapest countries and promo/error fares.
            Reply with ONE JSON object and nothing else:
            {"reply": "<one short friendly sentence saying what you'll look for, or a full answer if no action>",
             "action": "search" | "deals" | "none",
             "origins": ["${s.homeIata}"] (IATA; use only the home airport unless the user names another or explicitly says other airports are fine),
             "destination": "<IATA code, or city name, or null for anywhere>",
             "dateFrom": "YYYY-MM-DD", "dateTo": "YYYY-MM-DD" (departure window; resolve words like 'next weekend' or 'in March' to real dates after today),
             "hourFrom": 0-23, "hourTo": 1-24, "oneWay": true|false, "nightsMin": n, "nightsMax": n,
             "flexDays": 0-7, "maxPriceChf": n or null, "stops": "DIRECT"|"ONE"|"ANY",
             "horizonDays": 7-365 (deals only; how far ahead to look, e.g. 180 for six months)}
            Omit fields you don't need. Use "deals" for open-ended bargain hunting ("anything cheap", "error fares", "where can I go for little money").
            Use "none" for general travel questions, and answer them in "reply". Never state prices yourself.
        """.trimIndent()
    }

    companion object {
        private val day = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    }
}
