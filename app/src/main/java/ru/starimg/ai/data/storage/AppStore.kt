package ru.starimg.ai.data.storage

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.starimg.ai.data.model.ModelPricing
import ru.starimg.ai.data.model.AiModel
import ru.starimg.ai.data.model.Agent
import ru.starimg.ai.data.model.ChatSession
import ru.starimg.ai.data.model.Prompt
import ru.starimg.ai.data.model.SiteNews
import ru.starimg.ai.data.api.parseNews

/** SharedPreferences store. Keys and schema match the previous single-file app. */
class AppStore(context: Context) {
    private val prefs = context.getSharedPreferences("starchat", Context.MODE_PRIVATE)
    private val secrets = SecretStore(context)
    private val json = Json { ignoreUnknownKeys = true }
    private val db = AppDatabase.open(context)

    /**
     * Loads chats, moving the legacy single-JSON copy into Room on the first run
     * and then deleting it so startup no longer parses the whole history.
     */
    suspend fun loadChats(): List<ChatSession> {
        val stored = assemble(db.chats().chats(), db.chats().messages())
        val safe = stored.filterNot { it.messages.isEmpty() && it.draft.isBlank() && !it.pinned && it.folder.isBlank() && it.tags.isEmpty() }
        (stored - safe.toSet()).forEach { deleteChat(it.id) }
        if (stored.isNotEmpty() || prefs.getBoolean("migrated", false)) return safe
        val legacy = runCatching { json.decodeFromString<List<ChatSession>>(prefs.getString("chats", "") ?: "") }.getOrDefault(emptyList())
        val retained = legacy.filterNot { it.messages.isEmpty() && it.draft.isBlank() && !it.pinned && it.folder.isBlank() && it.tags.isEmpty() }
        if (retained.isNotEmpty()) db.chats().replaceAll(retained.map { it.toEntity() }, retained.flatMap { it.toMessageEntities() })
        prefs.edit().remove("chats").putBoolean("migrated", true).apply()
        return retained
    }

    suspend fun saveChat(chat: ChatSession) = db.chats().replaceChat(chat.toEntity(), chat.toMessageEntities())

    suspend fun deleteChat(id: String) { db.chats().deleteMessages(id); db.chats().deleteChat(id) }

    suspend fun replaceAllChats(chats: List<ChatSession>) =
        db.chats().replaceAll(chats.map { it.toEntity() }, chats.flatMap { it.toMessageEntities() })

    suspend fun loadPrompts(): List<Prompt> {
        val stored = db.library().prompts().map { it.toPrompt() }
        if (stored.isNotEmpty() || prefs.getBoolean("prompts_migrated", false)) return stored
        val legacy = runCatching { json.decodeFromString<List<Prompt>>(prefs.getString("prompts", "") ?: "") }.getOrDefault(emptyList())
        if (legacy.isNotEmpty()) db.library().replacePrompts(legacy.map { it.toEntity() })
        prefs.edit().remove("prompts").putBoolean("prompts_migrated", true).apply()
        return legacy
    }

    suspend fun savePrompts(prompts: List<Prompt>) = db.library().replacePrompts(prompts.map { it.toEntity() })

    suspend fun loadAgents(): List<Agent> {
        val stored = db.library().agents().map { it.toAgent() }
        if (stored.isNotEmpty() || prefs.getBoolean("agents_migrated", false)) return stored
        val legacy = runCatching { json.decodeFromString<List<Agent>>(prefs.getString("agents", "") ?: "") }.getOrDefault(emptyList())
        if (legacy.isNotEmpty()) db.library().replaceAgents(legacy.map { it.toEntity() })
        prefs.edit().remove("agents").putBoolean("agents_migrated", true).apply()
        return legacy
    }

    suspend fun saveAgents(agents: List<Agent>) = db.library().replaceAgents(agents.map { it.toEntity() })

    suspend fun loadNews(): List<SiteNews> = db.news().published().mapNotNull { runCatching { parseNews(it.payload).first() }.getOrNull() }

    suspend fun saveNews(items: List<SiteNews>) {
        if (items.isEmpty()) return
        db.news().replaceNews(items.map { NewsEntity(it.id, it.status, json.encodeToString(listOf(it)), System.currentTimeMillis()) })
    }

    var modelId: String
        get() = prefs.getString("model", "") ?: ""
        set(value) { prefs.edit().putString("model", value).apply() }

