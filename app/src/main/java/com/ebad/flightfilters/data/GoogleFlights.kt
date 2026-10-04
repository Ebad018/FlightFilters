package com.ebad.flightfilters.data

import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.MonthDay
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Reads live fares from the public Google Flights results page; no key, no account.
 * Every result carries a plain-English accessibility label ("From 123 Swiss francs. Nonstop flight
 * with easyJet. Leaves … at 12:05 PM on Thursday, November 12 …"), which is what this parses.
 * If Google changes that wording, [search] returns an empty list rather than wrong prices.
 */
class GoogleFlights(private val http: CachedHttp) {

    data class Result(val fares: List<Fare>, val priceLevel: String?, val url: String)

    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Safari/537.36",
        "Accept-Language" to "en-US,en;q=0.9",
        // The standard "accepted" consent cookie, so EU/CH visitors get results instead of the consent page.
        "Cookie" to "CONSENT=YES+cb; SOCS=CAESEwgDEgk0ODE3Nzk3MjQaAmVuIAEaBgiA_LyaBg",
    )

    fun url(origin: String, destination: String, date: LocalDate, returnDate: LocalDate?): String {
        val q = if (returnDate != null) "Flights to $destination from $origin on $date through $returnDate"
        else "Flights to $destination from $origin on $date oneway"
        return "https://www.google.com/travel/flights?hl=en&gl=CH&curr=CHF&q=" + URLEncoder.encode(q, "UTF-8")
    }

    suspend fun search(origin: String, destination: String, date: LocalDate, returnDate: LocalDate? = null): Result {
        val url = url(origin, destination, date, returnDate)
        val body = http.get(url, headers, ttlMillis = 30 * 60 * 1000L)
        val html = body.text
        if (!html.contains("aria-label=\"From ") && html.contains("consent.google")) {
            throw HttpException(0, "Google asked for cookie consent; try again later")
        }
        val fares = labelRegex.findAll(html).mapNotNull { m ->
            parse(clean(m.groupValues[1]), clean(m.groupValues[2]), origin, destination, date, returnDate, url, body.fetchedAt)
        }.distinctBy { it.key }.toList()
        val level = levelRegex.find(html)?.groupValues?.get(1)
        return Result(fares, level, url)
    }

    private fun parse(
        price: String, rest: String, origin: String, destination: String,
        date: LocalDate, returnDate: LocalDate?, url: String, seenAt: Long,
    ): Fare? {
        val chf = price.replace(",", "").toDoubleOrNull() ?: return null
        val stopsM = stopsRegex.find(rest) ?: return null
        val transfers = if (stopsM.groupValues[1] == "Nonstop") 0 else stopsM.groupValues[2].toIntOrNull() ?: 1
        val airline = stopsM.groupValues[3].substringBefore(" and ").substringBefore(",").trim()
        val legM = legRegex.find(rest) ?: return null
        val dep = at(legM.groupValues[3], legM.groupValues[2], date) ?: return null
        val durM = durationRegex.find(rest)
        val minutes = durM?.let { (it.groupValues[1].toIntOrNull() ?: 0) * 60 + (it.groupValues[2].toIntOrNull() ?: 0) } ?: return null
        val layovers = layoverRegex.findAll(rest).map { it.groupValues[2].trim() }.toList()
        return Fare(
            origin = origin,
            destination = destination,
            originAirport = origin,
            destinationAirport = destination,
            departAt = dep.atZone(ZURICH).toOffsetDateTime(),
            returnAt = returnDate?.atTime(12, 0)?.atZone(ZURICH)?.toOffsetDateTime(),
            priceChf = chf,
            airline = Airlines.codeFor(airline),
            flightNumber = "",
            transfers = transfers,
            returnTransfers = if (returnDate != null) transfers else 0,
            durationMin = minutes,
            bookingUrl = url,
            source = FareSource.GOOGLE_LIVE,
            seenAt = seenAt,
            destinationCity = Airports.cityOf(destination),
            layovers = layovers,
        )
    }

    /** "November 12" + "2:10 PM", with the year taken from the searched date (rolling into January). */
    private fun at(monthDay: String, time: String, searched: LocalDate): LocalDateTime? = runCatching {
        val md = MonthDay.parse(monthDay, DateTimeFormatter.ofPattern("MMMM d", Locale.ENGLISH))
        var d = md.atYear(searched.year)
        if (d.isBefore(searched.minusDays(2))) d = md.atYear(searched.year + 1)
        val t = LocalTime.parse(time.uppercase(), DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
        d.atTime(t)
    }.getOrNull()

    private fun clean(s: String): String = s
        .replace(' ', ' ').replace(' ', ' ')
        .replace("&#39;", "'").replace("&amp;", "&").replace("&quot;", "\"")

    companion object {
        private val ZURICH: ZoneId = ZoneId.of("Europe/Zurich")
        private val labelRegex = Regex("aria-label=\"From ([\\d,.]+) [^\".]*?\\.([^\"]*?)Select flight\"")
        private val stopsRegex = Regex("(Nonstop|(\\d+) stops?) flight with ([^.]+)\\.")
        private val legRegex = Regex("Leaves (.+?) at (\\d{1,2}:\\d{2}[\\s\\u202f]?[AP]M) on \\w+, (\\w+ \\d{1,2}) and arrives at (.+?) at (\\d{1,2}:\\d{2}[\\s\\u202f]?[AP]M) on \\w+, (\\w+ \\d{1,2})\\.")
        private val durationRegex = Regex("Total duration (?:(\\d+) hr)?\\s*(?:(\\d+) min)?")
        private val layoverRegex = Regex("layover at (.+?) in ([^.]+)\\.")
        private val levelRegex = Regex("Prices are currently\\s*(?:<[^>]*>\\s*)*(low|typical|high)")
    }
}
