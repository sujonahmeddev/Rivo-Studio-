package com.rivo.studio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rivo.studio.core.model.StudioScreen
import com.rivo.studio.ui.components.StatusPill
import com.rivo.studio.ui.screens.AssistantScreen
import com.rivo.studio.ui.screens.HomeScreen
import com.rivo.studio.ui.screens.SettingsScreen
import com.rivo.studio.ui.screens.WorkspaceScreen

@Composable
fun RivoStudioApp(viewModel: StudioViewModel, state: StudioUiState) {
    val snackbarHostState = remember { SnackbarHostState() }

    val importFileAsProject = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importFileAsProject)
    }
    val importFileIntoProject = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importFileIntoProject)
    }
    val importFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::importFolderAsProject)
    }
    val importZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importZipAsProject)
    }
    val exportZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(viewModel::exportProjectZip)
    }

    LaunchedEffect(state.snackbarMessage) {
        val message = state.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = state.screen == StudioScreen.HOME,
                    onClick = { viewModel.setScreen(StudioScreen.HOME) },
                    icon = { androidx.compose.material3.Icon(Icons.Rounded.Home, contentDescription = null) },
                    label = { Text(if (state.language == "bn") "হোম" else "Home") },
                )
                NavigationBarItem(
                    selected = state.screen == StudioScreen.WORKSPACE,
                    onClick = { viewModel.setScreen(StudioScreen.WORKSPACE) },
                    icon = { androidx.compose.material3.Icon(Icons.Rounded.Code, contentDescription = null) },
                    label = { Text(if (state.language == "bn") "কোড" else "Workspace") },
                )
                NavigationBarItem(
                    selected = state.screen == StudioScreen.ASSISTANT,
                    onClick = { viewModel.setScreen(StudioScreen.ASSISTANT) },
                    icon = { androidx.compose.material3.Icon(Icons.Rounded.SmartToy, contentDescription = null) },
                    label = { Text(if (state.language == "bn") "এআই" else "AI Studio") },
                )
                NavigationBarItem(
                    selected = state.screen == StudioScreen.SETTINGS,
                    onClick = { viewModel.setScreen(StudioScreen.SETTINGS) },
                    icon = { androidx.compose.material3.Icon(Icons.Rounded.Settings, contentDescription = null) },
                    label = { Text(if (state.language == "bn") "সেটিংস" else "Settings") },
                )
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            AnimatedContent(
                targetState = state.screen,
                transitionSpec = {
                    (fadeIn() + slideInHorizontally { it / 20 }) togetherWith
                        (fadeOut() + slideOutHorizontally { -it / 20 })
                },
                label = "studio-screen-transition",
            ) { screen ->
                when (screen) {
                    StudioScreen.HOME -> HomeScreen(
                        state = state,
                        viewModel = viewModel,
                        onImportFile = { importFileAsProject.launch(arrayOf("*/*")) },
                        onImportFolder = { importFolder.launch(null) },
                        onImportZip = { importZip.launch(arrayOf("application/zip", "application/x-zip-compressed")) },
                    )
                    StudioScreen.WORKSPACE -> WorkspaceScreen(
                        state = state,
                        viewModel = viewModel,
                        onImportFile = { importFileIntoProject.launch(arrayOf("*/*")) },
                        onExportZip = {
                            val name = state.project?.name?.replace(Regex("[^A-Za-z0-9._-]"), "_") ?: "rivo-project"
                            exportZip.launch("$name.zip")
                        },
                    )
                    StudioScreen.ASSISTANT -> AssistantScreen(state, viewModel)
                    StudioScreen.SETTINGS -> SettingsScreen(state, viewModel)
                }
            }
            if (state.isWorking) {
                Column(Modifier.align(Alignment.TopCenter).padding(top = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    StatusPill(text = if (state.language == "bn") "কাজ চলছে" else "Working…")
                }
            }
        }
    }

    state.pendingTransmission?.let { request ->
        AlertDialog(
            onDismissRequest = viewModel::cancelPreparedTransmission,
            title = { Text(if (request.kind == TransmissionSummary.Kind.AGENT) "Review AI context" else "Share selected file?") },
            text = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    Text("Your request will be sent to ${request.providerName} over the internet. Rivo stores chat history locally; this provider handles the request under its own terms.")
                    if (request.fileLabels.isNotEmpty()) {
                        Text("Selected file(s): ${request.fileLabels.joinToString()}", fontWeight = FontWeight.SemiBold)
                        Text(if (request.kind == TransmissionSummary.Kind.AGENT) "About ${request.characterCount} characters of source text plus the project file paths will be included." else "About ${request.characterCount} characters from this selected file will be included.")
                        if (request.projectPathCount > 0) Text("Project structure: ${request.projectPathCount} path(s).")
                        Text("Rivo redacts some common token and secret patterns before sending, but this heuristic is not a guarantee. Review the file first.", color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("No project file contents are included. Your written prompt and previous conversation messages are sent.")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmPreparedTransmission) {
                    Text(if (request.fileLabels.isEmpty()) "Send request" else "Share for this request")
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelPreparedTransmission) { Text("Cancel") } },
        )
    }

    state.pendingRunPath?.let { path ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRunRequest,
            title = { Text("Run local Python script?") },
            text = {
                Text("$path will run inside Rivo Studio's Android app process with the app's network permission. This is not an isolated shell. Only run code you trust. Android may stop the app in the background; a bot is not guaranteed to stay online.")
            },
            confirmButton = { TextButton(onClick = viewModel::confirmRun) { Text("Run on device") } },
            dismissButton = { TextButton(onClick = viewModel::cancelRunRequest) { Text("Cancel") } },
        )
    }

    state.pendingClosePath?.let { path ->
        AlertDialog(
            onDismissRequest = viewModel::cancelCloseTab,
            title = { Text("Unsaved changes") },
            text = { Text("Save changes to $path before closing this tab?") },
            confirmButton = { TextButton(onClick = { viewModel.closeDirtyTab(save = true) }) { Text("Save & close") } },
            dismissButton = {
                androidx.compose.foundation.layout.Row {
                    TextButton(onClick = { viewModel.closeDirtyTab(save = false) }) { Text("Discard") }
                    TextButton(onClick = viewModel::cancelCloseTab) { Text("Keep open") }
                }
            },
        )
    }

    state.pendingProjectSwitchId?.let { id ->
        val name = state.projects.firstOrNull { it.id == id }?.name ?: "this project"
        AlertDialog(
            onDismissRequest = viewModel::cancelProjectSwitch,
            title = { Text("Unsaved editor tabs") },
            text = { Text("Save your open edits before switching to $name, or discard them? Recoverable drafts are kept until you choose a destructive discard.") },
            confirmButton = { TextButton(onClick = { viewModel.confirmProjectSwitch(saveChanges = true) }) { Text("Save & open") } },
            dismissButton = {
                androidx.compose.foundation.layout.Row {
                    TextButton(onClick = { viewModel.confirmProjectSwitch(saveChanges = false) }) { Text("Discard & open") }
                    TextButton(onClick = viewModel::cancelProjectSwitch) { Text("Cancel") }
                }
            },
        )
    }

    state.pendingRecovery?.let { recovery ->
        AlertDialog(
            onDismissRequest = { viewModel.discardRecovery(recovery) },
            title = { Text("Unsaved draft found") },
            text = { Text("Rivo found a recoverable local draft for ${recovery.relativePath}. Restore it into the editor? The project file will not be replaced until you save.") },
            confirmButton = { TextButton(onClick = { viewModel.restoreRecovery(recovery) }) { Text("Restore draft") } },
            dismissButton = { TextButton(onClick = { viewModel.discardRecovery(recovery) }) { Text("Discard draft") } },
        )
    }
}
