package ru.starimg.ai.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.starimg.ai.data.api.ApiClient
import ru.starimg.ai.data.api.explainFailure
import ru.starimg.ai.data.model.Agent
import ru.starimg.ai.data.model.AiModel
import ru.starimg.ai.data.model.Catalog
import ru.starimg.ai.data.model.ChatMessage
import ru.starimg.ai.data.model.ChatSession
import ru.starimg.ai.data.model.ModelPricing
import ru.starimg.ai.data.model.Attachment
import ru.starimg.ai.data.model.MessageVersion
import ru.starimg.ai.data.model.Prompt
import ru.starimg.ai.data.model.TokenUsage
import ru.starimg.ai.data.model.calculateUsageCost
import ru.starimg.ai.data.model.fitContext
import ru.starimg.ai.data.storage.AppStore
import java.util.Calendar
import ru.starimg.ai.data.model.AccountBalance
import ru.starimg.ai.data.model.UsageLog
import ru.starimg.ai.data.model.SiteNews
import java.util.UUID

/** Everything the screens render. One snapshot, so a frame never mixes old and new state. */
data class AppState(
    val ready: Boolean = false,
    val chats: List<ChatSession> = emptyList(),
    val currentId: String = "",
    val selectedModel: String = "",
    val endpoint: String = "https://ai.starimg.ru/v1",
    val apiKey: String = "",
    val accessToken: String = "",
    val currency: String = "₽",
    val spendLimit: Double = 0.0,
    val themeMode: Int = 0,
    val textScale: Int = 1,
    val favorites: Set<String> = emptySet(),
    val remoteModels: List<AiModel> = emptyList(),
    val modelsLoading: Boolean = false,
    val modelsError: String? = null,
    val acceptedSendId: Long = 0L,
    val reasoningMode: String = "",
    val customPrompts: List<Prompt> = emptyList(),
    val customAgents: List<Agent> = emptyList(),
    val systemPrompt: String = "",
    val baseSystemPrompt: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val onboarded: Boolean = false,
    /** Early messages the next request will leave out, so the chat can say so. */
    val contextDropped: Int = 0,
    val balance: AccountBalance? = null,
    val usageLogs: List<UsageLog> = emptyList(),
    val telemetryError: String? = null,
    val telemetryLoading: Boolean = false,
    val news: List<SiteNews> = emptyList(), val newsLoading: Boolean = false,
    val newsError: String? = null, val newsUpdatedAt: Long? = null
)

