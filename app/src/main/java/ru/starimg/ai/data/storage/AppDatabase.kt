package ru.starimg.ai.data.storage

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.starimg.ai.data.model.Agent
import ru.starimg.ai.data.model.Attachment
import ru.starimg.ai.data.model.ChatMessage
import ru.starimg.ai.data.model.ChatSession
import ru.starimg.ai.data.model.MessageVersion
import ru.starimg.ai.data.model.Prompt

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String,
    val title: String,
    val pinned: Boolean,
    val createdAt: Long,
    val folder: String = "",
    val tags: String = "",
    val draft: String = ""
)

@Entity(tableName = "messages", primaryKeys = ["chatId", "position"])
data class MessageEntity(
    val chatId: String,
    val position: Int,
    val text: String,
    val user: Boolean,
    val image: String?,
    val mime: String,
    val error: Boolean,
    val inputTokens: Long,
    val outputTokens: Long,
    val totalTokens: Long,
    val coefficient: Double,
    val outputCoefficient: Double,
    val costRubles: Double,
    val modelId: String,
    val timestamp: Long,
    val usageAvailable: Boolean,
    /** JSON list of [Attachment]. Empty string when the message has none. */
    val attachments: String = "",
    /** JSON list of [MessageVersion]. Empty string when the answer was never regenerated. */
    val versions: String = "",
    val activeVersion: Int = 0,
    val requestAttemptId: String = "",
    val usageSource: String = "UNKNOWN",
    val serverRequestId: String? = null
)

@Entity(tableName = "prompts")
data class PromptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val body: String
)

@Entity(tableName = "agents")
data class AgentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val icon: String,
    val description: String,
    val system: String
)

@Entity(tableName = "site_news")
data class NewsEntity(
    @PrimaryKey val id: String,
    val status: String,
    val payload: String,
    val cachedAt: Long,
    val readAt: Long? = null
)

@Entity(tableName = "model_capabilities")
data class ModelCapabilityEntity(
    @PrimaryKey val id: String,
    val payload: String,
    val observedAt: Long
)

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats")
    suspend fun chats(): List<ChatEntity>

    @Query("SELECT * FROM messages ORDER BY chatId, position")
    suspend fun messages(): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChats(chats: List<ChatEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteMessages(chatId: String)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChat(chatId: String)

    @Query("DELETE FROM chats")
    suspend fun deleteAllChats()

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()

    /** Rewrites one chat and its messages together, so a crash can't leave them apart. */
    @Transaction
    suspend fun replaceChat(chat: ChatEntity, messages: List<MessageEntity>) {
        deleteMessages(chat.id)
        insertChats(listOf(chat))
        insertMessages(messages)
    }

    @Transaction
    suspend fun replaceAll(chats: List<ChatEntity>, messages: List<MessageEntity>) {
        deleteAllMessages()
        deleteAllChats()
        insertChats(chats)
        insertMessages(messages)
    }
}

@Dao
interface LibraryDao {
    @Query("SELECT * FROM prompts")
    suspend fun prompts(): List<PromptEntity>

    @Query("SELECT * FROM agents")
    suspend fun agents(): List<AgentEntity>

    @Insert
    suspend fun insertPrompts(prompts: List<PromptEntity>)

    @Insert
    suspend fun insertAgents(agents: List<AgentEntity>)

    @Query("DELETE FROM prompts")
    suspend fun deletePrompts()

    @Query("DELETE FROM agents")
    suspend fun deleteAgents()

    @Transaction
    suspend fun replacePrompts(prompts: List<PromptEntity>) { deletePrompts(); insertPrompts(prompts) }

    @Transaction
    suspend fun replaceAgents(agents: List<AgentEntity>) { deleteAgents(); insertAgents(agents) }
}

@Dao
interface NewsDao {
    @Query("SELECT * FROM site_news WHERE status = 'published' ORDER BY cachedAt DESC")
    suspend fun published(): List<NewsEntity>

    @Query("DELETE FROM site_news")
    suspend fun clearNews()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNews(items: List<NewsEntity>)

    @Transaction
    suspend fun replaceNews(items: List<NewsEntity>) { clearNews(); insertNews(items) }
}

@Database(entities = [ChatEntity::class, MessageEntity::class, PromptEntity::class, AgentEntity::class, NewsEntity::class, ModelCapabilityEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chats(): ChatDao
    abstract fun library(): LibraryDao
    abstract fun news(): NewsDao

    companion object {
        /** Adds folders, tags, drafts and the JSON columns without touching existing rows. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chats ADD COLUMN folder TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE chats ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE chats ADD COLUMN draft TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN attachments TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN versions TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN activeVersion INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Adds request attribution and site-news cache without rewriting conversations. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN requestAttemptId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE messages ADD COLUMN usageSource TEXT NOT NULL DEFAULT 'UNKNOWN'")
                db.execSQL("CREATE TABLE IF NOT EXISTS site_news (id TEXT NOT NULL PRIMARY KEY, status TEXT NOT NULL, payload TEXT NOT NULL, cachedAt INTEGER NOT NULL, readAt INTEGER)")
                db.execSQL("CREATE TABLE IF NOT EXISTS model_capabilities (id TEXT NOT NULL PRIMARY KEY, payload TEXT NOT NULL, observedAt INTEGER NOT NULL)")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE messages ADD COLUMN serverRequestId TEXT")
                }
        }

        fun open(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "starchat.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}

private val stored = Json { ignoreUnknownKeys = true }

fun ChatEntity.toSession(messages: List<ChatMessage>) =
    ChatSession(id, title, messages, pinned, createdAt, folder, tags.split(',').filter { it.isNotBlank() }, draft)

fun ChatSession.toEntity() = ChatEntity(id, title, pinned, createdAt, folder, tags.joinToString(","), draft)

fun MessageEntity.toMessage(): ChatMessage {
    val photos = runCatching { stored.decodeFromString<List<Attachment>>(attachments) }.getOrDefault(emptyList())
    val history = runCatching { stored.decodeFromString<List<MessageVersion>>(versions) }.getOrDefault(emptyList())
    return ChatMessage(
        text, user, image, mime, photos, error, inputTokens, outputTokens, totalTokens,
        coefficient, outputCoefficient, costRubles, modelId, timestamp, usageAvailable,
        requestAttemptId, serverRequestId, runCatching { ru.starimg.ai.data.model.UsageSource.valueOf(usageSource) }.getOrDefault(ru.starimg.ai.data.model.UsageSource.UNKNOWN), history, activeVersion
    )
}

fun ChatMessage.toEntity(chatId: String, position: Int): MessageEntity {
    val photos = photos
    return MessageEntity(
        chatId, position, text, user, photos.firstOrNull()?.data, photos.firstOrNull()?.mime ?: mime, error,
        inputTokens, outputTokens, totalTokens, coefficient, outputCoefficient, costRubles, modelId, timestamp,
        usageAvailable, stored.encodeToString(photos), stored.encodeToString(versions), activeVersion,
        requestAttemptId, usageSource.name, serverRequestId
    )
}

fun PromptEntity.toPrompt() = Prompt(title, description, body)
fun Prompt.toEntity() = PromptEntity(title = title, description = description, body = body)
fun AgentEntity.toAgent() = Agent(title, icon, description, system)
fun Agent.toEntity() = AgentEntity(title = title, icon = icon, description = description, system = system)
