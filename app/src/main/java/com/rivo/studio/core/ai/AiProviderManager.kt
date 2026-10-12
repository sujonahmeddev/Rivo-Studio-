package com.rivo.studio.core.ai

import com.rivo.studio.core.model.ChatTurn
import com.rivo.studio.core.model.ProviderConfig
import com.rivo.studio.core.model.ProviderDiagnostic
import com.rivo.studio.core.model.ProviderKind
import com.rivo.studio.core.security.SensitiveText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

interface AiProviderClient {
    suspend fun listModels(config: ProviderConfig): List<String>
    suspend fun chat(config: ProviderConfig, systemPrompt: String, turns: List<ChatTurn>): String
}

class AiProviderManager {
    private val clients: Map<ProviderKind, AiProviderClient> = ProviderKind.entries.associateWith { kind ->
        when (kind) {
            ProviderKind.GEMINI -> GeminiClient()
            ProviderKind.ANTHROPIC -> AnthropicClient()
            ProviderKind.OPENAI, ProviderKind.OPENROUTER, ProviderKind.CUSTOM -> OpenAiCompatibleClient()
        }
    }

    suspend fun testConnection(config: ProviderConfig): ProviderDiagnostic = try {
        val models = client(config).listModels(config)
        ProviderDiagnostic(
            message = if (models.isEmpty()) "Connected. The provider returned no models for this account." else "Connection verified. ${models.size} model(s) are available.",
            models = models,
            success = true,
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        ProviderDiagnostic(error.message ?: "The provider request failed.", success = false)
    }

    suspend fun listModels(config: ProviderConfig): List<String> = client(config).listModels(config)

    suspend fun chat(config: ProviderConfig, systemPrompt: String, turns: List<ChatTurn>): String {
        require(config.apiKey.isNotBlank()) { "Save an API key for ${config.name} in Settings first." }
        require(config.model.isNotBlank()) { "Choose a model for ${config.name} in Settings first." }
        return client(config).chat(config, systemPrompt, turns)
    }

    private fun client(config: ProviderConfig): AiProviderClient {
        require(config.apiKey.isNotBlank()) { "Add an API key in Settings before connecting to ${config.name}." }
        ProviderEndpointPolicy.validate(config.baseUrl)
        return clients.getValue(config.kind)
    }
}

object ProviderEndpointPolicy {
    fun validate(value: String): String {
        val url = runCatching { URL(value.trim().trimEnd('/')) }
            .getOrElse { throw IllegalArgumentException("Enter a valid HTTPS API base URL.") }
        require(url.protocol.equals("https", ignoreCase = true)) {
            "AI endpoints must use HTTPS so credentials are protected in transit."
        }
        require(url.host.isNotBlank() && url.userInfo == null && url.query == null && url.ref == null) {
            "The API base URL must not contain credentials, query parameters, or a fragment."
        }
        return url.toExternalForm().trimEnd('/')
    }
}

private class OpenAiCompatibleClient : AiProviderClient {
    override suspend fun listModels(config: ProviderConfig): List<String> {
        val response = HttpTransport.request(
            method = "GET",
            url = "${ProviderEndpointPolicy.validate(config.baseUrl)}/models",
            headers = mapOf("Authorization" to "Bearer ${config.apiKey}"),
            apiKey = config.apiKey,
        )
        val data = JSONObject(response).optJSONArray("data") ?: JSONArray()
        return (0 until data.length()).mapNotNull { data.optJSONObject(it)?.optString("id") }
            .filter(String::isNotBlank).distinct().sorted()
    }

    override suspend fun chat(config: ProviderConfig, systemPrompt: String, turns: List<ChatTurn>): String {
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", systemPrompt))
        turns.forEach { turn ->
            messages.put(JSONObject().put("role", if (turn.role == "assistant") "assistant" else "user").put("content", turn.content))
        }
        val payload = JSONObject()
            .put("model", config.model)
            .put("messages", messages)
            .put("temperature", 0.3)
        val response = HttpTransport.request(
            method = "POST",
            url = "${ProviderEndpointPolicy.validate(config.baseUrl)}/chat/completions",
            headers = mapOf("Authorization" to "Bearer ${config.apiKey}"),
            body = payload.toString(),
            apiKey = config.apiKey,
        )
        val choices = JSONObject(response).optJSONArray("choices")
            ?: throw IllegalStateException("The provider response did not contain any choices.")
        val message = choices.optJSONObject(0)?.optJSONObject("message")
            ?: throw IllegalStateException("The provider response did not contain a message.")
        return message.optString("content").takeIf(String::isNotBlank)
            ?: throw IllegalStateException("The provider returned an empty response. Check the model's text-chat support.")
    }
}

private class GeminiClient : AiProviderClient {
    override suspend fun listModels(config: ProviderConfig): List<String> {
        val url = "${ProviderEndpointPolicy.validate(config.baseUrl)}/models?key=${encode(config.apiKey)}"
        val response = HttpTransport.request("GET", url, apiKey = config.apiKey)
        val models = JSONObject(response).optJSONArray("models") ?: JSONArray()
        return (0 until models.length()).mapNotNull { index ->
            val item = models.optJSONObject(index) ?: return@mapNotNull null
            val methods = item.optJSONArray("supportedGenerationMethods") ?: JSONArray()
            val supportsGenerate = (0 until methods.length()).any { methods.optString(it) == "generateContent" }
            item.optString("name").takeIf { supportsGenerate && it.isNotBlank() }
        }.distinct().sorted()
    }

