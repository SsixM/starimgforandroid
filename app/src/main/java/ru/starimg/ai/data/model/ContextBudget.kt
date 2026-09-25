package ru.starimg.ai.data.model

/**
 * What actually goes to the model. [dropped] is how many early messages were
 * left out because they no longer fit the token budget.
 */
data class ContextWindow(val messages: List<ChatMessage>, val dropped: Int)

private const val IMAGE_TOKEN_ESTIMATE = 1600L

/** Rough budget: ~4 characters per token, plus a flat allowance per photo. */
fun estimateTokens(message: ChatMessage): Long =
    message.shownText.length / 4L + message.photos.size * IMAGE_TOKEN_ESTIMATE

/**
 * Keeps the newest messages that fit [budgetTokens], walking backwards so the
 * latest exchange is never the one cut. The system prompt is paid for first.
 * At least the newest message survives even when it alone exceeds the budget.
 */
fun fitContext(history: List<ChatMessage>, system: String, budgetTokens: Long = 24_000): ContextWindow {
    val usable = history.filter { !it.error }
    if (usable.isEmpty()) return ContextWindow(emptyList(), 0)
    var remaining = (budgetTokens - system.length / 4L).coerceAtLeast(1)
    val kept = ArrayDeque<ChatMessage>()
    for (message in usable.asReversed()) {
        val cost = estimateTokens(message)
        if (kept.isNotEmpty() && cost > remaining) break
        kept.addFirst(message)
        remaining -= cost
    }
    return ContextWindow(kept.toList(), usable.size - kept.size)
}