    var baseSystemPrompt: String
        get() = prefs.getString("base_system_prompt", "") ?: ""
        set(value) { prefs.edit().putString("base_system_prompt", value).apply() }

    var endpoint: String
        get() = "https://ai.starimg.ru/v1"
        set(@Suppress("UNUSED_PARAMETER") value) { prefs.edit().putString("endpoint", "https://ai.starimg.ru/v1").apply() }

    var apiKey: String
        get() = secrets.apiKey
        set(value) { secrets.apiKey = value }

    var accessToken: String
        get() = secrets.accessToken
        set(value) { secrets.accessToken = value }

    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(value) { prefs.edit().putBoolean("onboarded", value).apply() }

    var currency: String
        get() = prefs.getString("currency", "₽") ?: "₽"
        set(value) { prefs.edit().putString("currency", value).apply() }

    /** 0 means no cap. */
    var spendLimit: Double
        get() = prefs.getString("spend_limit", "0")?.toDoubleOrNull() ?: 0.0
        set(value) { prefs.edit().putString("spend_limit", value.toString()).apply() }

    /** 0 follows the system, 1 light, 2 dark. */
    var themeMode: Int
        get() = prefs.getInt("theme", 0)
        set(value) { prefs.edit().putInt("theme", value).apply() }

    /** 0 small, 1 normal, 2 large. */
    var textScale: Int
        get() = prefs.getInt("text_scale", 1)
        set(value) { prefs.edit().putInt("text_scale", value).apply() }

    var favoriteModels: Set<String>
        get() = prefs.getStringSet("favorites", emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet("favorites", value).apply() }

    fun loadRemoteModels(): List<AiModel> = runCatching {
        json.decodeFromString<List<StoredModel>>(prefs.getString("remote_models", "") ?: "").map { it.toModel() }
    }.getOrDefault(emptyList())

    /** Replaces the cache only after a successful, non-empty catalog response. */
    fun saveRemoteModels(models: List<AiModel>) {
        if (models.isEmpty()) return
        prefs.edit().putString("remote_models", json.encodeToString(models.map(StoredModel::from))).apply()
    }

    var reasoningMode: String
        get() = prefs.getString("reasoning_mode", "") ?: ""
        set(value) { prefs.edit().putString("reasoning_mode", value).apply() }

    fun exportSettings(): String = json.encodeToString(SettingsDump(currency = currency, spendLimit = spendLimit, themeMode = themeMode, textScale = textScale, favorites = favoriteModels.toList()))

    fun importSettings(raw: String): Boolean = runCatching {
        val dump = json.decodeFromString<SettingsDump>(raw)
        currency = dump.currency; spendLimit = dump.spendLimit
        themeMode = dump.themeMode; textScale = dump.textScale; favoriteModels = dump.favorites.toSet()
    }.isSuccess

    fun clearAll() { prefs.edit().clear().apply(); apiKey = ""; accessToken = "" }

    suspend fun clearDatabase() { db.clearAllTables() }

    suspend fun exportJson(): String = json.encodeToString(loadChats())

    private fun assemble(chats: List<ChatEntity>, messages: List<MessageEntity>): List<ChatSession> {
        val grouped = messages.groupBy { it.chatId }
        return chats.map { it.toSession(grouped[it.id].orEmpty().sortedBy { message -> message.position }.map { message -> message.toMessage() }) }
    }

    private fun ChatSession.toMessageEntities() = messages.mapIndexed { index, message -> message.toEntity(id, index) }
}

@Serializable
private data class StoredModel(val id: String, val name: String, val provider: String, val context: String, val input: Double, val output: Double, val vision: Boolean? = null, val isNew: Boolean = false, val contextTokens: Long? = null, val reasoningParameters: Set<String>? = null) {
    fun toModel() = AiModel(id, name, provider, context, ModelPricing(input, output), vision, isNew, contextTokens, reasoningParameters)
    companion object { fun from(model: AiModel) = StoredModel(model.id, model.name, model.provider, model.context, model.pricing.inputCoefficient, model.pricing.outputCoefficient, model.vision, model.isNew, model.contextTokens, model.confirmedReasoningParameters) }
}

@Serializable
private data class SettingsDump(val endpoint: String = "https://ai.starimg.ru/v1", val currency: String = "₽", val spendLimit: Double = 0.0, val themeMode: Int = 0, val textScale: Int = 1, val favorites: List<String> = emptyList())