    override suspend fun chat(config: ProviderConfig, systemPrompt: String, turns: List<ChatTurn>): String {
        val model = config.model.removePrefix("models/")
        val contents = JSONArray()
        turns.forEach { turn ->
            contents.put(
                JSONObject()
                    .put("role", if (turn.role == "assistant") "model" else "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", turn.content))),
            )
        }
        val payload = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("temperature", 0.3).put("maxOutputTokens", 2048))
        val url = "${ProviderEndpointPolicy.validate(config.baseUrl)}/models/${encodePath(model)}:generateContent?key=${encode(config.apiKey)}"
        val response = HttpTransport.request("POST", url, body = payload.toString(), apiKey = config.apiKey)
        val candidates = JSONObject(response).optJSONArray("candidates")
            ?: throw IllegalStateException("Gemini did not return a response candidate.")
        val parts = candidates.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            ?: throw IllegalStateException("Gemini returned an empty response.")
        return (0 until parts.length()).mapNotNull { parts.optJSONObject(it)?.optString("text") }
            .joinToString("").takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Gemini returned an empty text response.")
    }
}

private class AnthropicClient : AiProviderClient {
    private val headers get() = mapOf("anthropic-version" to "2023-06-01")

    override suspend fun listModels(config: ProviderConfig): List<String> {
        val response = HttpTransport.request(
            method = "GET",
            url = "${ProviderEndpointPolicy.validate(config.baseUrl)}/models?limit=100",
            headers = headers + ("x-api-key" to config.apiKey),
            apiKey = config.apiKey,
        )
        val data = JSONObject(response).optJSONArray("data") ?: JSONArray()
        return (0 until data.length()).mapNotNull { data.optJSONObject(it)?.optString("id") }
            .filter(String::isNotBlank).distinct().sorted()
    }

    override suspend fun chat(config: ProviderConfig, systemPrompt: String, turns: List<ChatTurn>): String {
        val messages = JSONArray()
        turns.forEach { turn ->
            messages.put(
                JSONObject()
                    .put("role", if (turn.role == "assistant") "assistant" else "user")
                    .put("content", turn.content),
            )
        }
        val payload = JSONObject()
            .put("model", config.model)
            .put("max_tokens", 2048)
            .put("system", systemPrompt)
            .put("messages", messages)
        val response = HttpTransport.request(
            method = "POST",
            url = "${ProviderEndpointPolicy.validate(config.baseUrl)}/messages",
            headers = headers + ("x-api-key" to config.apiKey),
            body = payload.toString(),
            apiKey = config.apiKey,
        )
        val blocks = JSONObject(response).optJSONArray("content") ?: JSONArray()
        return (0 until blocks.length()).mapNotNull { index ->
            val block = blocks.optJSONObject(index) ?: return@mapNotNull null
            if (block.optString("type") == "text") block.optString("text") else null
        }.joinToString("\n").takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Anthropic returned no text content.")
    }
}

private object HttpTransport {
    suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        apiKey: String,
    ): String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        val cancellation = currentCoroutineContext().job.invokeOnCompletion { cause ->
            if (cause is CancellationException) connection.disconnect()
        }
        try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", "RivoStudio/0.1")
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(MAX_RESPONSE_CHARS) }.orEmpty()
            if (status !in 200..299) {
                throw IllegalStateException(formatHttpError(status, responseText, apiKey))
            }
            responseText
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (error is IllegalStateException && error.message?.startsWith("HTTP ") == true) throw error
            val safeMessage = redact(error.message ?: "Network request failed.", apiKey)
            throw IllegalStateException("Network request failed: $safeMessage")
        } finally {
            cancellation.dispose()
            connection.disconnect()
        }
    }

    private fun formatHttpError(status: Int, body: String, apiKey: String): String {
        val message = runCatching {
            val json = JSONObject(body)
            json.optJSONObject("error")?.optString("message")
                ?: json.optString("message").takeIf(String::isNotBlank)
        }.getOrNull()
        val summary = when (status) {
            401, 403 -> "The provider rejected the API key or account permissions."
            404 -> "The endpoint or model was not found. Check the API base URL and model name."
            429 -> "The provider rate limit or account quota was reached. Try again later or check billing."
            in 500..599 -> "The AI provider is temporarily unavailable."
            else -> message?.take(240) ?: "The provider returned an HTTP error."
        }
        return "HTTP $status — ${redact(summary, apiKey)}"
    }

    private fun redact(text: String, apiKey: String): String = SensitiveText
        .redact(text, listOf(apiKey))
        .replace(Regex("https?://[^\\s?]+\\?key=[^&\\s]+", RegexOption.IGNORE_CASE), "[provider URL redacted]")

    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 90_000
    private const val MAX_RESPONSE_CHARS = 1_000_000
}

private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
private fun encodePath(value: String): String = value.split('/').joinToString("/") { encode(it) }
