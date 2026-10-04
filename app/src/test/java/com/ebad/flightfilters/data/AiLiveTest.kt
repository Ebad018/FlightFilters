package com.ebad.flightfilters.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Live smoke test against the default free, keyless provider (skipped unless -Dlive=true).
 * Checks the model returns the intent JSON the Assistant expects.
 */
class AiLiveTest {
    @Test
    fun pollinationsReturnsSearchIntent() = runBlocking {
        assumeTrue(System.getProperty("live") == "true")
        val system = """
            Today is 2026-10-02. Home airport BSL (Basel). Reply with ONE JSON object and nothing else:
            {"reply": "...", "action": "search"|"deals"|"none", "origins": ["BSL"], "destination": "<IATA or city>",
             "dateFrom": "YYYY-MM-DD", "dateTo": "YYYY-MM-DD", "maxPriceChf": n}
        """.trimIndent()
        val raw = AiClient().complete(AppSettings(), system, listOf(ChatTurn("user", "Cheap flight to Lisbon next Friday under 150 francs")))
        println("keyless ai: $raw")
        val obj = Json { ignoreUnknownKeys = true; isLenient = true }
            .decodeFromString(Assistant.Intent.serializer(), raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1))
        println("intent: $obj")
        assertTrue(obj.action == "search")
        assertTrue(obj.destination?.contains("LIS", true) == true || obj.destination?.contains("Lisbon", true) == true)
    }
}
