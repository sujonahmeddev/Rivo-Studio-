package com.rivo.studio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subject
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import com.rivo.studio.StudioUiState
import com.rivo.studio.StudioViewModel
import com.rivo.studio.core.editor.LanguageDetector
import com.rivo.studio.core.model.EditorTab
import com.rivo.studio.core.project.TextEncoding
import com.rivo.studio.ui.components.Eyebrow
import com.rivo.studio.ui.components.MonoLabel
import com.rivo.studio.ui.components.StatusPill
import com.rivo.studio.ui.components.StudioCard

@Composable
fun WorkspaceScreen(
    state: StudioUiState,
    viewModel: StudioViewModel,
    onImportFile: () -> Unit,
    onExportZip: () -> Unit,
) {
    val wide = LocalConfiguration.current.screenWidthDp >= 780
    var showSearch by remember(state.project?.id) { mutableStateOf(false) }
    var showReplace by remember { mutableStateOf(false) }
    var showGoToLine by remember { mutableStateOf(false) }
    var showEncoding by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var lineInput by remember { mutableStateOf("") }
    var createAction by remember { mutableStateOf<ExplorerAction.Create?>(null) }
    var createName by remember { mutableStateOf("") }
    var renameAction by remember { mutableStateOf<ExplorerAction.Rename?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var pathAction by remember { mutableStateOf<PendingPathOperation?>(null) }
    var pathValue by remember { mutableStateOf("") }
    var deletePath by remember { mutableStateOf<String?>(null) }

    fun handleExplorerAction(action: ExplorerAction) {
        when (action) {
            is ExplorerAction.Create -> {
                createAction = action
                createName = ""
            }
            is ExplorerAction.Rename -> {
                renameAction = action
                renameValue = action.currentName
            }
            is ExplorerAction.Copy -> {
                pathAction = PendingPathOperation.Copy(action.path)
                pathValue = action.suggestedPath
            }
            is ExplorerAction.Move -> {
                pathAction = PendingPathOperation.Move(action.path)
                pathValue = action.suggestedPath
            }
            is ExplorerAction.Delete -> deletePath = action.path
        }
    }

    Column(Modifier.fillMaxSize()) {
        WorkspaceTopBar(
            state = state,
            wide = wide,
            onToggleExplorer = { viewModel.setExplorerVisible(!state.showExplorer) },
            onRun = viewModel::requestRunActiveFile,
            onConsole = { viewModel.setConsoleVisible(!state.showConsole) },
            onFindProject = { searchQuery = ""; showSearch = true },
            onImportFile = onImportFile,
            onExportZip = onExportZip,
            onSaveAll = viewModel::saveAllNow,
        )
        if (wide) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (state.showExplorer) {
                    ProjectExplorer(
                        state = state,
                        onOpenFile = viewModel::openFile,
                        onAction = ::handleExplorerAction,
                        onImport = onImportFile,
                        modifier = Modifier.width(260.dp).fillMaxHeight(),
                    )
                    Box(Modifier.width(1.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)))
                }
                if (state.showConsole) {
                    RunConsolePane(state, viewModel, onClose = { viewModel.setConsoleVisible(false) }, modifier = Modifier.weight(1f))
                } else {
                    EditorWorkspace(
                        state,
                        viewModel,
                        modifier = Modifier.weight(1f),
                        onSearch = { showReplace = true },
                        onGoToLine = { showGoToLine = true; lineInput = "" },
                        onEncoding = { showEncoding = true },
                    )
                }
            }
        } else {
            when {
                state.showConsole -> RunConsolePane(state, viewModel, onClose = { viewModel.setConsoleVisible(false) }, modifier = Modifier.weight(1f))
                state.showExplorer -> ProjectExplorer(
                    state = state,
                    onOpenFile = viewModel::openFile,
                    onAction = ::handleExplorerAction,
                    onImport = onImportFile,
                    modifier = Modifier.weight(1f),
                )
                else -> EditorWorkspace(
                    state,
                    viewModel,
                    modifier = Modifier.weight(1f),
                    onSearch = { showReplace = true },
                    onGoToLine = { showGoToLine = true; lineInput = "" },
                    onEncoding = { showEncoding = true },
                )
            }
        }
    }

    createAction?.let { request ->
        AlertDialog(
            onDismissRequest = { createAction = null },
            title = { Text(if (request.directory) "New folder" else "New file") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (request.parentPath.isNotBlank()) MonoLabel("In ${request.parentPath}")
                    OutlinedTextField(value = createName, onValueChange = { createName = it.take(180) }, singleLine = true, label = { Text("Name") })
                }
            },
            confirmButton = {
                TextButton(enabled = createName.isNotBlank(), onClick = {
                    viewModel.createFile(createName, request.directory, request.parentPath)
                    createAction = null
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { createAction = null }) { Text("Cancel") } },
        )
    }

    renameAction?.let { request ->
        AlertDialog(
            onDismissRequest = { renameAction = null },
            title = { Text("Rename item") },
            text = { OutlinedTextField(value = renameValue, onValueChange = { renameValue = it.take(180) }, singleLine = true, label = { Text("New name") }) },
            confirmButton = { TextButton(onClick = { viewModel.renamePath(request.path, renameValue); renameAction = null }) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { renameAction = null }) { Text("Cancel") } },
        )
    }

    pathAction?.let { request ->
        AlertDialog(
            onDismissRequest = { pathAction = null },
            title = { Text(if (request is PendingPathOperation.Copy) "Copy project item" else "Move project item") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the full destination path relative to the project root.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = pathValue, onValueChange = { pathValue = it.take(300) }, singleLine = true, label = { Text("Destination path") })
                }
            },
            confirmButton = { TextButton(enabled = pathValue.isNotBlank(), onClick = {
                when (request) {
                    is PendingPathOperation.Copy -> viewModel.copyPath(request.source, pathValue)
                    is PendingPathOperation.Move -> viewModel.movePath(request.source, pathValue)
                }
                pathAction = null
            }) { Text(if (request is PendingPathOperation.Copy) "Copy" else "Move") } },
            dismissButton = { TextButton(onClick = { pathAction = null }) { Text("Cancel") } },
        )
    }

    deletePath?.let { path ->
        AlertDialog(
            onDismissRequest = { deletePath = null },
            title = { Text("Delete from project?") },
            text = { Text("This permanently removes $path and everything inside it from this local project. Check that you have a backup before continuing.") },
            confirmButton = { TextButton(onClick = { viewModel.deletePath(path); deletePath = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deletePath = null }) { Text("Cancel") } },
        )
    }

    if (showReplace) {
        AlertDialog(
            onDismissRequest = { showReplace = false },
            title = { Text("Find and replace") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    OutlinedTextField(searchQuery, { searchQuery = it }, singleLine = true, label = { Text("Find") })
                    OutlinedTextField(replacement, { replacement = it }, singleLine = true, label = { Text("Replace with") })
                }
            },
            confirmButton = { TextButton(enabled = searchQuery.isNotEmpty(), onClick = {
                state.activePath?.let { viewModel.replaceAll(it, searchQuery, replacement) }
            }) { Text("Replace all") } },
            dismissButton = {
                Row {
                    TextButton(enabled = searchQuery.isNotEmpty(), onClick = {
                        state.activePath?.let { path -> viewModel.findNext(path, searchQuery, state.editorJump?.takeIf { it.path == path }?.end ?: 0) }
                    }) { Text("Find next") }
                    TextButton(onClick = { showReplace = false }) { Text("Done") }
                }
            },
        )
    }

    if (showSearch) {
        AlertDialog(
            onDismissRequest = { showSearch = false },
            title = { Text("Search project contents") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    OutlinedTextField(searchQuery, { searchQuery = it }, singleLine = true, label = { Text("Text to find") })
                    OutlinedButton(onClick = { viewModel.searchProject(searchQuery) }, enabled = searchQuery.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Search text files")
                    }
                    if (state.projectSearchResults.isNotEmpty()) {
                        LazyColumn(Modifier.heightIn(max = 250.dp), state = rememberLazyListState()) {
                            items(state.projectSearchResults, key = { "${it.first}:${it.second}" }) { (path, line) ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { viewModel.openFile(path, line); showSearch = false }.padding(vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(path, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                                        Text("Line $line", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Rounded.Code, contentDescription = "Open result", tint = MaterialTheme.colorScheme.primary)
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSearch = false }) { Text("Close") } },
        )
    }

    if (showGoToLine) {
        AlertDialog(
            onDismissRequest = { showGoToLine = false },
            title = { Text("Go to line") },
            text = {
                OutlinedTextField(
                    lineInput,
                    { lineInput = it.filter(Char::isDigit).take(7) },
                    singleLine = true,
                    label = { Text("Line number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = { TextButton(enabled = lineInput.toIntOrNull() != null, onClick = {
                state.activePath?.let { viewModel.goToLine(it, lineInput.toInt()) }
                showGoToLine = false
            }) { Text("Go") } },
            dismissButton = { TextButton(onClick = { showGoToLine = false }) { Text("Cancel") } },
        )
    }

    if (showEncoding) {
        val active = state.tabs.firstOrNull { it.relativePath == state.activePath }
        AlertDialog(
            onDismissRequest = { showEncoding = false },
            title = { Text("Text encoding") },
            text = {
                Column {
                    Text("Current: ${active?.charsetName ?: "UTF-8"}${if (active?.hasBom == true) " · BOM" else ""}", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    EncodingChoice("UTF-8", TextEncoding.UTF8.charset.name(), false) {
                        active?.relativePath?.let { viewModel.convertEncoding(it, TextEncoding.UTF8.charset.name(), false) }; showEncoding = false
                    }
                    EncodingChoice("UTF-8 with BOM", TextEncoding.UTF8.charset.name(), true) {
                        active?.relativePath?.let { viewModel.convertEncoding(it, TextEncoding.UTF8.charset.name(), true) }; showEncoding = false
                    }
                    EncodingChoice("UTF-16 little-endian", TextEncoding.UTF16_LE.charset.name(), true) {
                        active?.relativePath?.let { viewModel.convertEncoding(it, TextEncoding.UTF16_LE.charset.name(), true) }; showEncoding = false
                    }
                    EncodingChoice("UTF-16 big-endian", TextEncoding.UTF16_BE.charset.name(), true) {
                        active?.relativePath?.let { viewModel.convertEncoding(it, TextEncoding.UTF16_BE.charset.name(), true) }; showEncoding = false
                    }
                    EncodingChoice("Windows-1252", TextEncoding.WINDOWS_1252.charset.name(), false) {
                        active?.relativePath?.let { viewModel.convertEncoding(it, TextEncoding.WINDOWS_1252.charset.name(), false) }; showEncoding = false
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showEncoding = false }) { Text("Done") } },
        )
    }
}

@Composable
private fun WorkspaceTopBar(
    state: StudioUiState,
    wide: Boolean,
    onToggleExplorer: () -> Unit,
    onRun: () -> Unit,
    onConsole: () -> Unit,
    onFindProject: () -> Unit,
    onImportFile: () -> Unit,
    onExportZip: () -> Unit,
    onSaveAll: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val path = state.activePath
    Column {
        Row(
            Modifier.fillMaxWidth().padding(start = 5.dp, end = 8.dp, top = 4.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onToggleExplorer) {
                Icon(if (state.showExplorer) Icons.Rounded.Code else Icons.Rounded.FolderOpen, contentDescription = "Toggle project explorer")
            }
            Column(Modifier.weight(1f)) {
                Text(state.project?.name ?: "Workspace", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(path ?: "Project explorer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onConsole) { Icon(Icons.Rounded.Subject, contentDescription = "Open run console") }
            IconButton(onClick = onRun, enabled = path?.endsWith(".py", true) == true) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Run Python file", tint = MaterialTheme.colorScheme.primary)
            }
            Box {
                IconButton(onClick = { expanded = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Project actions") }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("Save all open files") }, leadingIcon = { Icon(Icons.Rounded.Save, null) }, onClick = { expanded = false; onSaveAll() })
                    DropdownMenuItem(text = { Text("Search project contents") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, onClick = { expanded = false; onFindProject() })
                    DropdownMenuItem(text = { Text("Import file") }, leadingIcon = { Icon(Icons.Rounded.Add, null) }, onClick = { expanded = false; onImportFile() })
                    DropdownMenuItem(text = { Text("Export project ZIP") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = { expanded = false; onExportZip() })
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.32f))
    }
}

@Composable
private fun EditorWorkspace(
    state: StudioUiState,
    viewModel: StudioViewModel,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit,
    onGoToLine: () -> Unit,
    onEncoding: () -> Unit,
) {
    Column(modifier.fillMaxSize().padding(horizontal = 11.dp, vertical = 8.dp)) {
        if (state.tabs.isNotEmpty()) {
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                items(state.tabs, key = EditorTab::relativePath) { tab ->
                    EditorTabChip(
                        tab = tab,
                        selected = tab.relativePath == state.activePath,
                        onSelect = { viewModel.openFile(tab.relativePath) },
                        onClose = { viewModel.requestCloseTab(tab.relativePath) },
                    )
                }
            }
            Spacer(Modifier.height(7.dp))
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconButton(onClick = viewModel::saveActive, enabled = state.activePath != null) { Icon(Icons.Rounded.Save, contentDescription = "Save file") }
            IconButton(onClick = { state.activePath?.let(viewModel::undo) }, enabled = state.activePath != null) { Icon(Icons.Rounded.Undo, contentDescription = "Undo") }
            IconButton(onClick = { state.activePath?.let(viewModel::redo) }, enabled = state.activePath != null) { Icon(Icons.Rounded.Redo, contentDescription = "Redo") }
            IconButton(onClick = onSearch, enabled = state.activePath != null) { Icon(Icons.Rounded.Search, contentDescription = "Find and replace") }
            Spacer(Modifier.weight(1f))
            val tab = state.tabs.firstOrNull { it.relativePath == state.activePath }
            tab?.let {
                Text(if (it.isDirty) "UNSAVED" else "SAVED", style = MaterialTheme.typography.labelSmall, color = if (it.isDirty) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
            Box {
                var menuExpanded by remember { mutableStateOf(false) }
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "Editor options") }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Go to line") }, onClick = { menuExpanded = false; onGoToLine() })
                    DropdownMenuItem(text = { Text("Convert encoding") }, onClick = { menuExpanded = false; onEncoding() })
                }
            }
        }
        CodeEditor(
            state = state,
            viewModel = viewModel,
            jump = state.editorJump,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 2.dp),
        )
        val tab = state.tabs.firstOrNull { it.relativePath == state.activePath }
        Row(Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            MonoLabel(tab?.let { "${LanguageDetector.fromPath(it.relativePath).uppercase()} · ${it.charsetName}${if (it.hasBom) " BOM" else ""}" } ?: "PLAIN TEXT")
            Spacer(Modifier.weight(1f))
            Text("${tab?.text?.count { char -> char == '\n' }?.plus(1) ?: 0} lines", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EditorTabChip(tab: EditorTab, selected: Boolean, onSelect: () -> Unit, onClose: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onSelect),
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 6.dp, bottomEnd = 6.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(Modifier.padding(start = 10.dp, top = 7.dp, bottom = 7.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (tab.isDirty) "● " else "", color = MaterialTheme.colorScheme.secondary, fontSize = 8.sp)
            Text(tab.relativePath.substringAfterLast('/'), style = MaterialTheme.typography.labelMedium, maxLines = 1)
            IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) { Icon(Icons.Rounded.Close, contentDescription = "Close ${tab.relativePath}", modifier = Modifier.size(15.dp)) }
        }
    }
}

@Composable
private fun EncodingChoice(label: String, charsetName: String, bom: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
        if (charsetName == TextEncoding.UTF8.charset.name() && bom) StatusPill("BOM")
    }
}

private sealed class PendingPathOperation(val source: String) {
    class Copy(source: String) : PendingPathOperation(source)
    class Move(source: String) : PendingPathOperation(source)
}
