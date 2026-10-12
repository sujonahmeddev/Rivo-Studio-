package com.rivo.studio.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.ImportExport
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivo.studio.StudioUiState
import com.rivo.studio.StudioViewModel
import com.rivo.studio.core.model.ProjectRecord
import com.rivo.studio.core.model.StudioScreen
import com.rivo.studio.ui.components.BrandMark
import com.rivo.studio.ui.components.Eyebrow
import com.rivo.studio.ui.components.EmptyState
import com.rivo.studio.ui.components.MonoLabel
import com.rivo.studio.ui.components.ScreenHeading
import com.rivo.studio.ui.components.StatusPill
import com.rivo.studio.ui.components.StudioCard
import java.text.DateFormat
import java.util.Date

@Composable
fun HomeScreen(
    state: StudioUiState,
    viewModel: StudioViewModel,
    onImportFile: () -> Unit,
    onImportFolder: () -> Unit,
    onImportZip: () -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    var projectName by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 14.dp, bottom = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(42.dp)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("Rivo Studio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (state.language == "bn") "আপনার মোবাইল ডেভেলপমেন্ট স্টুডিও" else "Your mobile development studio", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusPill("LOCAL FIRST")
        }

        Spacer(Modifier.height(28.dp))
        Text(
            if (state.language == "bn") "ভাবনা থেকে\nকোডে এগিয়ে যান।" else "Make room for\nyour next idea.",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            lineHeight = 40.sp,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (state.language == "bn") "প্রজেক্ট তৈরি করুন, ফাইল সম্পাদনা করুন এবং Android-এ Python চালান।" else "Create real projects, edit code, and run supported Python scripts on your Android device.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(22.dp))
        Card(
            modifier = Modifier.fillMaxWidth().animateContentSize(),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)) {
                        Icon(Icons.Rounded.Code, contentDescription = null, modifier = Modifier.padding(12.dp).size(24.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (state.language == "bn") "একটি নতুন প্রজেক্ট শুরু করুন" else "Start a fresh project", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Private on this device by default", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Button(onClick = { projectName = ""; showCreate = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(7.dp))
                    Text(if (state.language == "bn") "প্রজেক্ট তৈরি করুন" else "Create project")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    OutlinedButton(onClick = onImportFile, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Rounded.UploadFile, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Import file", maxLines = 1)
                    }
                    OutlinedButton(onClick = onImportFolder, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Import folder", maxLines = 1)
                    }
                }
                TextButton(onClick = onImportZip, modifier = Modifier.align(Alignment.Start)) {
                    Icon(Icons.Rounded.ImportExport, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Import project ZIP")
                }
            }
        }

        if (state.project != null) {
            Spacer(Modifier.height(18.dp))
            ElevatedCard(
                modifier = Modifier.fillMaxWidth().clickable { viewModel.setScreen(StudioScreen.WORKSPACE) },
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Continue working", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(state.project.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = "Open project", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(Modifier.height(28.dp))
        ScreenHeading(
            title = if (state.language == "bn") "সাম্প্রতিক প্রজেক্ট" else "Your projects",
            subtitle = "${state.projects.size} stored locally",
            trailing = { if (state.projects.isNotEmpty()) StatusPill("${state.projects.size} PROJECT${if (state.projects.size == 1) "" else "S"}") },
        )
        Spacer(Modifier.height(12.dp))

        AnimatedVisibility(visible = state.projects.isEmpty()) {
            StudioCard(modifier = Modifier.fillMaxWidth()) {
                EmptyState(
                    title = "Nothing here yet",
                    body = "Create a project or import a file. Your project folders stay in Rivo's private device storage.",
                )
            }
        }
        state.projects.forEach { project ->
            ProjectCard(
                project = project,
                onOpen = { viewModel.openProject(project.id) },
                onFavorite = { viewModel.toggleFavorite(project.id, !project.isFavorite) },
            )
            Spacer(Modifier.height(9.dp))
        }

        if (state.recentFiles.isNotEmpty() && state.project != null) {
            Spacer(Modifier.height(16.dp))
            Eyebrow("Recent files in ${state.project.name}")
            Spacer(Modifier.height(6.dp))
            state.recentFiles.take(5).forEach { file ->
                Row(
                    Modifier.fillMaxWidth().clickable { viewModel.openFile(file.relativePath) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(file.relativePath, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    MonoLabel(DateFormat.getDateInstance(DateFormat.SHORT).format(Date(file.openedAt)))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Rivo is a native Android editor and local Python runner. AI requests use only the provider you configure.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Create a project") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("A project is a real local folder that can contain nested files and folders.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = projectName,
                        onValueChange = { projectName = it.take(80) },
                        singleLine = true,
                        label = { Text("Project name") },
                        placeholder = { Text("Telegram helper") },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = projectName.isNotBlank(),
                    onClick = { viewModel.createProject(projectName); showCreate = false },
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProjectCard(project: ProjectRecord, onOpen: () -> Unit, onFavorite: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(start = 15.dp, top = 12.dp, end = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(13.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.padding(10.dp).size(21.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(project.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text("Updated ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(project.updatedAt))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onFavorite) {
                Icon(
                    if (project.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (project.isFavorite) "Remove favorite" else "Add favorite",
                    tint = if (project.isFavorite) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Rounded.KeyboardArrowRight, contentDescription = "Open", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
