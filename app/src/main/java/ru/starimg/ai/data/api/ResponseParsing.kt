package ru.starimg.ai.data.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import ru.starimg.ai.data.model.AiModel
import ru.starimg.ai.data.model.ModelPricing
import ru.starimg.ai.data.model.TokenUsage
import ru.starimg.ai.data.model.SiteNews
import ru.starimg.ai.data.model.NewsState
import ru.starimg.ai.data.model.UsageSource
import java.io.IOException

/** Reads either Anthropic or OpenAI usage shapes. Missing usage is an empty [TokenUsage]. */
fun parseUsage(root: JsonObject?): TokenUsage {
    val usageJson = root?.get("usage")?.jsonObject ?: return TokenUsage()
    return TokenUsage(
        input = usageJson.longValue("input_tokens", "prompt_tokens", "inputTokenCount") ?: 0,
        output = usageJson.longValue("output_tokens", "completion_tokens", "outputTokenCount") ?: 0,
        reportedTotal = usageJson.longValue("total_tokens", "totalTokenCount") ?: 0
    )
}

fun parseNews(raw: String): List<SiteNews> {
    val root = runCatching { Json.parseToJsonElement(raw) }.getOrNull() ?: return emptyList()
    val entries = runCatching { root.jsonObject["posts"]?.jsonArray }.getOrNull()
        ?: runCatching { root.jsonArray }.getOrNull()
        ?: return emptyList()
    return entries.mapNotNull { element -> runCatching {
        val item = element.jsonObject
        val id = item.string("id") ?: return@runCatching null
        SiteNews(id, item.string("status") ?: "unknown", item.string("tag"), item.string("tagLabel"),
            item.string("tagColor"), item.string("title") ?: "", item.string("titleEn"),
            item.string("text") ?: "", item.string("textEn"),
            item["imgs"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
            item["pinned"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false)
    }.getOrNull() }
}

fun parseTelemetry(raw: String): Telemetry {
    val root = Json.parseToJsonElement(raw).jsonObject
    val balance = root["balance"]?.jsonObject ?: error("Telemetry response has no balance")
    val logsObject = root["logs"]?.jsonObject
    val logs = logsObject?.get("logs") as? JsonArray
    return Telemetry(
        ru.starimg.ai.data.model.AccountBalance(balance.long("remaining_tokens"), balance.long("token_limit"), balance.long("used_tokens"), balance["stale"]?.jsonPrimitive?.content == "true"),
        logs.orEmpty().mapNotNull { runCatching {
            val item = it.jsonObject
            ru.starimg.ai.data.model.UsageLog(item.str("model"), item.long("input_tokens"), item.long("output_tokens"), item.long("cached_tokens"), item.long("cost_tokens"), item.long("latency_ms"), item.str("status"), item.str("error_message"), item.str("created_at"), item.requestId())
        }.getOrNull() }
    )
}

private fun JsonObject.long(name: String): Long = this[name]?.jsonPrimitive?.longOrNull ?: this[name]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
private fun JsonObject.str(name: String): String = this[name]?.jsonPrimitive?.contentOrNull.orEmpty()
private fun JsonObject.requestId(): String? = listOf("request_id", "requestId", "id").firstNotNullOfOrNull { this[it]?.jsonPrimitive?.contentOrNull }

/**
 * Every reply shape the proxies in front of this app have been seen to return:
 * Anthropic `content[]`, OpenAI `choices[].message.content` (a string or parts),
 * `choices[].text`, a bare top-level `text`, and Gemini `candidates[].content.parts`.
 * When none of those parse, [textByPattern] still lifts the reply out of the raw body,
 * so an unfamiliar shape can never come back blank while tokens were billed.
 */
fun parseReplyText(root: JsonObject?, raw: String): String = runCatching {
    anthropicText(root) ?: openAiText(root) ?: geminiText(root) ?: plainText(root) ?: textByPattern(raw)
}.getOrDefault(textByPattern(raw))

private fun anthropicText(root: JsonObject?): String? =
    root?.get("content")?.jsonArray
        ?.filter { it.jsonObject["type"]?.jsonPrimitive?.content == "text" }
        ?.joinToString("\n") { it.jsonObject["text"]!!.jsonPrimitive.content }
        ?.takeIf { it.isNotEmpty() }

private fun openAiText(root: JsonObject?): String? =
    root?.get("choices")?.jsonArray?.firstOrNull()?.jsonObject?.let { choice ->
        choice["message"]?.jsonObject?.get("content")?.let(::partsText) ?: choice["text"]?.jsonPrimitive?.contentOrNull
    }?.takeIf { it.isNotEmpty() }

private fun geminiText(root: JsonObject?): String? =
    root?.get("candidates")?.jsonArray?.firstOrNull()?.jsonObject
        ?.get("content")?.jsonObject?.get("parts")?.jsonArray
        ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
        ?.joinToString("\n")
        ?.takeIf { it.isNotEmpty() }

private fun plainText(root: JsonObject?): String? =
    root?.get("text")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

/**
 * The new text inside one SSE event: an Anthropic `content_block_delta`,
 * an OpenAI chunk, or a Gemini candidate. Empty when the event carries no text.
 */
fun streamDelta(event: JsonObject): String = runCatching {
    when (event["type"]?.jsonPrimitive?.contentOrNull) {
        "content_block_delta" -> event["delta"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
        "content_block_start" -> event["content_block"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
        else -> null
    } ?: event["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("delta")?.jsonObject
        ?.get("content")?.jsonPrimitive?.contentOrNull
    ?: event["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
        ?.get("content")?.jsonObject?.get("parts")?.jsonArray
        ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
        ?.joinToString("")?.takeIf { it.isNotEmpty() }
    ?: ""
}.getOrDefault("")

/** A content field is either a plain string or a list of typed parts. */
private fun partsText(content: kotlinx.serialization.json.JsonElement): String =
    content.jsonPrimitive.contentOrNull
        ?: content.jsonArray.mapNotNull { part ->
            val item = part.jsonObject
            if (item["type"]?.jsonPrimitive?.content == "text" || item.containsKey("text")) item["text"]?.jsonPrimitive?.contentOrNull else null
        }.joinToString("\n")

/** Last resort: the first `"content"` or `"text"` string value anywhere in the body. */
private fun textByPattern(raw: String): String =
    Regex(""""(?:content|text)"\s*:\s*"((?:[^"\\]|\\.)*)"""").findAll(raw)
        .map { it.groupValues[1].replace(Regex("""\\(["\\/bfnrt])""")) { m -> unescape(m.groupValues[1][0]) }.replace(Regex("""\\u([0-9a-fA-F]{4})""")) { m -> m.groupValues[1].toInt(16).toChar().toString() } }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()

private fun unescape(char: Char): String = when (char) {
    'n' -> "\n"; 'r' -> "\r"; 't' -> "\t"; 'b' -> "\b"; 'f' -> "\u000c"; else -> char.toString()
}

/**
 * Remote catalog. Each item needs `id` and `name`; coefficients fall back to 1.
 * `coefficient` sets both sides when input/output are absent.
 */
fun parseModelCatalog(raw: String): List<AiModel> {
    val array = runCatching { Json.parseToJsonElement(raw).jsonArray }.getOrNull()
        ?: runCatching { Json.parseToJsonElement(raw).jsonObject["data"]?.jsonArray }.getOrNull()
        ?: return emptyList()
    return array.mapNotNull { element -> runCatching {
        val item = element.jsonObject
        val id = item.string("id") ?: return@runCatching null
        val shared = item.double("coefficient", "price") ?: 1.0
        AiModel(
            id = id,
            name = item.string("name") ?: id,
            provider = item.string("provider") ?: "API",
            context = item.string("context") ?: "",
            pricing = ModelPricing(
                item.double("input_coefficient", "inputCoefficient") ?: shared,
                item.double("output_coefficient", "outputCoefficient") ?: shared
            ),
            vision = item["vision"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull(),
            isNew = item["new"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false,
            contextTokens = item.longValue("context_length", "context_tokens", "contextLength"),
            confirmedReasoningParameters = item.reasoningParameters()
        )
    }.getOrNull() }
}

private fun JsonObject.reasoningParameters(): Set<String>? = runCatching {
    when (val value = this["reasoning_parameters"] ?: return null) {
        is kotlinx.serialization.json.JsonArray -> value.mapNotNull { it.jsonPrimitive.contentOrNull?.takeIf(String::isNotBlank) }.toSet()
        is JsonObject -> {
            val isExplicitSupport = value["supported"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() == true
            val supported = value["supported"]?.let { runCatching { it.jsonArray.mapNotNull { item -> item.jsonPrimitive.contentOrNull?.takeIf(String::isNotBlank) }.toSet() }.getOrNull() }
            val values = value["values"]?.let { runCatching { it.jsonArray.mapNotNull { item -> item.jsonPrimitive.contentOrNull?.takeIf(String::isNotBlank) }.toSet() }.getOrNull() }
            (if (isExplicitSupport) values else supported ?: values)
        }
        else -> null
    }?.takeIf { it.isNotEmpty() }
}.getOrNull()

/** Turns transport and HTTP failures into one sentence the user can act on. */
fun explainFailure(error: Throwable): String {
    val message = error.message.orEmpty()
    return when {
        error is IOException || message.contains("Unable to resolve host", true) || message.contains("failed to connect", true) || message.contains("timeout", true) ->
            "Нет соединения. Проверьте интернет и повторите."
        message.startsWith("Ошибка API (401)") || message.startsWith("Ошибка API (403)") -> "Ключ отклонён. Проверьте API-ключ в настройках."
        message.startsWith("Ошибка API (429)") -> "Слишком много запросов. Подождите немного и повторите."
        message.startsWith("Ошибка API (5") -> "Сервис временно недоступен. Повторите через минуту."
        message.isBlank() -> "Не получилось отправить запрос."
        else -> message
    }
}

private fun JsonObject.longValue(vararg names: String): Long? =
    names.firstNotNullOfOrNull { name -> this[name]?.jsonPrimitive?.longOrNull }

private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonObject.double(vararg names: String): Double? =
    names.firstNotNullOfOrNull { name -> this[name]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() }
