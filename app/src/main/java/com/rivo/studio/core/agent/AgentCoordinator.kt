package com.rivo.studio.core.agent

import com.rivo.studio.core.ai.AiProviderManager
import com.rivo.studio.core.data.StudioDatabase
import com.rivo.studio.core.model.AgentTaskRecord
import com.rivo.studio.core.model.ChatTurn
import com.rivo.studio.core.model.ProjectNode
import com.rivo.studio.core.model.ProviderConfig
import com.rivo.studio.core.project.LocalRecoveryStore
import com.rivo.studio.core.project.PathPolicy
import com.rivo.studio.core.project.ProjectRepository
import com.rivo.studio.core.runtime.LocalExecutionEngine
import com.rivo.studio.core.security.SensitiveText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

interface ProjectInspectionTool {
    suspend fun inspect(projectId: String): List<ProjectNode>
}

interface ProjectFileReadTool {
    suspend fun read(projectId: String, relativePath: String): String
}

interface ChangeProposalTool {
    suspend fun apply(taskId: String): AgentTaskRecord
}

interface ProjectCheckTool {
    suspend fun checkPython(projectId: String, relativePath: String): AgentCheckResult
}

data class AgentCheckResult(val passed: Boolean, val details: String)

class AgentCoordinator(
    private val database: StudioDatabase,
    private val projects: ProjectRepository,
    private val recovery: LocalRecoveryStore,
    private val providers: AiProviderManager,
    private val execution: LocalExecutionEngine,
) : ChangeProposalTool, ProjectCheckTool {
    private val inspectionTool: ProjectInspectionTool = LocalInspectionTool(projects)
    private val readTool: ProjectFileReadTool = LocalFileReadTool(projects)

    fun observeTasks(projectId: String): Flow<List<AgentTaskRecord>> = database.agentTasks().observeForProject(projectId)

    suspend fun runTask(
        projectId: String,
        prompt: String,
        provider: ProviderConfig,
        selectedPaths: List<String>,
        shareProjectContext: Boolean,
        preloadedContext: Map<String, String> = emptyMap(),
    ): AgentTaskRecord = withContext(Dispatchers.IO) {
        require(prompt.isNotBlank()) { "Describe the task for the agent." }
        require(prompt.length <= MAX_TASK_PROMPT_CHARS) { "Agent task descriptions are limited to $MAX_TASK_PROMPT_CHARS characters." }
        val safePrompt = SensitiveText.redact(prompt.trim())
        val now = System.currentTimeMillis()
        val initial = AgentTaskRecord(
            id = UUID.randomUUID().toString(),
            projectId = projectId,
            prompt = safePrompt,
            status = STATUS_INSPECTING,
            summary = "Inspecting this authorized project on device…",
            response = "",
            createdAt = now,
            updatedAt = now,
        )
        database.agentTasks().upsert(initial)
        val audit = JSONArray()
        try {
            // Read-only project inspection is a local tool call. Names are not sent unless
            // the user enabled project-context sharing below.
            val tree = inspectionTool.inspect(projectId)
            audit.put(audit("inspect_project", "completed", "${tree.size} local paths inspected"))
            val selected = selectedPaths.distinct().take(MAX_SHARED_FILES)
            val contextFiles = if (shareProjectContext) {
                selected.map { path ->
                    val safePath = PathPolicy.normalizeRelativePath(path)
                    val source = preloadedContext[safePath] ?: readTool.read(projectId, safePath)
                    safePath to SensitiveText.redact(source)
                }
            } else emptyList()
            val totalContextChars = contextFiles.sumOf { it.second.length }
            require(totalContextChars <= MAX_SHARED_CONTEXT_CHARS) {
                "Selected project context is too large. Choose fewer or smaller files (maximum 120 KB of text)."
            }
            audit.put(audit("read_selected_files", "completed", "${contextFiles.size} file(s) read locally"))

            val planning = initial.copy(
                status = STATUS_PLANNING,
                summary = "Preparing an implementation plan with the selected context…",
                toolAuditJson = audit.toString(),
                updatedAt = System.currentTimeMillis(),
            )
            database.agentTasks().upsert(planning)

            val userContent = buildString {
                append("Task: ").append(safePrompt)
                if (shareProjectContext) {
                    append("\n\nProject structure (paths only):\n")
                    tree.take(MAX_TREE_PATHS).forEach { node ->
                        append(if (node.isDirectory) "[folder] " else "[file] ")
                            .append(node.relativePath).append('\n')
                    }
                    contextFiles.forEach { (path, source) ->
                        append("\n--- selected file: ").append(path).append(" ---\n")
                        append(SensitiveText.redact(source)).append("\n--- end file ---\n")
                    }
                }
            }.take(MAX_AI_INPUT_CHARS)

            val response = SensitiveText.redact(
                providers.chat(
                    config = provider,
                    systemPrompt = AGENT_SYSTEM_PROMPT,
                    turns = listOf(ChatTurn("user", userContent)),
                ),
            ).take(MAX_AGENT_RESPONSE_CHARS)
            audit.put(audit("draft_change", "completed", "Model response received; no files were changed"))
            val parsed = AgentChangeParser.parse(response)
            var proposalPath: String? = null
            var proposalContent: String? = null
            var originalContent: String? = null
            var originalHash: String? = null
            if (parsed != null) {
                val path = PathPolicy.normalizeRelativePath(parsed.path)
                require(parsed.content.toByteArray(Charsets.UTF_8).size <= MAX_PROPOSAL_BYTES) {
                    "The proposed file is larger than the agent's 512 KB review limit."
                }
                val file = projects.resolveForExecution(projectId, path)
                if (file.exists()) {
                    require(file.isFile) { "The agent proposal targets a folder, not a file." }
                    val original = projects.readText(projectId, path)
                    require(original.text.toByteArray(Charsets.UTF_8).size <= MAX_PROPOSAL_BYTES) {
                        "This existing file is too large for a safe in-app agent diff preview. No change was staged."
                    }
                    originalContent = original.text
                    originalHash = sha256(original.text)
                }
                proposalPath = path
                proposalContent = parsed.content
                audit.put(audit("stage_change", "awaiting_user_review", path))
            }
            val completed = planning.copy(
                status = if (proposalPath == null) STATUS_COMPLETED else STATUS_REVIEW,
                summary = if (proposalPath == null) "Plan ready — no file changes were staged." else "Draft ready for review: $proposalPath",
                response = response,
                proposalPath = proposalPath,
                proposalContent = proposalContent,
                originalContent = originalContent,
                originalSha256 = originalHash,
                toolAuditJson = audit.toString(),
                updatedAt = System.currentTimeMillis(),
            )
            database.agentTasks().upsert(completed)
            completed
        } catch (cancelled: CancellationException) {
            val cancelledTask = initial.copy(
                status = STATUS_CANCELLED,
                summary = "Agent task cancelled. No staged changes were applied.",
                toolAuditJson = audit.toString(),
                updatedAt = System.currentTimeMillis(),
            )
            withContext(NonCancellable + Dispatchers.IO) { database.agentTasks().upsert(cancelledTask) }
            throw cancelled
        } catch (error: Exception) {
            audit.put(audit("task", "failed", SensitiveText.redact(error.message ?: "The task did not complete.")))
            val failed = initial.copy(
                status = STATUS_FAILED,
                summary = SensitiveText.redact(error.message ?: "The agent task failed."),
                toolAuditJson = audit.toString(),
                updatedAt = System.currentTimeMillis(),
            )
            database.agentTasks().upsert(failed)
            failed
        }
    }

    override suspend fun apply(taskId: String): AgentTaskRecord = withContext(Dispatchers.IO) {
        val task = database.agentTasks().get(taskId) ?: throw IllegalArgumentException("Agent task not found.")
        require(task.status == STATUS_REVIEW) { "This task does not have a pending code change." }
        val path = PathPolicy.normalizeRelativePath(task.proposalPath ?: throw IllegalStateException("No proposed file path was saved."))
        val proposedText = task.proposalContent ?: throw IllegalStateException("No proposed file content was saved.")
        val target = projects.resolveForExecution(task.projectId, path)
        var snapshotId: String? = null
        if (task.originalSha256 == null) {
            check(!target.exists()) { "A file was created at '$path' after the proposal. Review the conflict before applying." }
        } else {
            check(target.isFile) { "The original file was removed or replaced. The change was not applied." }
            val current = projects.readText(task.projectId, path)
            check(sha256(current.text) == task.originalSha256) {
                "The file changed after this proposal was prepared. Reopen the task and review a fresh diff."
            }
            snapshotId = recovery.createSnapshot(
                projectId = task.projectId,
                relativePath = path,
                text = current.text,
                charsetName = current.charsetName,
                hasBom = current.hasBom,
                reason = "Before applying agent task ${task.id}",
            )
            projects.writeText(task.projectId, path, proposedText, current.charsetName, current.hasBom)
        }
        if (task.originalSha256 == null) {
            projects.createFile(task.projectId, path)
            try {
                projects.writeText(task.projectId, path, proposedText, "UTF-8", false)
            } catch (error: Exception) {
                runCatching { projects.deletePath(task.projectId, path) }
                throw error
            }
        }
        val audit = JSONArray(task.toolAuditJson).put(audit("apply_change", "completed", path))
        task.copy(
            status = STATUS_APPLIED,
            summary = "Change applied to $path. A local pre-change snapshot is available for existing files.",
            snapshotId = snapshotId,
            toolAuditJson = audit.toString(),
            updatedAt = System.currentTimeMillis(),
        ).also { database.agentTasks().upsert(it) }
    }

    override suspend fun checkPython(projectId: String, relativePath: String): AgentCheckResult {
        if (!relativePath.endsWith(".py", ignoreCase = true)) {
            return AgentCheckResult(false, "No automatic check is available for this language in the initial release.")
        }
        val syntax = execution.syntaxCheck(projectId, relativePath)
        if (syntax != null) return AgentCheckResult(false, "Python syntax check failed: $syntax")
        val (imports, missing) = execution.importCheck(projectId, relativePath)
        return if (missing.isEmpty()) {
            AgentCheckResult(true, "Verified: Python syntax is valid and ${imports.size} import name(s) resolve in the embedded runtime. This does not prove API credentials, network services, or bot behavior work.")
        } else {
            AgentCheckResult(false, "Syntax is valid, but import check found: ${missing.joinToString()}. Run dependency diagnostics; packages may not be available on Android.")
        }
    }

    suspend fun checkTask(taskId: String): AgentTaskRecord = withContext(Dispatchers.IO) {
        val task = database.agentTasks().get(taskId) ?: throw IllegalArgumentException("Agent task not found.")
        require(task.status == STATUS_APPLIED) { "Apply the reviewed change before running its checks." }
        val result = checkPython(task.projectId, task.proposalPath ?: return@withContext task)
        val audit = JSONArray(task.toolAuditJson).put(audit("python_checks", if (result.passed) "passed" else "reported_issues", result.details))
        task.copy(
            status = STATUS_CHECKED,
            summary = if (result.passed) "Change applied; available Python checks passed." else "Change applied; check results need attention.",
            response = (task.response + "\n\nRivo local check result:\n" + result.details).take(MAX_AGENT_RESPONSE_CHARS),
            toolAuditJson = audit.toString(),
            updatedAt = System.currentTimeMillis(),
        ).also { database.agentTasks().upsert(it) }
    }

    suspend fun restoreTaskSnapshot(taskId: String): AgentTaskRecord = withContext(Dispatchers.IO) {
        val task = database.agentTasks().get(taskId) ?: throw IllegalArgumentException("Agent task not found.")
        val snapshotId = task.snapshotId ?: throw IllegalStateException("This task has no pre-change file snapshot.")
        val snapshot = recovery.restoreSnapshot(snapshotId) ?: throw IllegalStateException("The recovery snapshot is missing.")
        projects.writeText(task.projectId, snapshot.record.relativePath, snapshot.text, snapshot.charsetName, snapshot.hasBom)
        val audit = JSONArray(task.toolAuditJson).put(audit("restore_snapshot", "completed", snapshot.record.relativePath))
        task.copy(
            status = STATUS_RESTORED,
            summary = "Restored the pre-change file snapshot for ${snapshot.record.relativePath}.",
            toolAuditJson = audit.toString(),
            updatedAt = System.currentTimeMillis(),
        ).also { database.agentTasks().upsert(it) }
    }

    private fun audit(tool: String, status: String, detail: String) = JSONObject()
        .put("tool", tool)
        .put("status", status)
        .put("detail", detail.take(500))
        .put("at", System.currentTimeMillis())

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private class LocalInspectionTool(private val projects: ProjectRepository) : ProjectInspectionTool {
        override suspend fun inspect(projectId: String) = projects.listTree(projectId)
    }

    private class LocalFileReadTool(private val projects: ProjectRepository) : ProjectFileReadTool {
        override suspend fun read(projectId: String, relativePath: String): String = projects.readText(projectId, relativePath).text
    }

    companion object {
        const val STATUS_INSPECTING = "INSPECTING"
        const val STATUS_PLANNING = "PLANNING"
        const val STATUS_REVIEW = "REVIEW_REQUIRED"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_APPLIED = "APPLIED"
        const val STATUS_CHECKED = "CHECKED"
        const val STATUS_RESTORED = "RESTORED"
        const val STATUS_CANCELLED = "CANCELLED"
        const val STATUS_FAILED = "FAILED"

        private const val MAX_TASK_PROMPT_CHARS = 4_000
        private const val MAX_SHARED_FILES = 3
        private const val MAX_SHARED_CONTEXT_CHARS = 120_000
        private const val MAX_TREE_PATHS = 500
        private const val MAX_AI_INPUT_CHARS = 150_000
        private const val MAX_AGENT_RESPONSE_CHARS = 100_000
        private const val MAX_PROPOSAL_BYTES = 512L * 1024

        private val AGENT_SYSTEM_PROMPT = """
            You are Rivo Studio's project-aware coding agent. Help with this task using only the paths and file contents explicitly included in the user message. Do not assume unseen files, credentials, network access, or installed packages. Identify missing configuration and dependency uncertainty. Never claim you executed tests or commands.
            Provide a concise plan and explain trade-offs. If a single-file edit is justified, include exactly one complete replacement file in this form, using a relative path inside the project:
            ```rivo-file:path/to/file.py
            complete file contents
            ```
            Do not return partial diffs in this special block. Do not include API keys, Telegram bot tokens, passwords, or secrets. Do not generate unrelated boilerplate. A proposal is only a draft: Rivo will show it for review and will not apply it automatically. Avoid destructive changes.
        """.trimIndent()
    }
}

object AgentChangeParser {
    private val block = Regex("(?s)```rivo-file:([^\\r\\n]+)\\R(.*?)\\R?```")

    fun parse(response: String): FileDraft? {
        val matches = block.findAll(response).toList()
        if (matches.size != 1) return null
        val match = matches.single()
        val path = match.groupValues[1].trim()
        val content = match.groupValues[2]
        return if (path.isBlank()) null else FileDraft(path, content)
    }
}

data class FileDraft(val path: String, val content: String)
