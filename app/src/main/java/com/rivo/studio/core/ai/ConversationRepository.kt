package com.rivo.studio.core.ai

import com.rivo.studio.core.data.StudioDatabase
import com.rivo.studio.core.model.ConversationRecord
import com.rivo.studio.core.model.MessageRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

class ConversationRepository(private val database: StudioDatabase) {
    fun observeConversations(projectId: String): Flow<List<ConversationRecord>> =
        database.conversations().observeForProject(projectId)

    fun observeMessages(conversationId: String): Flow<List<MessageRecord>> =
        database.conversations().observeMessages(conversationId)

    suspend fun latestOrCreate(projectId: String): ConversationRecord = withContext(Dispatchers.IO) {
        database.conversations().latestForProject(projectId) ?: create(projectId)
    }

    suspend fun create(projectId: String, title: String = "New conversation"): ConversationRecord = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        ConversationRecord(UUID.randomUUID().toString(), projectId, title.take(80), now, now).also {
            database.conversations().upsert(it)
        }
    }

    suspend fun addMessage(
        conversationId: String,
        projectId: String,
        role: String,
        content: String,
        contextLabel: String? = null,
    ) = withContext(Dispatchers.IO) {
        require(role in setOf("user", "assistant", "system", "error"))
        val now = System.currentTimeMillis()
        database.conversations().insertMessage(
            MessageRecord(
                id = UUID.randomUUID().toString(),
                conversationId = conversationId,
                projectId = projectId,
                role = role,
                content = content.take(MAX_MESSAGE_CHARS),
                createdAt = now,
                contextLabel = contextLabel,
            ),
        )
        database.conversations().touch(conversationId, now)
    }

    suspend fun recentTurns(conversationId: String, limit: Int = 20) = withContext(Dispatchers.IO) {
        database.conversations().messages(conversationId, limit)
            .asReversed()
            .filter { it.role == "user" || it.role == "assistant" }
            .map { com.rivo.studio.core.model.ChatTurn(it.role, it.content) }
    }

    private companion object {
        const val MAX_MESSAGE_CHARS = 100_000
    }
}
