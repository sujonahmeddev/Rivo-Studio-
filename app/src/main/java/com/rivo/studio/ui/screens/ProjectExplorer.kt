package com.rivo.studio.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivo.studio.StudioUiState
import com.rivo.studio.core.model.ProjectNode

sealed class ExplorerAction {
    data class Rename(val path: String, val currentName: String) : ExplorerAction()
    data class Copy(val path: String, val suggestedPath: String) : ExplorerAction()
    data class Move(val path: String, val suggestedPath: String) : ExplorerAction()
    data class Delete(val path: String) : ExplorerAction()
    data class Create(val parentPath: String, val directory: Boolean) : ExplorerAction()
}

@Composable
fun ProjectExplorer(
    state: StudioUiState,
    onOpenFile: (String) -> Unit,
    onAction: (ExplorerAction) -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val expanded = remember(state.project?.id) { mutableStateListOf<String>() }
    var filenameQuery by remember(state.project?.id) { mutableStateOf("") }
    val nodesByPath = remember(state.nodes) { state.nodes.associateBy(ProjectNode::relativePath) }
    val visibleNodes = remember(state.nodes, expanded.toList(), filenameQuery) {
        state.nodes.filter { node ->
            val nameMatches = filenameQuery.isBlank() || node.name.contains(filenameQuery, ignoreCase = true)
            var parent = node.relativePath.substringBeforeLast('/', "")
            var parentsExpanded = true
            while (parent.isNotEmpty()) {
                if (filenameQuery.isBlank() && nodesByPath[parent]?.isDirectory == true && parent !in expanded) parentsExpanded = false
                parent = parent.substringBeforeLast('/', "")
            }
            nameMatches && parentsExpanded
        }
    }

    Column(modifier.fillMaxSize().animateContentSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Explorer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(state.project?.name.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { onAction(ExplorerAction.Create("", directory = false)) }) {
                Icon(Icons.Rounded.Add, contentDescription = "New file")
            }
            IconButton(onClick = { onAction(ExplorerAction.Create("", directory = true)) }) {
                Icon(Icons.Rounded.CreateNewFolder, contentDescription = "New folder")
            }
            IconButton(onClick = onImport) { Icon(Icons.Rounded.UploadFile, contentDescription = "Import file") }
        }

        OutlinedTextField(
            value = filenameQuery,
            onValueChange = { filenameQuery = it.take(120) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            singleLine = true,
            label = { Text("Filter file names") },
            leadingIcon = { Icon(Icons.Rounded.Description, contentDescription = null) },
            shape = RoundedCornerShape(14.dp),
        )

        if (state.nodes.isEmpty()) {
            Text("This project is empty. Create a file or import source code.", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (visibleNodes.isEmpty()) {
            Text("No matching paths. Search file contents from the workspace toolbar.", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(Modifier.weight(1f).padding(top = 8.dp, bottom = 12.dp)) {
                items(visibleNodes, key = ProjectNode::relativePath) { node ->
                    ExplorerRow(
                        node = node,
                        selected = state.activePath == node.relativePath,
                        expanded = node.relativePath in expanded,
                        onClick = {
                            if (node.isDirectory) {
                                if (node.relativePath in expanded) expanded.remove(node.relativePath) else expanded.add(node.relativePath)
                            } else onOpenFile(node.relativePath)
                        },
                        onAction = onAction,
                    )
                }
            }
        }

        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${state.nodes.count { !it.isDirectory }} files", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text("App-private storage", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ExplorerRow(
    node: ProjectNode,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    onAction: (ExplorerAction) -> Unit,
) {
    val extension = node.name.substringAfterLast('.', "").lowercase()
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(start = (10 + node.depth * 14).dp, end = 7.dp, top = 1.dp, bottom = 1.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f) else MaterialTheme.colorScheme.surface,
                RoundedCornerShape(11.dp),
            )
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 3.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (node.isDirectory) {
            Icon(if (expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(4.dp))
            Icon(if (expanded) Icons.Rounded.FolderOpen else Icons.Rounded.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        } else {
            Spacer(Modifier.width(24.dp))
            Icon(Icons.Rounded.Description, contentDescription = null, tint = if (extension == "py") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(9.dp))
        Text(
            node.name,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = if (!node.isDirectory) FontFamily.Monospace else FontFamily.Default, fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = if (node.isDirectory) FontWeight.Medium else FontWeight.Normal,
        )
        if (!node.isDirectory && node.sizeBytes > 0) {
            Text(formatBytes(node.sizeBytes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BoxMenu(node, onAction)
    }
}

@Composable
private fun BoxMenu(node: ProjectNode, onAction: (ExplorerAction) -> Unit) {
    var expanded by remember(node.relativePath) { mutableStateOf(false) }
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(36.dp)) {
        Icon(Icons.Rounded.MoreVert, contentDescription = "Actions for ${node.name}", modifier = Modifier.size(19.dp))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        if (node.isDirectory) {
            DropdownMenuItem(text = { Text("New file here") }, leadingIcon = { Icon(Icons.Rounded.Add, null) }, onClick = { expanded = false; onAction(ExplorerAction.Create(node.relativePath, false)) })
            DropdownMenuItem(text = { Text("New folder here") }, leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, null) }, onClick = { expanded = false; onAction(ExplorerAction.Create(node.relativePath, true)) })
        }
        DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { expanded = false; onAction(ExplorerAction.Rename(node.relativePath, node.name)) })
        DropdownMenuItem(text = { Text("Copy") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = { expanded = false; onAction(ExplorerAction.Copy(node.relativePath, copyName(node))) })
        DropdownMenuItem(text = { Text("Move") }, leadingIcon = { Icon(Icons.Rounded.DriveFileMove, null) }, onClick = { expanded = false; onAction(ExplorerAction.Move(node.relativePath, node.relativePath)) })
        DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) }, onClick = { expanded = false; onAction(ExplorerAction.Delete(node.relativePath)) })
    }
}

private fun copyName(node: ProjectNode): String {
    if (node.isDirectory || '.' !in node.name) return node.relativePath + " copy"
    val base = node.name.substringBeforeLast('.')
    val extension = node.name.substringAfterLast('.')
    val parent = node.relativePath.substringBeforeLast('/', "")
    val copy = "$base copy.$extension"
    return if (parent.isBlank()) copy else "$parent/$copy"
}

private fun formatBytes(size: Long): String = when {
    size < 1024 -> "${size}B"
    size < 1024 * 1024 -> "${size / 1024}KB"
    else -> "${size / (1024 * 1024)}MB"
}
