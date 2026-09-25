package ru.starimg.ai.data.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.starimg.ai.data.model.ModelPricing
import ru.starimg.ai.data.model.ApiResult
import ru.starimg.ai.data.model.TokenUsage
import ru.starimg.ai.data.model.calculateUsageCost
import ru.starimg.ai.data.model.ChatMessage
import ru.starimg.ai.data.model.fitContext
import ru.starimg.ai.data.model.formatTokens
import java.io.IOException

class UsageParsingTest {
    private fun root(json: String) = Json.parseToJsonElement(json).let { it as kotlinx.serialization.json.JsonObject }

    @Test fun anthropicUsage() {
        val usage = parseUsage(root("""{"usage":{"input_tokens":1200,"output_tokens":450}}"""))
        assertEquals(1200, usage.input)
        assertEquals(450, usage.output)
        assertEquals(1650, usage.total)
        assertTrue(usage.available)
    }

    @Test fun openAiUsage() {
        val usage = parseUsage(root("""{"usage":{"prompt_tokens":1200,"completion_tokens":450,"total_tokens":1650}}"""))
        assertEquals(1200, usage.input)
        assertEquals(450, usage.output)
        assertEquals(1650, usage.total)
    }

    @Test fun totalOnly() {
        val usage = parseUsage(root("""{"usage":{"totalTokenCount":800}}"""))
        assertEquals(0, usage.input)
        assertEquals(800, usage.total)
    }

    @Test fun missingUsage() {
        val usage = parseUsage(root("""{"content":[{"type":"text","text":"ok"}]}"""))
        assertFalse(usage.available)
        assertEquals(0, usage.total)
    }

    @Test fun splitCoefficients() {
        val cost = calculateUsageCost(TokenUsage(1_000_000, 1_000_000), ModelPricing(2.0, 0.5))
        assertEquals(2_500_000, cost.equivalentTokens)
        assertEquals(10.0, cost.rubles, 0.0001)
    }

    @Test fun totalUsesInputCoefficient() {
        val cost = calculateUsageCost(TokenUsage(reportedTotal = 1_000_000), ModelPricing(3.0, 9.0))
        assertEquals(3_000_000, cost.equivalentTokens)
        assertEquals(12.0, cost.rubles, 0.0001)
    }

    @Test fun replyText() {
        assertEquals("привет", parseReplyText(root("""{"content":[{"type":"text","text":"привет"}]}"""), ""))
    }

    @Test fun openAiReplyText() {
        val raw = """{"choices":[{"message":{"role":"assistant","content":"Эпиктет учил..."}}],"usage":{"prompt_tokens":360,"completion_tokens":2020}}"""
        assertEquals("Эпиктет учил...", parseReplyText(root(raw), raw))
    }

    @Test fun geminiAndPlainText() {
        assertEquals("ответ", parseReplyText(root("""{"candidates":[{"content":{"parts":[{"text":"ответ"}]}}]}"""), ""))
        assertEquals("просто", parseReplyText(root("""{"text":"просто"}"""), ""))
    }

    @Test fun patternFallback() {
        val raw = """{"reply":{"content":"Эпиктет учил довольствоваться."},"usage":{"total_tokens":81}}"""
        assertEquals("Эпиктет учил довольствоваться.", parseReplyText(root(raw), raw))
    }

    @Test fun contextBudgetKeepsTheNewest() {
        val history = (1..10).map { ChatMessage("x".repeat(400), it % 2 == 1) }
        val window = fitContext(history, system = "", budgetTokens = 300)
        assertTrue(window.messages.size < history.size)
        assertEquals(history.last(), window.messages.last())
        assertEquals(history.size - window.messages.size, window.dropped)
    }

    @Test fun contextBudgetCountsPhotos() {
        val photo = ChatMessage("что это", true, attachments = listOf(ru.starimg.ai.data.model.Attachment("abc")))
        val window = fitContext(listOf(photo), system = "", budgetTokens = 100)
        assertEquals(1, window.messages.size)
    }

    @Test fun tokenFormat() {
        assertEquals("360", formatTokens(360))
        assertEquals("2,02к", formatTokens(2_020))
        assertEquals("1,5кк", formatTokens(1_500_000))
    }

