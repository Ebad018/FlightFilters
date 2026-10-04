package com.ebad.flightfilters.data

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
enum class AiProvider(
    val label: String,
    val free: Boolean,
    val needsKey: Boolean,
    val endpoint: String,
    val defaultModel: String,
    val keyUrl: String,
) {
    LLM7("LLM7 (free, no key)", true, false, "https://api.llm7.io/v1/chat/completions", "default", "https://token.llm7.io"),
    POLLINATIONS("Pollinations (free, no key, often busy)", true, false, "https://text.pollinations.ai/openai", "openai", "https://enter.pollinations.ai"),
    GEMINI("Google Gemini (free key)", true, true, "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", "gemini-flash-latest", "https://aistudio.google.com/apikey"),
    GROQ("Groq (free key)", true, true, "https://api.groq.com/openai/v1/chat/completions", "openai/gpt-oss-20b", "https://console.groq.com/keys"),
    OPENROUTER("OpenRouter free models (free key)", true, true, "https://openrouter.ai/api/v1/chat/completions", "openrouter/free", "https://openrouter.ai/keys"),
    OPENAI("OpenAI (paid key)", false, true, "https://api.openai.com/v1/chat/completions", "gpt-5-mini", "https://platform.openai.com/api-keys"),
    ANTHROPIC("Claude by Anthropic (paid key)", false, true, "", "claude-opus-5-5", "https://console.anthropic.com/settings/keys"),
    CUSTOM("Custom OpenAI-compatible", false, false, "", "", ""),
}

data class ChatTurn(val role: String, val text: String) // role: "user" | "assistant"

/**
 * One call surface over every provider. Most speak the OpenAI chat-completions shape;
 * Claude goes through the official Anthropic Java SDK.
 */
class AiClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    @Serializable private data class Msg(val role: String, val content: String)
    @Serializable private data class Req(val model: String, val messages: List<Msg>, val temperature: Double? = null)
    @Serializable private data class Resp(val choices: List<Choice> = emptyList(), val error: Err? = null)
    @Serializable private data class Choice(val message: RMsg? = null)
    @Serializable private data class RMsg(val content: String? = null)
    @Serializable private data class Err(val message: String? = null)

    class AiException(message: String) : Exception(message)

    /**
     * Ask the selected provider. A keyless provider that fails (they're best-effort) falls back to
     * the other keyless one before giving up.
     */
    suspend fun complete(settings: AppSettings, system: String, turns: List<ChatTurn>): String = withContext(Dispatchers.IO) {
        val p = settings.aiProvider
        try {
            completeWith(settings, p, system, turns)
        } catch (e: AiException) {
            val other = when (p) { AiProvider.LLM7 -> AiProvider.POLLINATIONS; AiProvider.POLLINATIONS -> AiProvider.LLM7; else -> null }
                ?: throw e
            runCatching { completeWith(settings, other, system, turns) }.getOrElse { throw e }
        }
    }

    private fun completeWith(settings: AppSettings, p: AiProvider, system: String, turns: List<ChatTurn>): String {
        val key = settings.aiKey(p)
        if (p.needsKey && key.isBlank()) throw AiException("Add your ${p.label} key in Settings → AI assistant, or switch to the free LLM7 option.")
        return when (p) {
            AiProvider.ANTHROPIC -> claude(key, settings.aiModel(p), system, turns)
            else -> openAiCompatible(
                url = if (p == AiProvider.CUSTOM) settings.aiCustomUrl.trimEnd('/').let { if (it.endsWith("/chat/completions")) it else "$it/chat/completions" } else p.endpoint,
                key = key,
                model = settings.aiModel(p).ifBlank { "default" },
                system = system,
                turns = turns,
                extraHeaders = if (p == AiProvider.OPENROUTER) mapOf("X-Title" to "FlightFilters") else emptyMap(),
            )
        }
    }

    private fun openAiCompatible(url: String, key: String, model: String, system: String, turns: List<ChatTurn>, extraHeaders: Map<String, String>): String {
        if (url.isBlank() || !url.startsWith("http")) throw AiException("Set the custom endpoint URL in Settings → AI assistant.")
        val body = json.encodeToString(
            Req.serializer(),
            Req(model, listOf(Msg("system", system)) + turns.map { Msg(it.role, it.text) }),
        )
        val req = Request.Builder().url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .apply {
                if (key.isNotBlank()) header("Authorization", "Bearer $key")
                extraHeaders.forEach { (k, v) -> header(k, v) }
            }
            .build()
        http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            val parsed = runCatching { json.decodeFromString(Resp.serializer(), text) }.getOrNull()
            if (!r.isSuccessful) {
                throw AiException("${r.code}: " + (parsed?.error?.message ?: text.take(200)).ifBlank { "request failed" })
            }
            return parsed?.choices?.firstOrNull()?.message?.content?.trim()?.takeIf { it.isNotEmpty() }
                ?: throw AiException("The model returned an empty answer.")
        }
    }

    private fun claude(key: String, model: String, system: String, turns: List<ChatTurn>): String {
        val client = AnthropicOkHttpClient.builder().apiKey(key).build()
        val params = MessageCreateParams.builder()
            .model(model)
            .maxTokens(16000L)
            .system(system)
            // Short conversational turns: low effort keeps replies quick and cheap.
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
            // Server-side fallback when a safety classifier declines a request.
            .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            .apply {
                turns.forEach { t -> if (t.role == "assistant") addAssistantMessage(t.text) else addUserMessage(t.text) }
            }
            .build()
        val response = try {
            client.messages().create(params)
        } catch (e: com.anthropic.errors.UnauthorizedException) {
            throw AiException("Claude rejected the API key. Check it in Settings → AI assistant.")
        } catch (e: com.anthropic.errors.RateLimitException) {
            throw AiException("Claude rate limit reached; try again in a moment.")
        } catch (e: com.anthropic.errors.AnthropicServiceException) {
            throw AiException("Claude error ${e.statusCode()}: ${e.message}")
        } catch (e: com.anthropic.errors.AnthropicIoException) {
            throw AiException("Couldn't reach Claude: ${e.message}")
        }
        if (response.stopReason().orElse(null) == StopReason.REFUSAL) {
            throw AiException("Claude declined this request.")
        }
        return response.content().mapNotNull { block -> block.text().orElse(null)?.text() }
            .joinToString("").trim()
            .ifEmpty { throw AiException("Claude returned an empty answer.") }
    }
}