class MainViewModel(app: Context) : ViewModel() {
    private val store = AppStore(app)
    private val api = ApiClient()

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    private var lastText = ""
    private var lastPhotos: List<Attachment> = emptyList()
    private var request: Job? = null
    private var modelRefresh: Job? = null
    private var lastModelRefreshAt = 0L
    private var newsRefresh: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val chats = store.loadChats()
            val initialChat = chats.firstOrNull() ?: freshChat()
            val model = store.modelId.ifBlank { Catalog.models.first().id }
            _state.value = AppState(
                ready = true,
                chats = chats,
                currentId = initialChat.id,
                selectedModel = model,
                endpoint = store.endpoint,
                apiKey = store.apiKey,
                accessToken = store.accessToken,
                currency = store.currency,
                spendLimit = store.spendLimit,
                themeMode = store.themeMode,
                textScale = store.textScale,
                favorites = store.favoriteModels,
                remoteModels = store.loadRemoteModels(),
                reasoningMode = store.reasoningMode,
                customPrompts = store.loadPrompts(),
                customAgents = store.loadAgents(),
                onboarded = store.onboarded,
                baseSystemPrompt = store.baseSystemPrompt
            )
            recountContext()
            refreshModels()
        }
    }

    private val snapshot get() = _state.value
    val messages get() = snapshot.chats.firstOrNull { it.id == snapshot.currentId }?.messages ?: emptyList()
    val currentChat get() = snapshot.chats.firstOrNull { it.id == snapshot.currentId }
    val models: List<AiModel> get() {
        val byId = linkedMapOf<String, AiModel>()
        (Catalog.models + snapshot.remoteModels).forEach { byId[it.id] = it }
        return byId.values.toList()
    }

    fun model(id: String): AiModel? = models.firstOrNull { it.id == id }
    val selectedModel: AiModel? get() = model(snapshot.selectedModel)
    val selectedReasoningModes: List<String> get() = selectedModel?.confirmedReasoningParameters.orEmpty().sorted()

    private data class UsageEntry(val chat: ChatSession, val modelId: String, val timestamp: Long, val input: Long, val output: Long, val total: Long, val cost: Double, val available: Boolean, val attemptId: String, val requestId: String?, val source: ru.starimg.ai.data.model.UsageSource)
    val assistantMessages: List<ChatMessage> get() = snapshot.chats.flatMap { it.messages }.filter { !it.user && !it.error }
    private fun usageEntries(): List<UsageEntry> = snapshot.chats.flatMap { chat -> chat.messages.filter { !it.user && !it.error }.flatMap { message ->
        (0 until message.versionCount).mapNotNull { index ->
            val v = message.versionAt(index)
            val entry = UsageEntry(chat, v?.modelId ?: message.modelId, v?.timestamp ?: message.timestamp, v?.inputTokens ?: message.inputTokens, v?.outputTokens ?: message.outputTokens, v?.totalTokens ?: message.totalTokens, v?.costRubles ?: message.costRubles, v?.usageAvailable ?: message.usageAvailable, v?.requestAttemptId ?: message.requestAttemptId, v?.serverRequestId ?: message.serverRequestId, v?.usageSource ?: message.usageSource)
            if (entry.attemptId.isBlank()) null else entry
        }
    } }

    val totalUsage: TokenUsage
        get() = usageEntries().filter { it.available }.fold(TokenUsage()) { acc, entry ->
            TokenUsage(
                acc.input + entry.input,
                acc.output + entry.output,
                acc.reportedTotal + entry.total
            )
        }

    val totalCost: Double get() = usageEntries().filter { it.available && it.source != ru.starimg.ai.data.model.UsageSource.ESTIMATE }.sumOf { it.cost }

    fun costSince(from: Long): Double = usageEntries().filter { it.available && it.source != ru.starimg.ai.data.model.UsageSource.ESTIMATE && it.timestamp >= from }.sumOf { it.cost }
    fun tokensSince(from: Long): Long = usageEntries().filter { it.available && it.timestamp >= from }.sumOf { it.total }

    fun finishOnboarding() { _state.update { it.copy(onboarded = true) }; store.onboarded = true }

    /** Persists one chat. The whole history is never rewritten for a single message. */
    private fun persist(chat: ChatSession) { viewModelScope.launch(Dispatchers.IO) { store.saveChat(chat) } }

    private fun edit(id: String, change: (ChatSession) -> ChatSession) {
        val updated = snapshot.chats.firstOrNull { it.id == id }?.let(change) ?: return
        _state.update { it.copy(chats = it.chats.map { chat -> if (chat.id == id) updated else chat }) }
        persist(updated)
    }

    fun newChat() {
        if (currentChat?.messages?.isEmpty() == true) return
        val chat = freshChat()
        _state.update { it.copy(chats = listOf(chat) + it.chats, currentId = chat.id, systemPrompt = "", error = null, contextDropped = 0) }
    }

    fun selectChat(id: String) { _state.update { it.copy(currentId = id, systemPrompt = "", error = null) }; recountContext() }

    fun renameChat(id: String, title: String) { val clean = title.trim().take(60); if (clean.isNotBlank()) edit(id) { it.copy(title = clean) } }

    /** Moves a chat into a folder. A blank name takes it back to the main list. */
    fun setFolder(id: String, folder: String) = edit(id) { it.copy(folder = folder.trim().take(30)) }

    fun setTags(id: String, tags: List<String>) = edit(id) {
        it.copy(tags = tags.map { tag -> tag.trim().lowercase() }.filter { tag -> tag.isNotBlank() }.distinct().take(5))
    }

    /** Remembers the composer text so leaving the chat does not throw the draft away. */
    fun saveDraft(id: String, text: String) {
        val chat = snapshot.chats.firstOrNull { it.id == id } ?: return
        if (chat.draft == text) return
        if (chat.messages.isEmpty()) {
            _state.update { state -> state.copy(chats = state.chats.map { if (it.id == id) it.copy(draft = text) else it }) }
            return
        }
        edit(id) { it.copy(draft = text) }
    }

    val folders: List<String> get() = snapshot.chats.map { it.folder }.filter { it.isNotBlank() }.distinct().sorted()

    val allTags: List<String> get() = snapshot.chats.flatMap { it.tags }.distinct().sorted()

    /** Drops everything before the last user message, so the next send starts fresh. */
    fun clearContext() { edit(snapshot.currentId) { it.copy(messages = emptyList()) }; recountContext() }

    fun toggleFavorite(id: String) {
        val favorites = if (id in snapshot.favorites) snapshot.favorites - id else snapshot.favorites + id
        _state.update { it.copy(favorites = favorites) }
        store.favoriteModels = favorites
    }

    fun deleteChat(id: String) {
        var remaining = snapshot.chats.filterNot { it.id == id }
        if (remaining.isEmpty()) remaining = listOf(freshChat())
        val current = if (snapshot.currentId == id || remaining.none { it.id == snapshot.currentId }) remaining.first().id else snapshot.currentId
        _state.update { it.copy(chats = remaining, currentId = current) }
        viewModelScope.launch(Dispatchers.IO) { store.deleteChat(id) }
        recountContext()
    }

    fun togglePin(id: String) = edit(id) { it.copy(pinned = !it.pinned) }

    fun selectModel(id: String) {
        val selected = model(id)
        _state.update { state ->
            val supportedMode = state.reasoningMode.takeIf { it in selected?.confirmedReasoningParameters.orEmpty() }.orEmpty()
            state.copy(selectedModel = id, reasoningMode = supportedMode)
        }
        store.modelId = id
        store.reasoningMode = _state.value.reasoningMode
    }

    fun selectReasoningMode(mode: String) {
        val supported = selectedModel?.confirmedReasoningParameters.orEmpty()
        val selected = mode.takeIf { it.isNotBlank() && it in supported }.orEmpty()
        _state.update { it.copy(reasoningMode = selected) }
        store.reasoningMode = selected
    }

    fun saveSettings(key: String, url: String, money: String, limit: String, theme: Int, scale: Int, access: String) {
        val endpoint = "https://ai.starimg.ru/v1"
        val currency = money.trim().ifBlank { "₽" }
        val spendLimit = limit.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
        val accessToken = access.trim()
        _state.update { it.copy(apiKey = key.trim(), endpoint = endpoint, currency = currency, spendLimit = spendLimit, themeMode = theme, textScale = scale, accessToken = accessToken) }
        store.apiKey = key.trim(); store.endpoint = endpoint; store.currency = currency
        store.spendLimit = spendLimit; store.themeMode = theme; store.textScale = scale
        store.accessToken = accessToken
    }

    /** Pulls the account balance and recent requests. Safe to call repeatedly. */
    fun refreshTelemetry() {
        val now = snapshot
        if (now.accessToken.isBlank() || now.telemetryLoading) return
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(telemetryLoading = true, telemetryError = null) }
            runCatching { api.loadTelemetry(now.endpoint, now.accessToken) }
                .onSuccess { data ->
                    val current = snapshot
                    val claimed = mutableSetOf<String>()
                    val logsById = data.logs.mapNotNull { log -> log.requestId?.let { it to log } }.toMap()
                    val updatedChats = current.chats.map { chat -> chat.copy(messages = chat.messages.map { message -> if (message.user) message else message.withTelemetry(data.logs, logsById, claimed) }) }
                    _state.update { it.copy(balance = data.balance, usageLogs = data.logs, telemetryLoading = false, chats = updatedChats) }
                    updatedChats.forEach(::persist)
                }
                .onFailure { failure -> _state.update { it.copy(telemetryError = failure.message ?: "Не удалось получить баланс.", telemetryLoading = false) } }
        }
    }

    fun loadNewsCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val cached = store.loadNews()
            if (cached.isNotEmpty()) _state.update { if (it.news.isEmpty()) it.copy(news = cached) else it }
        }
    }

    fun refreshNews() {
        if (newsRefresh?.isActive == true) return
        newsRefresh = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(newsLoading = true, newsError = null) }
            try {
                val loaded = api.loadNews()
                if (loaded.isEmpty()) {
                    _state.update { it.copy(newsLoading = false, newsError = "Опубликованных новостей пока нет.") }
                } else {
                    store.saveNews(loaded)
                    _state.update { it.copy(news = loaded, newsLoading = false, newsError = null, newsUpdatedAt = System.currentTimeMillis()) }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (failure: Throwable) {
                _state.update { it.copy(newsLoading = false, newsError = failure.message ?: "Не удалось загрузить новости.") }
            }
        }
    }

    fun cancelNewsRefresh() { newsRefresh?.cancel(); newsRefresh = null; api.cancelNews() }

    fun refreshModels(force: Boolean = true) {
        val now = System.currentTimeMillis()
        if (modelRefresh?.isActive == true) return
        if (!force && now - lastModelRefreshAt < 15 * 60 * 1000L) return
        val state = snapshot
        lastModelRefreshAt = now
        modelRefresh = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(modelsLoading = true, modelsError = null) }
            runCatching { api.loadModels(state.endpoint, state.apiKey) }
                .onSuccess { loaded ->
                    if (loaded.isEmpty()) {
                        _state.update { it.copy(modelsLoading = false, modelsError = "Сервер вернул пустой каталог. Сохранённые модели оставлены без изменений.") }
                    } else {
                        _state.update { current ->
                            val mode = current.reasoningMode.takeIf { it in loaded.firstOrNull { m -> m.id == current.selectedModel }?.confirmedReasoningParameters.orEmpty() }.orEmpty()
                            if (mode != current.reasoningMode) store.reasoningMode = mode
                            current.copy(remoteModels = loaded, reasoningMode = mode, modelsLoading = false, modelsError = null)
                        }
                        store.saveRemoteModels(loaded)
                    }
                }
                .onFailure { failure ->
                    _state.update { it.copy(modelsLoading = false, modelsError = explainFailure(failure)) }
                }
        }
    }

    fun refreshModelsIfStale() {
        refreshModels(force = false)
    }

    fun exportSettings(): String = store.exportSettings()
    fun importSettings(raw: String): Boolean {
        if (!store.importSettings(raw)) return false
        _state.update { it.copy(endpoint = store.endpoint, currency = store.currency, spendLimit = store.spendLimit, themeMode = store.themeMode, textScale = store.textScale, favorites = store.favoriteModels) }
        return true
    }

    fun clearLocalData() {
        store.clearAll()
        val chat = freshChat()
        _state.value = AppState(ready = true, chats = listOf(chat), currentId = chat.id, selectedModel = Catalog.models.first().id)
        viewModelScope.launch(Dispatchers.IO) { store.clearDatabase(); store.saveChat(chat) }
    }

    fun clearError() { _state.update { it.copy(error = null) } }
    fun setAgent(agent: Agent) { _state.update { it.copy(systemPrompt = agent.system) } }
    fun setSystemPrompt(prompt: String) { _state.update { it.copy(systemPrompt = prompt) }; recountContext() }
    fun setBaseSystemPrompt(prompt: String) { _state.update { it.copy(baseSystemPrompt = prompt) }; store.baseSystemPrompt = prompt; recountContext() }

    fun addPrompt(prompt: Prompt) = updateLibrary { it.copy(customPrompts = it.customPrompts + prompt) }
    fun deletePrompt(prompt: Prompt) = updateLibrary { it.copy(customPrompts = it.customPrompts - prompt) }
    fun addAgent(agent: Agent) = updateLibrary { it.copy(customAgents = it.customAgents + agent) }
    fun deleteAgent(agent: Agent) = updateLibrary { it.copy(customAgents = it.customAgents - agent) }

    private fun updateLibrary(change: (AppState) -> AppState) {
        _state.update(change)
        viewModelScope.launch(Dispatchers.IO) { store.savePrompts(snapshot.customPrompts); store.saveAgents(snapshot.customAgents) }
    }

    fun retry() { val (text, photos) = lastRequest() ?: return; send(text, photos, replace = true) }

    /**
     * Regenerates the answer that follows the user message at [userIndex].
     * The previous answer is kept as a version, so nothing already written is lost.
     */
    fun regenerate(userIndex: Int) {
        val history = messages
        val user = history.getOrNull(userIndex) ?: return
        if (!user.user || snapshot.busy) return
        val kept = history.take(userIndex)
        send(user.text, user.photos, replace = true, history = kept, branchFrom = history.getOrNull(userIndex + 1)?.takeIf { !it.user })
    }

    /** Pages between the answers stored for the assistant message at [index]. */
    fun selectVersion(index: Int, version: Int) {
        edit(snapshot.currentId) { chat ->
            val message = chat.messages.getOrNull(index) ?: return@edit chat
            if (message.user || version !in 0 until message.versionCount) chat
            else chat.copy(messages = chat.messages.replaceAt(index) { it.copy(activeVersion = version) })
        }
    }

    /** Drops the message at [index]. An assistant answer takes its user turn with it only when asked. */
    fun deleteMessage(index: Int) {
        if (snapshot.busy) return
        edit(snapshot.currentId) { chat ->
            if (index !in chat.messages.indices) chat else chat.copy(messages = chat.messages.filterIndexed { i, _ -> i != index })
        }
        recountContext()
    }

    /**
     * Rewrites the user message at [index] and resends it. Everything after that
     * message is cut, because it was an answer to the old wording.
     */
    fun editAndResend(index: Int, text: String) {
        if (text.isBlank() || snapshot.busy) return
        val history = messages
        val original = history.getOrNull(index) ?: return
        if (!original.user) return
        send(text, original.photos, replace = true, history = history.take(index))
    }

    /** Asks the model to finish the last answer that stopped early. */
    fun continueReply() {
        val last = messages.lastOrNull() ?: return
        if (last.user || snapshot.busy) return
        send("Продолжи ответ с того места, где остановился. Не повторяй уже написанное.")
    }

    fun editLast(text: String) { if (text.isBlank() || snapshot.busy) return; send(text, lastPhotosOf(), replace = true) }

    private fun lastRequest(): Pair<String, List<Attachment>>? {
        val user = messages.lastOrNull { it.user } ?: return null
        return user.text to user.photos
    }

    private fun lastPhotosOf(): List<Attachment> = messages.lastOrNull { it.user }?.photos ?: emptyList()

    /** The whole chat as plain text, one message per block, for the share sheet. */
    fun chatAsText(chat: ChatSession? = currentChat): String {
        val source = chat ?: return ""
        return buildString {
        append(source.title).append("\n\n")
        source.messages.forEach { message ->
            append(if (message.user) "Вы" else "Ассистент").append(":\n")
            append(message.shownText)
            if (message.photos.isNotEmpty()) append("\n[").append(message.photos.size).append(" фото]")
            append("\n\n")
        }
        }
    }

    /** Same chat in Markdown, so it pastes cleanly into notes and editors. */
    fun chatAsMarkdown(chat: ChatSession? = currentChat): String {
        val source = chat ?: return ""
        return buildString {
        append("# ").append(source.title).append("\n\n")
        source.messages.forEach { message ->
            append(if (message.user) "## Вы" else "## Ассистент").append("\n\n")
            append(message.shownText).append("\n")
            if (message.photos.isNotEmpty()) append("\n*Прикреплено фото: ").append(message.photos.size).append("*\n")
            append("\n")
        }
        }
    }

    fun resetStats() {
        fun ChatMessage.clearUsage() = copy(inputTokens = 0, outputTokens = 0, totalTokens = 0, costRubles = 0.0, usageAvailable = false, usageSource = ru.starimg.ai.data.model.UsageSource.UNKNOWN, versions = versions.map { it.copy(inputTokens = 0, outputTokens = 0, totalTokens = 0, costRubles = 0.0, usageAvailable = false, usageSource = ru.starimg.ai.data.model.UsageSource.UNKNOWN) })
        val cleared = snapshot.chats.map { chat -> chat.copy(messages = chat.messages.map { if (it.user) it else it.clearUsage() }) }
        _state.update { it.copy(chats = cleared) }
        viewModelScope.launch(Dispatchers.IO) { store.replaceAllChats(cleared) }
    }

    suspend fun exportJson(): String = withContext(Dispatchers.IO) { store.exportJson() }

    fun exportCsv(): String = buildString {
        append("chat,model,timestamp,attempt_id,request_id,source,input_tokens,output_tokens,total_tokens,cost_rub\n")
        usageEntries().forEach { e -> append(listOf(e.chat.title, e.modelId, e.timestamp.toString(), e.attemptId, e.requestId.orEmpty(), if (e.available) e.source.name else "UNKNOWN", if (e.available) e.input.toString() else "", if (e.available) e.output.toString() else "", if (e.available) e.total.toString() else "", if (e.available && e.source != ru.starimg.ai.data.model.UsageSource.ESTIMATE) e.cost.toString() else "").joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" } + "\n") }
    }

    /**
     * Sends [text] with [photos]. [replace] cuts the tail first: the last user turn when
     * [history] is null, or everything past [history] when a specific message was edited.
     * [branchFrom] is the answer being replaced, stored so it stays reachable as a version.
     */
    fun send(
        text: String,
        photos: List<Attachment> = emptyList(),
        replace: Boolean = false,
        history: List<ChatMessage>? = null,
        branchFrom: ChatMessage? = null
    ) {
        val now = snapshot
        if ((text.isBlank() && photos.isEmpty()) || now.busy) return
        if (photos.sumOf { it.data.length } > 8_000_000) { _state.update { it.copy(error = "Фото слишком большие. Выберите изображения меньшего размера.") }; return }
        if (now.spendLimit > 0 && costSince(startOfMonth()) >= now.spendLimit) { _state.update { it.copy(error = "Достигнут лимит ${formatMoney(now.spendLimit)} за месяц. Измените его в настройках.") }; return }
        lastText = text; lastPhotos = photos
        val id = now.currentId
        val message = ChatMessage(
            text.ifBlank { "Опиши эти изображения" }, true,
            attachments = photos, timestamp = System.currentTimeMillis()
        )
        val chat = now.chats.firstOrNull { it.id == id } ?: freshChat().copy(id = id)
        val base = history ?: if (replace) chat.messages.dropLastWhile { !it.user }.dropLast(1) else chat.messages
        val title = if (base.isEmpty() && (chat.title == "Новый чат" || replace)) text.take(32).ifBlank { "Новый чат" } else chat.title
        val placeholder = ChatMessage("", user = false, timestamp = System.currentTimeMillis(), modelId = now.selectedModel)
        val updated = chat.copy(title = title, messages = base + message + placeholder, draft = "")
        _state.update { state -> state.copy(chats = state.chats.filterNot { it.id == id }.let { listOf(updated) + it }, currentId = id) }
        viewModelScope.launch(Dispatchers.IO) { store.saveChat(updated) }
        _state.update { it.copy(busy = true, error = null, acceptedSendId = it.acceptedSendId + 1) }
        val modelId = now.selectedModel
        val modelInfo = model(modelId)
        val system = buildString { append(now.baseSystemPrompt.trim()); if (now.systemPrompt.isNotBlank()) { if (isNotEmpty()) append("\n\n"); append("Инструкция чата/агента:\n").append(now.systemPrompt.trim()) } }
        request = viewModelScope.launch(Dispatchers.IO) {
            try {
                val reasoningModes = modelInfo?.confirmedReasoningParameters
                val reasoningMode = now.reasoningMode.takeIf { it in reasoningModes.orEmpty() }
                val result = api.send(now.endpoint, now.apiKey, modelId, base + message, system, reasoningMode, reasoningModes) { delta ->
                    _state.update { state ->
                        state.copy(chats = state.chats.map { existing ->
                            if (existing.id != id || existing.messages.isEmpty()) existing
                            else existing.copy(messages = existing.messages.dropLast(1) + existing.messages.last().copy(text = existing.messages.last().text + delta))
                        })
                    }
                }
                val pricing = model(modelId)?.pricing ?: ModelPricing(1.0, 1.0)
                val cost = if (result.usageAvailable) calculateUsageCost(result.usage, pricing) else null
                val reply = ChatMessage(
                    text = result.text.ifBlank { "Модель вернула пустой ответ." }, user = false,
                    inputTokens = result.usage.input, outputTokens = result.usage.output, totalTokens = result.usage.total,
                    coefficient = pricing.inputCoefficient, outputCoefficient = pricing.outputCoefficient,
                    costRubles = cost?.rubles ?: 0.0,
                    modelId = modelId, timestamp = System.currentTimeMillis(), usageAvailable = result.usageAvailable,
                    requestAttemptId = result.requestAttemptId,
                    serverRequestId = result.serverRequestId,
                    usageSource = if (result.usageAvailable) ru.starimg.ai.data.model.UsageSource.CHAT_RESPONSE else ru.starimg.ai.data.model.UsageSource.UNKNOWN
                )
                edit(id) { it.copy(messages = it.messages.dropLast(1) + reply.withBranch(branchFrom)) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: java.io.IOException) {
                if (e.message?.contains("canceled", true) == true || e.message?.contains("cancelled", true) == true) return@launch
                _state.update { state -> state.copy(chats = state.chats.map { c -> if (c.id == id) c.copy(messages = c.messages.dropLast(1)) else c }) }
                snapshot.chats.firstOrNull { it.id == id }?.let { persist(it) }
                _state.update { it.copy(error = explainFailure(e)) }
            } catch (e: Throwable) {
                _state.update { state -> state.copy(chats = state.chats.map { c -> if (c.id == id) c.copy(messages = c.messages.dropLast(1)) else c }) }
                snapshot.chats.firstOrNull { it.id == id }?.let { persist(it) }
                _state.update { it.copy(error = explainFailure(e)) }
            } finally {
                _state.update { it.copy(busy = false) }
                recountContext()
            }
        }
    }

    /** Stops the reply in progress and frees the composer, in this chat and any other. */
    fun stop() { api.cancel(); request?.cancel() }

    fun formatMoney(value: Double) = ru.starimg.ai.data.model.formatRubles(value).replace("₽", snapshot.currency)

    private fun recountContext() {
        val dropped = fitContext(messages, snapshot.baseSystemPrompt + "\n\n" + snapshot.systemPrompt).dropped
        _state.update { it.copy(contextDropped = dropped) }
    }

    private fun freshChat() = ChatSession(UUID.randomUUID().toString(), "Новый чат", createdAt = System.currentTimeMillis())
}

/** Swaps the item at [index]. Returns the same list when the index is out of range. */
private fun <T> List<T>.replaceAt(index: Int, change: (T) -> T): List<T> =
    if (index !in indices) this else mapIndexed { i, item -> if (i == index) change(item) else item }

/**
 * Keeps the answer being replaced. Its stored versions come first, then the answer
 * itself, and the fresh reply becomes the one on screen.
 */
private fun ChatMessage.withBranch(previous: ChatMessage?): ChatMessage {
    if (previous == null || previous.text.isBlank()) return this
    val older = (0 until previous.versionCount).mapNotNull { index ->
        val version = previous.versionAt(index)
        MessageVersion(
            version?.text ?: previous.text,
            version?.inputTokens ?: previous.inputTokens,
            version?.outputTokens ?: previous.outputTokens,
            version?.totalTokens ?: previous.totalTokens,
            version?.coefficient ?: previous.coefficient,
            version?.outputCoefficient ?: previous.outputCoefficient,
            version?.costRubles ?: previous.costRubles,
            version?.modelId ?: previous.modelId,
            version?.timestamp ?: previous.timestamp,
            version?.usageAvailable ?: previous.usageAvailable,
            version?.requestAttemptId ?: previous.requestAttemptId,
            version?.serverRequestId ?: previous.serverRequestId,
            version?.usageSource ?: previous.usageSource
        )
    }
    return copy(versions = older, activeVersion = older.size)
}

private fun ChatMessage.withTelemetry(logs: List<UsageLog>, logsById: Map<String, UsageLog>, claimed: MutableSet<String>): ChatMessage {
    fun enrich(attempt: String, request: String?, model: String, input: Long, output: Long, total: Long, available: Boolean, source: ru.starimg.ai.data.model.UsageSource, timestamp: Long, cost: Double, coefficient: Double, outputCoefficient: Double, attemptFallback: Boolean): MessageVersion? {
        if (source == ru.starimg.ai.data.model.UsageSource.CHAT_RESPONSE && available) return null
        val log = request?.let(logsById::get)?.takeIf { claimed.add(it.requestId ?: "") }
            ?: if (!attemptFallback) null else logs.filter { it.requestId != null && it.requestId !in claimed && it.model == model && (timestamp == 0L || kotlin.math.abs(timestamp - runCatching { java.time.Instant.parse(it.createdAt).toEpochMilli() }.getOrDefault(Long.MIN_VALUE)) < 120_000) }.singleOrNull()?.also { claimed.add(it.requestId!!) }
            ?: return null
        val matched = log ?: return null
        return MessageVersion("", matched.inputTokens, matched.outputTokens, matched.inputTokens + matched.outputTokens, coefficient, outputCoefficient, calculateUsageCost(TokenUsage(matched.inputTokens, matched.outputTokens, cachedInput = matched.cachedTokens), ModelPricing(coefficient, outputCoefficient)).rubles, model, timestamp, true, attempt, matched.requestId, ru.starimg.ai.data.model.UsageSource.TELEMETRY)
    }
    val enrichedVersions = versions.map { v -> enrich(v.requestAttemptId, v.serverRequestId, v.modelId, v.inputTokens, v.outputTokens, v.totalTokens, v.usageAvailable, v.usageSource, v.timestamp, v.costRubles, v.coefficient, v.outputCoefficient, true)?.let { v.copy(inputTokens = it.inputTokens, outputTokens = it.outputTokens, totalTokens = it.totalTokens, costRubles = it.costRubles, usageAvailable = true, usageSource = it.usageSource, serverRequestId = it.serverRequestId) } ?: v }
    val enrichedSelf = enrich(requestAttemptId, serverRequestId, modelId, inputTokens, outputTokens, totalTokens, usageAvailable, usageSource, timestamp, costRubles, coefficient, outputCoefficient, true)
    return copy(versions = enrichedVersions, inputTokens = enrichedSelf?.inputTokens ?: inputTokens, outputTokens = enrichedSelf?.outputTokens ?: outputTokens, totalTokens = enrichedSelf?.totalTokens ?: totalTokens, costRubles = enrichedSelf?.costRubles ?: costRubles, usageAvailable = enrichedSelf?.usageAvailable ?: usageAvailable, usageSource = enrichedSelf?.usageSource ?: usageSource, serverRequestId = enrichedSelf?.serverRequestId ?: serverRequestId)
}

fun startOfDay(): Long = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

fun startOfWeek(): Long = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, firstDayOfWeek); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

fun startOfMonth(): Long = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