    @Test fun modelCatalog() {
        val models = parseModelCatalog("""[{"id":"m1","name":"M","input_coefficient":1.5,"output_coefficient":3,"vision":false}]""")
        assertEquals(1, models.size)
        assertEquals(1.5, models[0].pricing.inputCoefficient, 0.0)
        assertEquals(3.0, models[0].pricing.outputCoefficient, 0.0)
        assertFalse(models[0].vision == true)
    }

    @Test fun modelCatalogWrappedAndSharedCoefficient() {
        val models = parseModelCatalog("""{"data":[{"id":"m2","coefficient":4}]}""")
        assertEquals(4.0, models.single().pricing.outputCoefficient, 0.0)
        assertEquals(null, models.single().vision)
        assertEquals(null, models.single().confirmedReasoningParameters)
    }

    @Test fun modelCatalogPreservesOnlyExplicitReasoningModes() {
        val models = parseModelCatalog("""{"data":[{"id":"reasoner","reasoning_parameters":["low","high"]},{"id":"object","reasoning_parameters":{"supported":true,"values":["minimal","extended"]}},{"id":"none","thinking":true}]}""")
        assertEquals(setOf("low", "high"), models[0].confirmedReasoningParameters)
        assertEquals(setOf("minimal", "extended"), models[1].confirmedReasoningParameters)
        assertEquals(null, models[2].confirmedReasoningParameters)
    }

    @Test fun modelCatalogDeduplicatesMalformedRows() {
        val models = parseModelCatalog("""[{"id":"ok"},{"name":"missing-id"},null,{"id":"ok","name":"duplicate"}]""")
        assertEquals(listOf("ok", "ok"), models.map { it.id })
    }

    @Test fun starimgNewsContractAndOptionalFields() {
        val news = parseNews("""{"posts":[{"id":1790162614705,"status":"published","tag":"custom","tagLabel":"Новая модель","tagColor":"#5ac8fa","title":"Обновление","titleEn":"Update","text":"Описание","textEn":"Body","imgs":["/news/media/a.jpg"],"pinned":false}]}""")
        assertEquals("1790162614705", news.single().id)
        assertEquals("published", news.single().status)
        assertEquals("/news/media/a.jpg", news.single().images.single())
    }

    @Test fun telemetryLogRequestIdIsOptional() {
        val telemetry = parseTelemetry("""{"balance":{"remaining_tokens":9,"token_limit":10,"used_tokens":1,"stale":false},"logs":{"logs":[{"model":"m","input_tokens":2,"output_tokens":3,"status":"ok","request_id":"req-1"}]}}""")
        assertEquals("req-1", telemetry.logs.single().requestId)
        assertEquals(2, telemetry.logs.single().inputTokens)
    }

    @Test fun telemetryMissingLogsAndFieldsRemainReadable() {
        val telemetry = parseTelemetry("""{"balance":{"remaining_tokens":0,"token_limit":5,"used_tokens":5}}""")
        assertTrue(telemetry.logs.isEmpty())
    }

    @Test fun usageAttemptIdIsDistinctFromServerRequestId() {
        val result = ApiResult("ok", TokenUsage(1, 2), true, "attempt-local", "request-server")
        assertEquals("attempt-local", result.requestAttemptId)
        assertEquals("request-server", result.serverRequestId)
    }

    @Test fun streamDeltas() {
        assertEquals("мир", streamDelta(root("""{"type":"content_block_delta","delta":{"type":"text_delta","text":"мир"}}""")))
        assertEquals("привет", streamDelta(root("""{"choices":[{"delta":{"content":"привет"}}]}""")))
        assertEquals("", streamDelta(root("""{"type":"message_stop"}""")))
    }

    @Test fun failures() {
        assertTrue(explainFailure(IOException("timeout")).contains("интернет"))
        assertTrue(explainFailure(IllegalStateException("Ошибка API (401): bad")).contains("Ключ"))
        assertTrue(explainFailure(IllegalStateException("Ошибка API (429): slow")).contains("много"))
        assertTrue(explainFailure(IllegalStateException("Ошибка API (503): down")).contains("недоступен"))
    }
}
