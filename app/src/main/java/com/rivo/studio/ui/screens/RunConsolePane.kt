package com.rivo.studio.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectionContainer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivo.studio.StudioUiState
import com.rivo.studio.StudioViewModel
import com.rivo.studio.core.model.ProcessState
import com.rivo.studio.ui.components.Eyebrow
import com.rivo.studio.ui.components.ScreenHeading
import com.rivo.studio.ui.components.StatusPill
import com.rivo.studio.ui.components.StudioCard
import java.text.DateFormat
import java.util.Date

@Composable
fun RunConsolePane(
    state: StudioUiState,
    viewModel: StudioViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        ScreenHeading(
            title = "Run Console",
            subtitle = "Local Python runtime · no shell commands",
            trailing = {
                TextButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = null)
                    Text("Close")
                }
            },
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = viewModel::runSyntaxCheck, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) {
                Icon(Icons.Rounded.BugReport, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text("Syntax")
            }
            OutlinedButton(onClick = viewModel::runImportCheck, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp)) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null)
                Spacer(Modifier.width(5.dp))
                Text("Imports")
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = viewModel::checkProjectRequirements, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
            Text("Check requirements.txt")
        }

        state.syntaxResult?.let { ResultCard("Syntax check", it, passed = it == "Python syntax check passed.") }
        state.importResult?.let { ResultCard("Import check", it, passed = it.startsWith("0 import") || it.contains("resolve in the local runtime.")) }
        state.dependencyReport?.let { report ->
            StudioCard(Modifier.fillMaxWidth(), contentPadding = 15.dp) {
                Eyebrow("Dependency report")
                Text(report.message, style = MaterialTheme.typography.bodyMedium)
                if (report.requirements.isNotEmpty()) {
                    Text("Available: ${report.bundled.ifEmpty { listOf("none") }.joinToString()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    if (report.missing.isNotEmpty()) Text("Not found in this APK: ${report.missing.joinToString()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Text("Rivo does not run pip on the device. Packages must be embedded at app build time; missing packages are not installed.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        val process = state.currentProcess?.takeIf { it.projectId == state.project?.id }
        if (process != null) {
            Spacer(Modifier.height(10.dp))
            StudioCard(Modifier.fillMaxWidth(), contentPadding = 15.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(process.scriptPath, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("Started ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(process.startedAt))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    StatusPill(process.state.name.lowercase().replace('_', ' '), color = processColor(process.state), container = processContainer(process.state))
                }
                if (process.state == ProcessState.STARTING) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                process.diagnostic?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (process.state in setOf(ProcessState.STARTING, ProcessState.RUNNING, ProcessState.STOPPING)) {
                        OutlinedButton(onClick = viewModel::stopCurrentProcess, shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Rounded.StopCircle, contentDescription = null)
                            Spacer(Modifier.width(5.dp))
                            Text(if (process.state == ProcessState.STOPPING) "Stop requested" else "Stop")
                        }
                    } else {
                        Button(onClick = viewModel::restartCurrentProcess, shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                            Spacer(Modifier.width(5.dp))
                            Text("Run again")
                        }
                    }
                    process.exitCode?.let { Text("Exit $it", modifier = Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Eyebrow("Process output", Modifier.padding(top = 14.dp))
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 360.dp),
                color = if (state.darkTheme) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.44f) else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
            ) {
                SelectionContainer {
                    Text(
                        process.output.ifBlank { "Waiting for Python output…" },
                        modifier = Modifier.verticalScroll(rememberScrollState()).padding(14.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        } else {
            Spacer(Modifier.height(12.dp))
            StudioCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
                Eyebrow("Runtime")
                Text("Embedded CPython 3.13 via Chaquopy. Rivo can run a selected .py file in the foreground app process; it does not provide a Linux terminal or process isolation.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Android can suspend or terminate an app in the background. Telegram bots are not guaranteed to run continuously.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(12.dp))
            state.executionHistory.take(8).forEach { record ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(record.state.lowercase(), style = MaterialTheme.typography.labelSmall, color = if (record.state == "FAILED") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(record.scriptPath, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(record.startedAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            }
        }
    }
}

@Composable
private fun ResultCard(title: String, result: String, passed: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (passed) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(result, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun processColor(state: ProcessState) = when (state) {
    ProcessState.RUNNING -> MaterialTheme.colorScheme.primary
    ProcessState.STARTING, ProcessState.STOPPING -> MaterialTheme.colorScheme.secondary
    ProcessState.EXITED -> MaterialTheme.colorScheme.primary
    ProcessState.FAILED -> MaterialTheme.colorScheme.error
}

@Composable
private fun processContainer(state: ProcessState) = when (state) {
    ProcessState.RUNNING -> MaterialTheme.colorScheme.primaryContainer
    ProcessState.STARTING, ProcessState.STOPPING -> MaterialTheme.colorScheme.secondaryContainer
    ProcessState.EXITED -> MaterialTheme.colorScheme.primaryContainer
    ProcessState.FAILED -> MaterialTheme.colorScheme.errorContainer
}
