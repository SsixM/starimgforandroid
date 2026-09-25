package ru.starimg.ai.data.model

import kotlinx.serialization.Serializable

/**
 * Pricing relative to the base tariff: 1 000 000 equivalent tokens = 4 ₽.
 * [inputCoefficient] and [outputCoefficient] scale raw tokens before billing.
 * When the provider only publishes one multiplier, both coefficients are equal.
 */
data class ModelPricing(
    val inputCoefficient: Double,
    val outputCoefficient: Double,
    val currency: String = "₽"
)

data class AiModel(
    val id: String,
    val name: String,
    val provider: String,
    val context: String,
    val pricing: ModelPricing,
    val vision: Boolean? = null,
    val isNew: Boolean = false,
    val contextTokens: Long? = null,
    val confirmedReasoningParameters: Set<String>? = null
) {
    val coefficient: Double get() = pricing.inputCoefficient
    val supportsReasoning: Boolean get() = !confirmedReasoningParameters.isNullOrEmpty()
}

/** One picture attached to a message. [data] is base64, [mime] is the media type. */
@Serializable
data class Attachment(val data: String, val mime: String = "image/jpeg")

@Serializable
data class ChatMessage(
    val text: String,
    val user: Boolean,
    val image: String? = null,
    val mime: String = "image/jpeg",
    /** Several photos per message. Empty when the message only carries the legacy single [image]. */
    val attachments: List<Attachment> = emptyList(),
    val error: Boolean = false,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val totalTokens: Long = 0,
    val coefficient: Double = 1.0,
    val outputCoefficient: Double = 1.0,
    val costRubles: Double = 0.0,
    val modelId: String = "",
    val timestamp: Long = 0L,
    val usageAvailable: Boolean = true,
    val requestAttemptId: String = "",
    val usageSource: UsageSource = if (usageAvailable) UsageSource.CHAT_RESPONSE else UsageSource.UNKNOWN,
    /**
     * Earlier answers for the same turn, newest last. Only assistant messages carry them.
     * [activeVersion] picks which one is shown; the message itself is version zero.
     */
    val versions: List<MessageVersion> = emptyList(),
    val activeVersion: Int = 0
) {
    /** Photos actually shown, whether they came from the list or the old single field. */
    val photos: List<Attachment>
        get() = attachments.ifEmpty { image?.let { listOf(Attachment(it, mime)) } ?: emptyList() }

    /** Text of the version on screen, so the rest of the app never has to know about branches. */
    val shownText: String get() = versionAt(activeVersion)?.text ?: text

    val versionCount: Int get() = 1 + versions.size

    fun versionAt(index: Int): MessageVersion? = when {
        index <= 0 -> null
        index - 1 in versions.indices -> versions[index - 1]
        else -> null
    }
}

/** A previous or alternative answer kept so the reader can page back to it. */
@Serializable
data class MessageVersion(
    val text: String,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val totalTokens: Long = 0,
    val coefficient: Double = 1.0,
    val outputCoefficient: Double = 1.0,
    val costRubles: Double = 0.0,
    val modelId: String = "",
    val timestamp: Long = 0L,
    val usageAvailable: Boolean = true,
    val requestAttemptId: String = "",
    val usageSource: UsageSource = if (usageAvailable) UsageSource.CHAT_RESPONSE else UsageSource.UNKNOWN
)

@Serializable
data class ChatSession(
    val id: String,
    val title: String,
    val messages: List<ChatMessage> = emptyList(),
    val pinned: Boolean = false,
    val createdAt: Long = 0L,
    /** Optional folder name. Blank means the chat sits in the main list. */
    val folder: String = "",
    /** Free-form labels, kept short and lower-case by the editor. */
    val tags: List<String> = emptyList(),
    /** Composer text remembered while the chat is not on screen. */
    val draft: String = ""
)

@Serializable
data class Prompt(val title: String, val description: String, val body: String)

@Serializable
data class Agent(val title: String, val icon: String, val description: String, val system: String)

data class TokenUsage(
    val input: Long = 0,
    val output: Long = 0,
    val reportedTotal: Long = 0,
    val estimatedInput: Long? = null,
    val estimatedOutput: Long? = null
) {
    val total: Long get() = if (input + output > 0) input + output else reportedTotal
    val available: Boolean get() = total > 0
}

data class UsageCost(
    val rawTokens: Long,
    val coefficient: Double,
    val equivalentTokens: Long,
    val rubles: Double
)

enum class UsageSource { CHAT_RESPONSE, TELEMETRY, ESTIMATE, UNKNOWN }

data class ApiResult(
    val text: String,
    val usage: TokenUsage,
    val usageAvailable: Boolean,
    val requestAttemptId: String = "",
    val serverRequestId: String? = null
)
