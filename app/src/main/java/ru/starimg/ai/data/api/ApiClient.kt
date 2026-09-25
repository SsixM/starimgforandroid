package ru.starimg.ai.data.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Call
import okhttp3.Response
import ru.starimg.ai.data.model.AiModel
import ru.starimg.ai.data.model.ApiResult
import ru.starimg.ai.data.model.ChatMessage
import ru.starimg.ai.data.model.ContextWindow
import ru.starimg.ai.data.model.TokenUsage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.longOrNull
import ru.starimg.ai.data.model.AccountBalance
import ru.starimg.ai.data.model.UsageLog
import ru.starimg.ai.data.model.fitContext
import java.util.concurrent.TimeUnit
import java.util.UUID

class ApiClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val newsHttp = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS).followRedirects(false).build()
    @Volatile private var newsCall: Call? = null

    @Volatile private var current: Call? = null

    /** Aborts the request in flight so the user can send again. */
    fun cancel() { current?.cancel() }

    fun loadNews(): List<ru.starimg.ai.data.model.SiteNews> {
        val request = Request.Builder().url("https://ai.starimg.ru/news/api").get().build()
        val call = newsHttp.newCall(request)
        newsCall = call
        try { call.execute().use { response ->
            if (!response.isSuccessful) error("Новости недоступны (${response.code}).")
            return parseNewsResponse(response.body?.string().orEmpty()).filter { it.status.equals("published", true) }
        } } finally { if (newsCall === call) newsCall = null }
    }
    fun cancelNews() { newsCall?.cancel() }

    /**
     * Streams a reply. [onDelta] is called on the calling thread for every chunk of text.
     * Returns the final text plus usage, which only arrives with the last event.
     */
    fun send(
        endpoint: String, key: String, model: String, history: List<ChatMessage>, system: String,
        reasoningMode: String? = null,
        reasoningParameters: Set<String>? = null,
        onDelta: (String) -> Unit = {}
    ): ApiResult {
        val attemptId = UUID.randomUUID().toString()
        require(key.isNotBlank()) { "Добавьте API-ключ в настройках." }
        val window = fitContext(history, system)
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", 4096)
            put("stream", true)
            if (system.isNotBlank()) put("system", system)
            put("messages", messagesJson(window))
            reasoningMode?.takeIf { reasoningParameters?.contains(it) == true }?.let { mode ->
                put("reasoning_effort", mode)
            }
        }.toString()
        val request = Request.Builder()
            .url(endpoint.trimEnd('/') + "/messages")
            .addHeader("x-api-key", key)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("accept", "text/event-stream")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        val call = http.newCall(request)
        current = call
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) fail(response)
                return if (response.header("content-type").orEmpty().contains("text/event-stream")) {
                    readStream(response, onDelta)
                } else {
                    // A proxy that ignores `stream` answers with one JSON document.
                    val raw = response.body?.string().orEmpty()
                    val root = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull()
                    val text = parseReplyText(root, raw)
                    if (text.isNotEmpty()) onDelta(text)
                    ApiResult(text, parseUsage(root), parseUsage(root).available, attemptId, root?.requestId())
                }
            }
        } finally {
            if (current === call) current = null
        }
    }

    /** Walks the SSE events. Anthropic sends deltas; OpenAI sends cumulative chunks. */
    private fun readStream(response: Response, onDelta: (String) -> Unit): ApiResult {
        val source = response.body?.source() ?: error("Пустой ответ сервера.")
        val text = StringBuilder()
        var seen = ""
        var usage = TokenUsage()
        var requestId: String? = null
        val attemptId = UUID.randomUUID().toString()
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith("data:")) continue
            val data = line.removePrefix("data:").trim()
            if (data.isEmpty() || data == "[DONE]") continue
            val event = runCatching { Json.parseToJsonElement(data).jsonObject }.getOrNull() ?: continue
            requestId = requestId ?: event.requestId() ?: event["message"]?.jsonObject?.requestId()
            event["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content?.let { error("Ошибка API: $it") }
            val piece = streamDelta(event)
            if (piece.isNotEmpty()) {
                // OpenAI streams the whole reply so far; Anthropic streams only the new part.
                val addition = if (piece.startsWith(seen) && piece.length >= seen.length) piece.removePrefix(seen) else piece
                seen = if (piece.startsWith(seen)) piece else seen + piece
                if (addition.isNotEmpty()) { text.append(addition); onDelta(addition) }
            }
            val reported = parseUsage(event)
            if (reported.available) usage = reported
            event["message"]?.jsonObject?.let { parseUsage(it) }?.takeIf { it.available }?.let { usage = it }
        }
        return ApiResult(text.toString(), usage, usage.available, attemptId, requestId)
    }

    private fun fail(response: Response): Nothing {
        val raw = response.body?.string().orEmpty()
        val detail = runCatching {
            Json.parseToJsonElement(raw).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
        }.getOrNull()
        error("Ошибка API (${response.code}): ${detail ?: raw.take(240)}")
    }

    private fun messagesJson(window: ContextWindow) = buildJsonArray {
        window.messages.forEach { message ->
            add(buildJsonObject {
                put("role", if (message.user) "user" else "assistant")
                val photos = message.photos
                if (photos.isEmpty()) put("content", message.shownText) else put("content", buildJsonArray {
                    photos.forEach { photo ->
                        add(buildJsonObject {
                            put("type", "image")
                            put("source", buildJsonObject {
                                put("type", "base64")
                                put("media_type", photo.mime)
                                put("data", photo.data)
                            })
                        })
                    }
                    add(buildJsonObject { put("type", "text"); put("text", message.shownText) })
                })
            })
        }
    }

    /** GET {endpoint}/models. HTTP errors are surfaced, not disguised as an empty catalog. */
    fun loadModels(endpoint: String, key: String): List<AiModel> {
        val request = Request.Builder().url(endpoint.trimEnd('/') + "/models").apply { if (key.isNotBlank()) addHeader("x-api-key", key) }.get().build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) fail(response)
            return parseModelCatalog(response.body?.string().orEmpty())
        }
    }

    /**
     * Account balance and recent requests from the site's telemetry endpoint.
     * [accessToken] is the short tokens_access_key cookie, not the API key.
     */
    fun loadTelemetry(endpoint: String, accessToken: String): Telemetry {
        require(accessToken.isNotBlank()) { "Добавьте access token в настройках." }
        val base = endpoint.trimEnd('/').removeSuffix("/v1")
        val request = Request.Builder()
            .url("$base/telemetry?logs=1")
            .addHeader("accept", "*/*")
            .addHeader("cookie", "tokens_access_key=$accessToken")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403) error("Access token не подошёл — проверьте его в настройках.")
            if (!response.isSuccessful) error("Не удалось получить баланс (${response.code}).")
            val root = Json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
            return parseTelemetry(response.body?.string().orEmpty())
        }
    }
}

fun parseNewsResponse(raw: String) = parseNews(raw)

data class Telemetry(val balance: AccountBalance, val logs: List<UsageLog>)

private fun kotlinx.serialization.json.JsonObject.requestId(): String? =
    listOf("request_id", "requestId", "id").firstNotNullOfOrNull { this[it]?.jsonPrimitive?.content }
