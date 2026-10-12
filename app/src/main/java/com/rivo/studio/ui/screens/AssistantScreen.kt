package com.rivo.studio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivo.studio.StudioUiState
import com.rivo.studio.StudioViewModel
import com.rivo.studio.core.agent.AgentChangeParser
import com.rivo.studio.core.agent.AgentCoordinator
import com.rivo.studio.core.agent.DiffPreview
import com.rivo.studio.core.model.AgentTaskRecord
import com.rivo.studio.core.model.AssistantMode
import com.rivo.studio.core.model.MessageRecord
import com.rivo.studio.core.model.StudioScreen
import com.rivo.studio.ui.components.BrandMark
import com.rivo.studio.ui.components.Eyebrow
import com.rivo.studio.ui.components.EmptyState
import com.rivo.studio.ui.components.ScreenHeading
import com.rivo.studio.ui.components.StatusPill
import com.rivo.studio.ui.components.StudioCard
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun AssistantScreen(state: StudioUiState, viewModel: StudioViewModel) {
    var showTaskConfirmation by remember { mutableStateOf(false) }
    var restoreTaskId by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(37.dp, 21.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("AI Studio", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(state.project?.name ?: "Project-aware assistant", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusPill(viewModel.activeProvider()?.name ?: "OFFLINE")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)) {
            FilterChip(
                selected = state.assistantMode == AssistantMode.CHAT,
                onClick = { viewModel.setAssistantMode(AssistantMode.CHAT) },
                label = { Text("Chat") },
                leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(17.dp)) },
            )
            FilterChip(
                selected = state.assistantMode == AssistantMode.AGENT,
                onClick = { viewModel.setAssistantMode(AssistantMode.AGENT) },
                label = { Text("Agent") },
                leadingIcon = { Icon(Icons.Rounded.CheckCircle, null, Modifier.size(17.dp)) },
            )
        }

        if (viewModel.activeProvider() == null) {
            StudioCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CloudOff, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("No AI provider configured", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Rivo remains a working offline editor. Add your own provider key to use AI.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { viewModel.setScreen(StudioScreen.SETTINGS) }) { Text("Settings") }
                }
            }
            Spacer(Modifier.height(10.dp))
        } else {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f), shape = RoundedCornerShape(12.dp)) {
                Text(
                    "Requests use ${viewModel.activeProvider()?.name} over HTTPS. Prompts and conversation history are sent; project source is excluded unless you opt in and confirm.",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        when (state.assistantMode) {
            AssistantMode.CHAT -> ChatPanel(state, viewModel, modifier = Modifier.weight(1f))
            AssistantMode.AGENT -> AgentPanel(state, viewModel, onConfirmApply = { showTaskConfirmation = true }, onRestore = { restoreTaskId = it }, modifier = Modifier.weight(1f))
        }
    }

    val selectedTask = state.agentTasks.firstOrNull { it.id == state.selectedAgentTaskId }
    if (showTaskConfirmation && selectedTask != null) {
        AlertDialog(
            onDismissRequest = { showTaskConfirmation = false },
            title = { Text("Apply reviewed change?") },
            text = { Text("Rivo will replace ${selectedTask.proposalPath} with the staged content. Existing files get a local recovery snapshot first; the file is checked for concurrent edits. This does not execute the code.") },
            confirmButton = {
                TextButton(onClick = { viewModel.applyAgentTask(selectedTask.id); showTaskConfirmation = false }) { Text("Apply change") }
            },
            dismissButton = { TextButton(onClick = { showTaskConfirmation = false }) { Text("Keep reviewing") } },
        )
    }
    restoreTaskId?.let { taskId ->
        val task = state.agentTasks.firstOrNull { it.id == taskId }
        AlertDialog(
            onDismissRequest = { restoreTaskId = null },
            title = { Text("Restore before-change snapshot?") },
            text = { Text("This will replace the current ${task?.proposalPath ?: "file"} with the saved local version from before the agent change.") },
            confirmButton = { TextButton(onClick = { viewModel.restoreAgentTaskSnapshot(taskId); restoreTaskId = null }) { Text("Restore snapshot") } },
            dismissButton = { TextButton(onClick = { restoreTaskId = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ChatPanel(state: StudioUiState, viewModel: StudioViewModel, modifier: Modifier = Modifier) {
    var prompt by remember(state.project?.id, state.conversationId) { mutableStateOf("") }
    var includeFile by remember(state.activePath) { mutableStateOf(false) }
    var historyExpanded by remember { mutableStateOf(false) }
    val messagesScroll = rememberLazyListState()
    Column(modifier.fillMaxSize()) {
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) messagesScroll.animateScrollToItem(state.messages.lastIndex)
    }

    Row(Modifier.fillMaxWidth().padding(bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Conversation", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Box {
            TextButton(onClick = { historyExpanded = true }) {
                Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(state.conversations.firstOrNull { it.id == state.conversationId }?.title ?: "Current chat", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            DropdownMenu(expanded = historyExpanded, onDismissRequest = { historyExpanded = false }) {
                state.conversations.forEach { conversation ->
                    DropdownMenuItem(
                        text = { Text(conversation.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        onClick = { historyExpanded = false; viewModel.selectConversation(conversation.id) },
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = viewModel::createConversation) { Icon(Icons.Rounded.Add, contentDescription = "New conversation") }
    }

    if (state.messages.isEmpty() && !state.assistantBusy) {
        StudioCard(Modifier.fillMaxWidth().weight(1f), contentPadding = 12.dp) {
            EmptyState(
                title = "A thoughtful coding partner",
                body = "Ask a question, explain a snippet, or request a review. Rivo never claims to have run code unless it really has.",
                modifier = Modifier.weight(1f),
                icon = "AI",
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            state = messagesScroll,
            verticalArrangement = Arrangement.spacedBy(9.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        ) {
            items(state.messages, key = MessageRecord::id) { message -> MessageBubble(message) }
            if (state.assistantBusy) {
                item(key = "assistant-thinking") {
                    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(9.dp))
                        Text("Waiting for the configured provider…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = viewModel::cancelChatRequest) { Icon(Icons.Rounded.StopCircle, null); Text("Cancel") }
                    }
                }
            }
        }
    }

    if (state.activePath != null) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Checkbox(checked = includeFile, onCheckedChange = { includeFile = it })
            Text("Include ${state.activePath} for this request", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        Text("Open a file to optionally share it as context.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        PromptChip("Explain file", Modifier.weight(1f)) { prompt = "Explain the purpose and main control flow of the selected file."; includeFile = state.activePath != null }
        PromptChip("Review code", Modifier.weight(1f)) { prompt = "Review the selected file for correctness, security, and maintainability. Give findings first."; includeFile = state.activePath != null }
        PromptChip("Plan a feature", Modifier.weight(1f)) { prompt = "Help me plan this feature before proposing any code changes." }
    }

    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it.take(4_000) },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Ask about your code…") },
            minLines = 1,
            maxLines = 5,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (!state.assistantBusy) { viewModel.prepareChat(prompt, includeFile); prompt = "" } }),
            shape = RoundedCornerShape(18.dp),
        )
        Button(
            onClick = { viewModel.prepareChat(prompt, includeFile); prompt = "" },
            enabled = prompt.isNotBlank() && !state.assistantBusy && viewModel.activeProvider() != null,
            modifier = Modifier.size(54.dp),
            shape = RoundedCornerShape(17.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) { Icon(Icons.Rounded.Send, contentDescription = "Send") }
    }
    Text("Only the prompt and recent chat turns are sent unless you check file context. Context sharing asks for one-time confirmation.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 5.dp, bottom = 1.dp))
    }
}

@Composable
private fun MessageBubble(message: MessageRecord) {
    val isUser = message.role == "user"
    val isError = message.role == "error"
    val background = when {
        isUser -> MaterialTheme.colorScheme.primaryContainer
        isError -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Card(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.9f else 1f),
            shape = RoundedCornerShape(if (isUser) 20.dp else 15.dp),
            colors = CardDefaults.cardColors(containerColor = background),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    when (message.role) { "user" -> "YOU"; "error" -> "PROVIDER ERROR"; else -> "RIVO ASSISTANT" },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.7.sp,
                )
                message.contextLabel?.let { Text("Context: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(message.content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun PromptChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AgentPanel(
    state: StudioUiState,
    viewModel: StudioViewModel,
    onConfirmApply: () -> Unit,
    onRestore: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var prompt by remember(state.project?.id) { mutableStateOf("") }
    var includeContext by remember(state.activePath) { mutableStateOf(false) }
    val selected = state.agentTasks.firstOrNull { it.id == state.selectedAgentTaskId }
    Column(modifier.fillMaxSize()) {
        StudioCard(Modifier.fillMaxWidth(), contentPadding = 13.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Tool-using project agent", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("Inspect → plan → review. No command or dependency is run automatically.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it.take(4_000) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("What should the agent investigate or propose?") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(15.dp),
            )
            if (state.activePath != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeContext, onCheckedChange = { includeContext = it })
                    Text("Share project paths and ${state.activePath}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Text("The agent can inspect project filenames locally. Open a file to optionally share source context.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.agentBusy) {
                OutlinedButton(onClick = viewModel::cancelAgentTask, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.StopCircle, null)
                    Spacer(Modifier.width(7.dp))
                    Text("Cancel task")
                }
            } else {
                Button(
                    onClick = {
                        viewModel.prepareAgentTask(prompt, includeContext)
                        prompt = ""
                    },
                    enabled = prompt.isNotBlank() && viewModel.activeProvider() != null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null)
                    Spacer(Modifier.width(7.dp))
                    Text("Start agent task")
                }
            }
        }
        if (state.agentTasks.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("Task history")
                Spacer(Modifier.weight(1f))
                Text("${state.agentTasks.size} recent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(state.agentTasks, key = AgentTaskRecord::id) { task ->
                    FilterChip(
                        selected = task.id == state.selectedAgentTaskId,
                        onClick = { viewModel.selectAgentTask(task.id) },
                        label = { Text("${task.status.replace('_', ' ').lowercase()} · ${task.prompt.take(24)}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }
        if (selected == null) {
            EmptyState(
                title = "Your agent stays in control",
                body = "The agent reads selected project data, drafts one-file changes, and waits for your review before writing anything.",
                modifier = Modifier.weight(1f),
                icon = "TASK",
            )
        } else {
            AgentTaskDetail(
                task = selected,
                state = state,
                viewModel = viewModel,
                onConfirmApply = onConfirmApply,
                onRestore = onRestore,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AgentTaskDetail(
    task: AgentTaskRecord,
    state: StudioUiState,
    viewModel: StudioViewModel,
    onConfirmApply: () -> Unit,
    onRestore: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 9.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        StudioCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow("${task.status.replace('_', ' ')}")
                Spacer(Modifier.weight(1f))
                StatusPill(if (task.proposalPath != null) "CHANGE STAGED" else "NO FILE CHANGE")
            }
            Text(task.summary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("Task: ${task.prompt}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val audit = remember(task.toolAuditJson) { runCatching { JSONArray(task.toolAuditJson) }.getOrNull() }
            if (audit != null && audit.length() > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    (0 until audit.length()).forEach { index ->
                        val item = audit.optJSONObject(index) ?: JSONObject()
                        StatusPill(item.optString("tool", "tool").replace('_', ' '), color = MaterialTheme.colorScheme.onSurfaceVariant, container = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }

        if (task.proposalPath != null && task.proposalContent != null) {
            val diff = remember(task.id, task.originalContent, task.proposalContent) {
                DiffPreview.render(task.originalContent, task.proposalContent)
            }
            StudioCard(Modifier.fillMaxWidth(), contentPadding = 13.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Eyebrow("Review before apply")
                        Text(task.proposalPath, style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace)
                    }
                    StatusPill(if (task.originalContent == null) "NEW FILE" else "DIFF")
                }
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(
                            diff,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()).padding(11.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                if (task.originalContent == null) Text("This proposal creates a new file. Confirm the path and contents before applying.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (task.status == AgentCoordinator.STATUS_REVIEW) {
                        Button(onClick = onConfirmApply, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Review & apply") }
                    }
                    if (task.status == AgentCoordinator.STATUS_APPLIED) {
                        Button(onClick = { viewModel.checkAgentTask(task.id) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Run Python checks") }
                    }
                    if (task.snapshotId != null) {
                        TextButton(onClick = { onRestore(task.id) }) { Text("Restore snapshot") }
                    }
                }
                Text("Applying creates a recoverable local snapshot for existing files. Tests and code execution are separate and never started by the agent.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (task.response.isNotBlank()) {
            StudioCard(Modifier.fillMaxWidth(), contentPadding = 13.dp) {
                Eyebrow("Plan & agent report")
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(task.response, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
