package com.rivo.studio.core.runtime

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.rivo.studio.core.data.StudioDatabase
import com.rivo.studio.core.model.DependencyReport
import com.rivo.studio.core.model.ExecutionRecord
import com.rivo.studio.core.model.ProcessSnapshot
import com.rivo.studio.core.model.ProcessState
import com.rivo.studio.core.project.ProjectRepository
import com.rivo.studio.core.security.SensitiveText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.util.UUID

interface LocalExecutionEngine {
    val currentProcess: StateFlow<ProcessSnapshot?>
    fun observeHistory(projectId: String): Flow<List<ExecutionRecord>>
    suspend fun syntaxCheck(projectId: String, relativePath: String): String?
    suspend fun importCheck(projectId: String, relativePath: String): Pair<List<String>, List<String>>
    suspend fun checkRequirements(projectId: String): DependencyReport
    suspend fun startPython(projectId: String, relativePath: String): ProcessSnapshot
    suspend fun stop(processId: String)
    suspend fun restartPython(projectId: String, relativePath: String): ProcessSnapshot
}

/** Chaquopy-backed Python runner. It is intentionally a single foreground-app process, not a shell. */
class ChaquopyExecutionEngine(
    context: Context,
    private val projects: ProjectRepository,
    private val database: StudioDatabase,
) : LocalExecutionEngine {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startupRecovery = scope.async(Dispatchers.IO) {
        database.executions().markInterrupted(
            System.currentTimeMillis(),
            "Rivo Studio's Android process ended before this Python run could finish.",
        )
    }
    private val logDirectory = File(appContext.noBackupFilesDir, "execution").apply { mkdirs() }
    private val state = MutableStateFlow<ProcessSnapshot?>(null)
    override val currentProcess: StateFlow<ProcessSnapshot?> = state.asStateFlow()
    private val processLock = Any()

    override fun observeHistory(projectId: String): Flow<List<ExecutionRecord>> = database.executions().observeForProject(projectId)

    override suspend fun syntaxCheck(projectId: String, relativePath: String): String? = withContext(Dispatchers.IO) {
        requirePythonFile(relativePath)
        val source = projects.readText(projectId, relativePath).text
        val runner = pythonInstance().getModule(RUNNER_MODULE)
        runner.callAttr("syntax_check", source, relativePath).toString().ifBlank { null }
    }

    override suspend fun importCheck(projectId: String, relativePath: String): Pair<List<String>, List<String>> = withContext(Dispatchers.IO) {
        requirePythonFile(relativePath)
        val source = projects.readText(projectId, relativePath).text
        val root = projects.resolveForExecution(projectId, "")
        val result = JSONObject(
            pythonInstance().getModule(RUNNER_MODULE)
                .callAttr("check_imports", source, root.absolutePath, relativePath).toString(),
        )
        val imports = result.optJSONArray("imports")?.let { array -> (0 until array.length()).map(array::getString) }.orEmpty()
        val missing = result.optJSONArray("missing")?.let { array -> (0 until array.length()).map(array::getString) }.orEmpty()
        val syntaxError = result.optString("error").takeIf(String::isNotBlank)
        imports to (if (syntaxError != null) listOf(syntaxError) else missing)
    }

    override suspend fun checkRequirements(projectId: String): DependencyReport = withContext(Dispatchers.IO) {
        val root = projects.resolveForExecution(projectId, "")
        val result = JSONObject(pythonInstance().getModule(RUNNER_MODULE).callAttr("check_requirements", root.absolutePath).toString())
        fun strings(key: String): List<String> = result.optJSONArray(key)?.let { array ->
            (0 until array.length()).map(array::getString)
        }.orEmpty()
        DependencyReport(
            requirements = strings("requirements"),
            bundled = strings("bundled"),
            missing = strings("missing"),
            message = result.optString("message", "The dependency check did not return a result."),
        )
    }

    override suspend fun startPython(projectId: String, relativePath: String): ProcessSnapshot {
        startupRecovery.await()
        requirePythonFile(relativePath)
        val script = projects.resolveForExecution(projectId, relativePath)
        check(script.isFile) { "The selected Python file is missing." }
        val root = projects.resolveForExecution(projectId, "")
        val current = state.value
        check(current == null || current.state !in ACTIVE_STATES) { "Stop the current local process before starting another." }
        val id = UUID.randomUUID().toString()
        val logFile = File(logDirectory, "$id.log")
        val cancelFile = File(logDirectory, "$id.cancel")
        val initial = ProcessSnapshot(
            id = id,
            projectId = projectId,
            scriptPath = relativePath,
            state = ProcessState.STARTING,
            output = "Starting the embedded Python 3.13 runtime…\n",
            exitCode = null,
            startedAt = System.currentTimeMillis(),
        )
        synchronized(processLock) {
            val latest = state.value
            check(latest == null || latest.state !in ACTIVE_STATES) { "Stop the current local process before starting another." }
            state.value = initial
        }
        database.executions().upsert(
            ExecutionRecord(
                id = id,
                projectId = projectId,
                scriptPath = relativePath,
                state = ProcessState.STARTING.name,
                exitCode = null,
                startedAt = initial.startedAt,
                finishedAt = null,
                logFileName = logFile.name,
                diagnostic = null,
            ),
        )
        synchronized(processLock) {
            scope.launch { runProcess(initial, root, relativePath, logFile, cancelFile) }
        }
        pruneOldLogs()
        return initial
    }

    override suspend fun stop(processId: String) {
        val current = state.value ?: return
        if (current.id != processId || current.state !in ACTIVE_STATES) return
        File(logDirectory, "$processId.cancel").writeText("stop")
        updateSnapshot(current.copy(state = ProcessState.STOPPING, diagnostic = "Stop requested; Python checks for cancellation between Python instructions."))
    }

    override suspend fun restartPython(projectId: String, relativePath: String): ProcessSnapshot {
        val current = state.value
        if (current != null && current.state in ACTIVE_STATES) {
            stop(current.id)
            val stopped = withTimeoutOrNull(10_000) {
                currentProcess.first { it == null || it.id != current.id || it.state !in ACTIVE_STATES }
            }
            check(stopped != null) { "The current script has not stopped yet. It may be blocked in a native or network call." }
        }
        return startPython(projectId, relativePath)
    }

    private suspend fun runProcess(
        initial: ProcessSnapshot,
        projectRoot: File,
        relativePath: String,
        logFile: File,
        cancelFile: File,
    ) {
        val started = initial.copy(state = ProcessState.RUNNING)
        updateSnapshot(started)
        val execution = scope.async(Dispatchers.IO) {
            pythonInstance().getModule(RUNNER_MODULE)
                .callAttr("run_file", projectRoot.absolutePath, relativePath, logFile.absolutePath, cancelFile.absolutePath)
                .toInt()
        }
        try {
            while (!execution.isCompleted) {
                val current = state.value ?: started
                val stateToUse = if (cancelFile.exists()) ProcessState.STOPPING else current.state
                updateSnapshot(current.copy(state = stateToUse, output = readLog(logFile)))
                delay(POLL_INTERVAL_MS)
            }
            val exitCode = execution.await()
            val stoppedByUser = cancelFile.exists() || state.value?.state == ProcessState.STOPPING || exitCode == 130
            val finalState = if (exitCode == 0 || stoppedByUser) ProcessState.EXITED else ProcessState.FAILED
            val finished = (state.value ?: started).copy(
                state = finalState,
                output = readLog(logFile),
                exitCode = exitCode,
                finishedAt = System.currentTimeMillis(),
                diagnostic = when {
                    stoppedByUser -> "Stopped by user."
                    exitCode == 0 -> "Script exited normally."
                    else -> "Python script exited with code $exitCode. Review the traceback above."
                },
            )
            updateSnapshot(finished)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val message = SensitiveText.redact(error.message ?: "Python runtime failed.")
            updateSnapshot(
                (state.value ?: started).copy(
                    state = ProcessState.FAILED,
                    output = readLog(logFile),
                    exitCode = null,
                    finishedAt = System.currentTimeMillis(),
                    diagnostic = "Local Python runtime error: $message",
                ),
            )
        } finally {
            cancelFile.delete()
        }
    }

    private suspend fun updateSnapshot(snapshot: ProcessSnapshot) {
        state.value = snapshot
        database.executions().upsert(
            ExecutionRecord(
                id = snapshot.id,
                projectId = snapshot.projectId,
                scriptPath = snapshot.scriptPath,
                state = snapshot.state.name,
                exitCode = snapshot.exitCode,
                startedAt = snapshot.startedAt,
                finishedAt = snapshot.finishedAt,
                logFileName = "${snapshot.id}.log",
                diagnostic = snapshot.diagnostic?.let { SensitiveText.redact(it) },
            ),
        )
    }

    private fun readLog(file: File): String = if (file.isFile) {
        runCatching { SensitiveText.redact(file.readText(Charsets.UTF_8)).takeLast(MAX_VISIBLE_LOG_CHARS) }
            .getOrDefault("[Could not read the process output file.]\n")
    } else {
        ""
    }

    private fun pythonInstance(): Python = synchronized(PYTHON_LOCK) {
        if (!Python.isStarted()) Python.start(AndroidPlatform(appContext))
        Python.getInstance()
    }

    private fun requirePythonFile(path: String) {
        require(path.substringAfterLast('.', "").equals("py", ignoreCase = true)) {
            "The local Python runtime only runs .py files. Other language runtimes are not installed."
        }
    }

    private fun pruneOldLogs() {
        val logs = logDirectory.listFiles { file -> file.extension == "log" }
            ?.sortedByDescending { it.lastModified() }.orEmpty()
        var retainedBytes = 0L
        logs.forEachIndexed { index, file ->
            retainedBytes += file.length()
            if (index >= MAX_RETAINED_LOGS || retainedBytes > MAX_RETAINED_LOG_BYTES) {
                file.delete()
                File(logDirectory, "${file.nameWithoutExtension}.cancel").delete()
            }
        }
    }

    private companion object {
        const val RUNNER_MODULE = "rivo_runner"
        const val POLL_INTERVAL_MS = 500L
        const val MAX_VISIBLE_LOG_CHARS = 64 * 1024
        const val MAX_RETAINED_LOGS = 30
        const val MAX_RETAINED_LOG_BYTES = 20L * 1024 * 1024
        val ACTIVE_STATES = setOf(ProcessState.STARTING, ProcessState.RUNNING, ProcessState.STOPPING)
        val PYTHON_LOCK = Any()
    }
}
