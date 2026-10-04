package com.ebad.flightfilters.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Promo and error fares hand-picked by Fly4free's European desk, read from its public RSS feed.
 * These are real published deals; the app only flags which ones leave from near Basel.
 */
class DealsFeed(private val http: CachedHttp) {

    suspend fun europe(): List<PromoDeal> {
        val body = http.get(
            "https://www.fly4free.com/flights/flight-deals/europe/feed/",
            mapOf("User-Agent" to "Mozilla/5.0 (Linux; Android 14) FlightFilters RSS reader"),
            ttlMillis = 60 * 60 * 1000L,
        )
        return parse(body.text)
    }

    private fun parse(xml: String): List<PromoDeal> {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(StringReader(xml))
        val out = mutableListOf<PromoDeal>()
        var inItem = false
        var title = ""; var link = ""; var date = ""; var desc = ""
        val cats = mutableListOf<String>()
        var tag: String? = null
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> {
                    tag = p.name
                    if (p.name == "item") { inItem = true; title = ""; link = ""; date = ""; desc = ""; cats.clear() }
                }
                XmlPullParser.TEXT -> if (inItem) when (tag) {
                    "title" -> title += p.text
                    "link" -> link += p.text
                    "pubDate" -> date += p.text
                    "description" -> desc += p.text
                    "category" -> cats += p.text
                }
                XmlPullParser.END_TAG -> {
                    if (p.name == "item") {
                        inItem = false
                        out += toDeal(title.trim(), link.trim(), date.trim(), desc.trim(), cats.toList())
                    }
                    tag = null
                }
            }
        }
        return out
    }

    private fun toDeal(title: String, link: String, date: String, desc: String, cats: List<String>): PromoDeal {
        val haystack = (title + " " + cats.joinToString(" ")).lowercase()
        val price = priceRegex.find(title)?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }
            ?.replace(",", "")?.replace(".", "")?.toIntOrNull()
        val published = runCatching { ZonedDateTime.parse(date, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
            .getOrDefault(System.currentTimeMillis())
        return PromoDeal(
            title = title.replace("&#038;", "&").replace("&amp;", "&"),
            link = link,
            publishedAt = published,
            priceEur = price,
            summary = desc.replace(Regex("<[^>]+>"), "").replace("&#8217;", "'").trim().take(220),
            nearYou = nearby.any { it in haystack },
            errorFare = "error fare" in haystack || "mistake" in haystack,
            site = "Fly4free",
        )
    }

    companion object {
        private val priceRegex = Regex("€\\s?([\\d.,]+)|([\\d.,]+)\\s?€|EUR\\s?([\\d.,]+)")
        /** Departure places within reach of Basel, as they appear in deal titles and tags. */
        private val nearby = listOf(
            "switzerland", "swiss", "basel", "zurich", "zürich", "geneva", "bern",
            "germany", "frankfurt", "stuttgart", "munich", "karlsruhe", "memmingen", "baden",
            "france", "paris", "lyon", "strasbourg", "mulhouse", "milan", "bergamo",
        )
    }
}
