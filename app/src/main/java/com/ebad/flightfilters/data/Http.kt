package com.ebad.flightfilters.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class HttpException(val code: Int, message: String) : IOException(message)

/**
 * GET with a disk cache keyed by URL. The free tiers are the budget, so a response
 * younger than [ttlMillis] is never re-requested.
 */
class CachedHttp(private val cacheDir: File) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class Body(val text: String, val fetchedAt: Long)

    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        ttlMillis: Long,
    ): Body = withContext(Dispatchers.IO) {
        val file = File(cacheDir, sha1(url) + ".json")
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < ttlMillis) {
            return@withContext Body(file.readText(), file.lastModified())
        }
        val request = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw HttpException(response.code, text.take(300))
            cacheDir.mkdirs()
            file.writeText(text)
            Body(text, file.lastModified())
        }
    }

    fun clear() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }
    }
}
