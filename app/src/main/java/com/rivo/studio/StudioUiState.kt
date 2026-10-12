package com.rivo.studio

import com.rivo.studio.core.model.AgentTaskRecord
import com.rivo.studio.core.model.AssistantMode
import com.rivo.studio.core.model.ConversationRecord
import com.rivo.studio.core.model.DependencyReport
import com.rivo.studio.core.model.EditorTab
import com.rivo.studio.core.model.ExecutionRecord
import com.rivo.studio.core.model.MessageRecord
import com.rivo.studio.core.model.ProcessSnapshot
import com.rivo.studio.core.model.ProjectNode
import com.rivo.studio.core.model.ProjectRecord
import com.rivo.studio.core.model.ProviderConfig
import com.rivo.studio.core.model.ProviderDiagnostic
import com.rivo.studio.core.model.RecoveryCandidate
import com.rivo.studio.core.model.StudioScreen

data class TransmissionSummary(
    val kind: Kind,
    val providerName: String,
    val fileLabels: List<String>,
    val projectPathCount: Int = 0,
    val characterCount: Int = 0,
    val redacted: Boolean = true,
) {
    enum class Kind { CHAT, AGENT }
}

data class EditorJump(
    val path: String,
    val start: Int,
    val end: Int,
    val sequence: Long,
)

data class StudioUiState(
    val screen: StudioScreen = StudioScreen.HOME,
    val darkTheme: Boolean = false,
    val fontSize: Int = 15,
    val language: String = "en",
    val projects: List<ProjectRecord> = emptyList(),
    val project: ProjectRecord? = null,
    val nodes: List<ProjectNode> = emptyList(),
    val projectSearchResults: List<Pair<String, Int>> = emptyList(),
    val recentFiles: List<com.rivo.studio.core.model.RecentFileRecord> = emptyList(),
    val tabs: List<EditorTab> = emptyList(),
    val activePath: String? = null,
    val showExplorer: Boolean = false,
    val showConsole: Boolean = false,
    val currentProcess: ProcessSnapshot? = null,
    val executionHistory: List<ExecutionRecord> = emptyList(),
    val syntaxResult: String? = null,
    val importResult: String? = null,
    val dependencyReport: DependencyReport? = null,
    val providerConfigs: List<ProviderConfig> = emptyList(),
    val defaultProviderId: String? = null,
    val providerDiagnostic: ProviderDiagnostic? = null,
    val providerModels: List<String> = emptyList(),
    val providerOperationId: String? = null,
    val conversations: List<ConversationRecord> = emptyList(),
    val conversationId: String? = null,
    val messages: List<MessageRecord> = emptyList(),
    val assistantMode: AssistantMode = AssistantMode.CHAT,
    val assistantBusy: Boolean = false,
    val agentTasks: List<AgentTaskRecord> = emptyList(),
    val selectedAgentTaskId: String? = null,
    val agentBusy: Boolean = false,
    val pendingTransmission: TransmissionSummary? = null,
    val pendingRunPath: String? = null,
    val pendingRecovery: RecoveryCandidate? = null,
    val pendingClosePath: String? = null,
    val pendingProjectSwitchId: String? = null,
    val editorJump: EditorJump? = null,
    val isWorking: Boolean = false,
    val snackbarMessage: String? = null,
)
