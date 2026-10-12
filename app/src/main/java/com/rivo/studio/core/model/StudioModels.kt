package com.rivo.studio.core.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectRecord(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isFavorite: Boolean = false,
)

@Entity(
    tableName = "recent_files",
    primaryKeys = ["projectId", "relativePath"],
    indices = [Index("projectId"), Index("openedAt")],
)
data class RecentFileRecord(
    val projectId: String,
    val relativePath: String,
    val openedAt: Long,
)

@Entity(tableName = "conversations", indices = [Index("projectId"), Index("updatedAt")])
data class ConversationRecord(
    @PrimaryKey val id: String,
    val projectId: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "messages",
    indices = [Index("conversationId", "createdAt"), Index("projectId")],
)
data class MessageRecord(
    @PrimaryKey val id: String,
    val conversationId: String,
    val projectId: String,
    val role: String,
    val content: String,
    val createdAt: Long,
    val contextLabel: String? = null,
)

@Entity(tableName = "agent_tasks", indices = [Index("projectId", "updatedAt")])
data class AgentTaskRecord(
    @PrimaryKey val id: String,
    val projectId: String,
    val prompt: String,
    val status: String,
    val summary: String,
    val response: String,
    val proposalPath: String? = null,
    val proposalContent: String? = null,
    val originalContent: String? = null,
    val originalSha256: String? = null,
    val snapshotId: String? = null,
    val toolAuditJson: String = "[]",
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "executions", indices = [Index("projectId", "startedAt")])
data class ExecutionRecord(
    @PrimaryKey val id: String,
    val projectId: String,
    val scriptPath: String,
    val state: String,
    val exitCode: Int?,
    val startedAt: Long,
    val finishedAt: Long?,
    val logFileName: String,
    val diagnostic: String?,
)

@Entity(tableName = "snapshots", indices = [Index("projectId", "createdAt")])
data class SnapshotRecord(
    @PrimaryKey val id: String,
    val projectId: String,
    val relativePath: String,
    val storedName: String,
    val charsetName: String = "UTF-8",
    val hasBom: Boolean = false,
    val createdAt: Long,
    val reason: String,
)

data class ProjectNode(
    val relativePath: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val depth: Int,
)

data class DecodedText(
    val text: String,
    val charsetName: String,
    val hasBom: Boolean,
)

data class RecoveryCandidate(
    val projectId: String,
    val relativePath: String,
    val text: String,
    val savedAt: Long,
)

enum class ProviderKind(val title: String, val defaultBaseUrl: String) {
    OPENAI("OpenAI", "https://api.openai.com/v1"),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1"),
    GEMINI("Gemini", "https://generativelanguage.googleapis.com/v1beta"),
    ANTHROPIC("Anthropic", "https://api.anthropic.com/v1"),
    CUSTOM("OpenAI-compatible", "https://"),
}

data class ProviderConfig(
    val id: String,
    val name: String,
    val kind: ProviderKind,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val isDefault: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis(),
)

data class ChatTurn(val role: String, val content: String)

data class ProviderDiagnostic(
    val message: String,
    val models: List<String> = emptyList(),
    val success: Boolean,
)

enum class ProcessState { STARTING, RUNNING, STOPPING, EXITED, FAILED }

data class ProcessSnapshot(
    val id: String,
    val projectId: String,
    val scriptPath: String,
    val state: ProcessState,
    val output: String,
    val exitCode: Int?,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val diagnostic: String? = null,
)

data class DependencyReport(
    val requirements: List<String>,
    val bundled: List<String>,
    val missing: List<String>,
    val message: String,
)

enum class StudioScreen { HOME, WORKSPACE, ASSISTANT, SETTINGS }

enum class AssistantMode { CHAT, AGENT }

data class EditorTab(
    val relativePath: String,
    val text: String,
    val savedText: String,
    val charsetName: String,
    val hasBom: Boolean,
    val savedCharsetName: String = charsetName,
    val savedHasBom: Boolean = hasBom,
    val isDirty: Boolean = false,
)
