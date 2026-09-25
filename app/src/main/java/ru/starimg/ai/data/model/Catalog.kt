package ru.starimg.ai.data.model

/**
 * Model list and multipliers. Coefficients are a local stand-in for a remote
 * pricing config: one number means input and output share the same rate.
 */
object Catalog {
    private fun price(input: Double, output: Double = input) = ModelPricing(input, output)

    val models = listOf(
        AiModel("claude-fable-5.1", "Claude Fable 5.1", "Anthropic", "1M", price(8.0), true),
        AiModel("claude-opus-5", "Claude Opus 5", "Anthropic", "1M", price(4.0), true),
        AiModel("claude-sonnet-4-6", "Claude Sonnet 4.6", "Anthropic", "200K", price(2.0), true),
        AiModel("claude-haiku-4-5", "Claude Haiku 4.5", "Anthropic", "200K", price(0.9), true),
        AiModel("gpt-6-astra", "GPT-6 Astra", "OpenAI", "1M", price(7.5), true, true),
        AiModel("gpt-5.6-terra", "GPT-5.6 Terra", "OpenAI", "1M", price(1.5), true),
        AiModel("gpt-5.6-sol", "GPT-5.6 Sol", "OpenAI", "1M", price(3.0), true),
        AiModel("gpt-5.6-luna", "GPT-5.6 Luna", "OpenAI", "1M", price(0.33), true),
        AiModel("gpt-5.5", "GPT-5.5", "OpenAI", "258K", price(3.0), true),
        AiModel("gemini-3.8-flash", "Gemini 3.8 Flash", "Google", "1M", price(2.0), true),
        AiModel("gemini-3.1-pro", "Gemini 3.1 Pro", "Google", "1M", price(2.0), true),
        AiModel("grok-4.6", "Grok 4.6", "xAI", "500K", price(0.5), true),
        AiModel("composer-2.5-fast", "Composer 2.5 Fast", "xAI", "200K", price(0.3), false, true),
        AiModel("deepseek-v4-pro", "DeepSeek V4 Pro", "DeepSeek", "1M", price(0.5), false),
        AiModel("deepseek-v4.1-flash", "DeepSeek V4.1 Flash", "DeepSeek", "1M", price(0.3), false, true),
        AiModel("kimi-k3", "Kimi K3", "Kimi", "1M", price(2.5), true, true),
        AiModel("glm-5.3", "GLM 5.3", "GLM", "1M", price(1.5), false, true),
        AiModel("qwen3.8-max", "Qwen 3.8 Max", "Китайские модели", "1M", price(2.5), true),
        AiModel("qwen3.8-flash", "Qwen 3.8 Flash", "Китайские модели", "1M", price(0.7), true)
    )

    val prompts = listOf(
        Prompt("Улучшить текст", "Сделать текст яснее", "Улучши мой текст: исправь ошибки, сохрани смысл и сделай стиль ясным."),
        Prompt("Идеи", "Быстрый брейншторм", "Предложи 10 разных идей по моей теме и выбери лучшие."),
        Prompt("Переводчик", "Сохранить стиль", "Переведи следующий текст на русский, сохранив смысл и форматирование."),
        Prompt("Разобрать код", "Найти ошибки", "Проанализируй код, найди ошибки и предложи улучшенный вариант.")
    )

    val agents = listOf(
        Agent("Кодер", "⌘", "Пишет и отлаживает код", "Ты опытный senior-разработчик. Пиши надёжный код и объясняй решения кратко."),
        Agent("Исследователь", "◈", "Структурирует сложные темы", "Отделяй факты от предположений и отмечай, что нужно проверить."),
        Agent("Копирайтер", "✦", "Создаёт сильные тексты", "Пиши живо, конкретно и с учётом аудитории."),
        Agent("Наставник", "♧", "Объясняет простыми словами", "Объясняй сложное простыми словами и давай небольшие примеры.")
    )

    fun model(id: String): AiModel? = models.firstOrNull { it.id == id }
}
