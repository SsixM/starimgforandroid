package ru.starimg.ai.data.model

import java.util.Locale
import kotlin.math.roundToLong

/** Account snapshot from GET /telemetry?logs=1. */
data class AccountBalance(
    val remaining: Long,
    val limit: Long,
    val used: Long,
    val stale: Boolean
) {
    val fraction: Float get() = if (limit <= 0) 0f else (used.toFloat() / limit).coerceIn(0f, 1f)
}

data class UsageLog(
    val model: String,
    val inputTokens: Long,
    val outputTokens: Long,
    val cachedTokens: Long,
    val costTokens: Long,
    val latencyMs: Long,
    val status: String,
    val errorMessage: String,
    val createdAt: String,
    val requestId: String? = null,
    val usageSource: UsageSource = UsageSource.TELEMETRY
)

data class SiteNews(
    val id: String, val status: String, val tag: String?, val tagLabel: String?, val tagColor: String?,
    val title: String, val titleEn: String?, val text: String, val textEn: String?, val images: List<String>, val pinned: Boolean
)

data class NewsState(val id: String, val readAt: Long? = null, val dismissed: Boolean = false)
const val BASE_RUB_PER_MILLION_TOKENS = 4.0

fun calculateUsageCost(usage: TokenUsage, pricing: ModelPricing): UsageCost {
    val inputEq = usage.input * pricing.inputCoefficient
    val outputEq = usage.output * pricing.outputCoefficient
    val equivalent = if (usage.input + usage.output > 0) {
        (inputEq + outputEq).roundToLong()
    } else {
        (usage.reportedTotal * pricing.inputCoefficient).roundToLong()
    }
    return UsageCost(
        rawTokens = usage.total.coerceAtLeast(0),
        coefficient = pricing.inputCoefficient,
        equivalentTokens = equivalent.coerceAtLeast(0),
        rubles = equivalent.coerceAtLeast(0) * BASE_RUB_PER_MILLION_TOKENS / 1_000_000.0
    )
}

/**
 * Compact count with a Russian suffix: 360 → "360", 2 020 → "2,02к", 1 500 000 → "1,5кк".
 * Thousands use a grouping space; the decimal comma only appears in the compact forms.
 */
fun formatTokens(value: Long): String = when {
    value >= 1_000_000 -> compact(value / 1_000_000.0, "кк")
    value >= 1_000 -> compact(value / 1_000.0, "к")
    else -> value.toString()
}

private fun compact(value: Double, suffix: String): String {
    val text = if (value >= 100) "%.0f".format(Locale.US, value) else "%.2f".format(Locale.US, value)
    return text.trimEnd('0').trimEnd('.').replace('.', ',') + suffix
}

fun formatRubles(value: Double): String = when {
    value <= 0.0 -> "0 ₽"
    value < 0.01 -> "< 0,01 ₽"
    else -> String.format(Locale("ru", "RU"), "%.2f ₽", value)
}

fun formatCoefficient(value: Double): String =
    if (value % 1.0 == 0.0) "×${value.toInt()}" else "×${String.format(Locale.US, "%.2f", value).trimEnd('0')}"

/** Rough pre-send price: ~4 characters per token, output guessed equal to input, capped at 4096. */
fun estimateRequestCost(characters: Int, pricing: ModelPricing): Double {
    val input = (characters / 4.0).coerceAtLeast(1.0)
    val output = input.coerceAtMost(4096.0)
    val equivalent = input * pricing.inputCoefficient + output * pricing.outputCoefficient
    return equivalent * BASE_RUB_PER_MILLION_TOKENS / 1_000_000.0
}
