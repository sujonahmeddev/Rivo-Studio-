package com.rivo.studio.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivo.studio.StudioUiState
import com.rivo.studio.StudioViewModel
import com.rivo.studio.core.model.ProviderConfig
import com.rivo.studio.core.model.ProviderKind
import com.rivo.studio.ui.components.Eyebrow
import com.rivo.studio.ui.components.ScreenHeading
import com.rivo.studio.ui.components.StatusPill
import com.rivo.studio.ui.components.StudioCard
import java.util.UUID

@Composable
fun SettingsScreen(state: StudioUiState, viewModel: StudioViewModel) {
    var showProviderEditor by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<ProviderConfig?>(null) }
    var deleteProvider by remember { mutableStateOf<ProviderConfig?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeading("Settings", "Rivo Studio is local-first by design")

        StudioCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Palette, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Comfortable, adjustable code workspace", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalSettingRow("Dark theme", "Optional dark editor and panels") {
                Switch(checked = state.darkTheme, onCheckedChange = viewModel::setDarkTheme)
            }
            HorizontalSettingRow("Editor font", "${state.fontSize} sp · monospaced") {
                Text("${state.fontSize}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
            }
            Slider(
                value = state.fontSize.toFloat(),
                onValueChange = { viewModel.setFontSize(it.toInt()) },
                valueRange = 12f..24f,
                steps = 11,
            )
            HorizontalSettingRow("Interface language", if (state.language == "bn") "বাংলা · core navigation" else "English · full interface") {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { viewModel.setLanguage("en") }) { Text(if (state.language == "en") "EN ✓" else "EN") }
                    TextButton(onClick = { viewModel.setLanguage("bn") }) { Text(if (state.language == "bn") "বাং ✓" else "বাং") }
                }
            }
        }

        StudioCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Key, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("AI providers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Your credentials are encrypted with Android Keystore on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { editingProvider = null; viewModel.clearProviderFeedback(); showProviderEditor = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add AI provider")
                }
            }
            if (state.providerConfigs.isEmpty()) {
                Text("No providers saved. You can use Rivo fully offline without an AI provider.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.providerConfigs.forEach { provider ->
                ProviderCard(
                    provider = provider,
                    isDefault = provider.id == state.defaultProviderId,
                    onDefault = { viewModel.setDefaultProvider(provider.id) },
                    onEdit = { editingProvider = provider; viewModel.clearProviderFeedback(); showProviderEditor = true },
                    onDelete = { deleteProvider = provider },
                )
            }
            Text("Provider APIs have different protocols: OpenAI-compatible chat/models, Gemini generateContent/models, and Anthropic Messages/models. Streaming and tool calling are not used by this initial assistant.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        StudioCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(9.dp))
                Text("Privacy & local data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Text("Projects, editor settings, provider configuration, chat history, agent tasks, recovery drafts, and process metadata stay in app-private device storage by default. Android automatic app backup is disabled; project ZIP export is user initiated.", style = MaterialTheme.typography.bodySmall)
            Text("API keys are encrypted with an AES-GCM key held by Android Keystore. Project files and chat text are app-private but are not separately encrypted by Rivo. Device loss, uninstall, or clearing app data can permanently remove them.", style = MaterialTheme.typography.bodySmall)
            Text("AI requests require internet and send your prompt to the configured provider. Only the selected project context you approve is sent; common secrets are heuristically redacted. Review sensitive files before sharing.", style = MaterialTheme.typography.bodySmall)
        }

        StudioCard(Modifier.fillMaxWidth()) {
            Eyebrow("Local runtime")
            Text("CPython 3.13 via Chaquopy. The Android APK embeds compatible packages at build time; device-time pip installation and a general-purpose shell are not available. Python code runs inside the app process with app permissions.", style = MaterialTheme.typography.bodySmall)
            Text("The app may be stopped in the background. Do not rely on it for uninterrupted bot hosting.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Text("Rivo Studio · 0.1.0 · Android API 26+ · current device storage", modifier = Modifier.padding(bottom = 14.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (showProviderEditor) {
        ProviderEditorDialog(
            initial = editingProvider,
            state = state,
            onDismiss = { showProviderEditor = false },
            onTest = viewModel::testProvider,
            onFetchModels = viewModel::fetchProviderModels,
            onSave = { config, makeDefault -> viewModel.saveProvider(config, makeDefault); showProviderEditor = false },
        )
    }
    deleteProvider?.let { provider ->
        AlertDialog(
            onDismissRequest = { deleteProvider = null },
            title = { Text("Remove ${provider.name}?") },
            text = { Text("This deletes the encrypted provider entry from this device. It does not revoke the API key with the provider.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteProvider(provider.id); deleteProvider = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { deleteProvider = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HorizontalSettingRow(title: String, subtitle: String, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
private fun ProviderCard(
    provider: ProviderConfig,
    isDefault: Boolean,
    onDefault: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(provider.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("${provider.kind.title} · ${provider.model.ifBlank { "model not selected" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (isDefault) StatusPill("DEFAULT") else TextButton(onClick = onDefault) { Text("Make default") }
                IconButton(onClick = onEdit, modifier = Modifier.size(38.dp)) { Icon(Icons.Rounded.Edit, contentDescription = "Edit ${provider.name}") }
                IconButton(onClick = onDelete, modifier = Modifier.size(38.dp)) { Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove ${provider.name}", tint = MaterialTheme.colorScheme.error) }
            }
            Text(provider.baseUrl, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ProviderEditorDialog(
    initial: ProviderConfig?,
    state: StudioUiState,
    onDismiss: () -> Unit,
    onTest: (ProviderConfig) -> Unit,
    onFetchModels: (ProviderConfig) -> Unit,
    onSave: (ProviderConfig, Boolean) -> Unit,
) {
    var kind by remember(initial?.id) { mutableStateOf(initial?.kind ?: ProviderKind.OPENAI) }
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var baseUrl by remember(initial?.id) { mutableStateOf(initial?.baseUrl ?: kind.defaultBaseUrl) }
    var apiKey by remember(initial?.id) { mutableStateOf(initial?.apiKey ?: "") }
    var model by remember(initial?.id) { mutableStateOf(initial?.model ?: "") }
    var makeDefault by remember(initial?.id) { mutableStateOf(initial?.isDefault ?: state.providerConfigs.isEmpty()) }
    var showKey by remember { mutableStateOf(false) }
    val providerId = remember(initial?.id) { initial?.id ?: UUID.randomUUID().toString() }
    var kindMenu by remember { mutableStateOf(false) }
    var modelsMenu by remember { mutableStateOf(false) }
    val config = ProviderConfig(
        id = providerId,
        name = name.ifBlank { kind.title },
        kind = kind,
        baseUrl = baseUrl,
        apiKey = apiKey,
        model = model,
        isDefault = makeDefault,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add AI provider" else "Edit AI provider") },
        text = {
            Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Box {
                    OutlinedButton(onClick = { kindMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(kind.title, modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.ExpandMore, null)
                    }
                    DropdownMenu(expanded = kindMenu, onDismissRequest = { kindMenu = false }) {
                        ProviderKind.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(option.title) }, onClick = {
                                kind = option
                                if (initial == null || baseUrl == initial.baseUrl) baseUrl = option.defaultBaseUrl
                                kindMenu = false
                            })
                        }
                    }
                }
                OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, singleLine = true, label = { Text("Display name") }, placeholder = { Text(kind.title) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = baseUrl, onValueChange = { baseUrl = it.take(300) }, singleLine = true, label = { Text("HTTPS API base URL") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it.take(500) },
                    singleLine = true,
                    label = { Text("API key") },
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(value = model, onValueChange = { model = it.take(160) }, singleLine = true, label = { Text("Model name") }, placeholder = { Text("Choose or type a model ID") }, modifier = Modifier.fillMaxWidth())
                if (state.providerModels.isNotEmpty()) {
                    Box {
                        OutlinedButton(onClick = { modelsMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Choose fetched model", modifier = Modifier.weight(1f)); Icon(Icons.Rounded.ExpandMore, null) }
                        DropdownMenu(expanded = modelsMenu, onDismissRequest = { modelsMenu = false }) {
                            state.providerModels.take(40).forEach { fetched ->
                                DropdownMenuItem(text = { Text(fetched, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { model = fetched; modelsMenu = false })
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedButton(onClick = { onTest(config) }, enabled = state.providerOperationId != config.id, modifier = Modifier.weight(1f)) {
                        if (state.providerOperationId == config.id) androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(15.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Test connection")
                    }
                    OutlinedButton(onClick = { onFetchModels(config) }, enabled = state.providerOperationId != config.id, modifier = Modifier.weight(1f)) { Text("Fetch models") }
                }
                state.providerDiagnostic?.let { diagnostic ->
                    Text(diagnostic.message, style = MaterialTheme.typography.bodySmall, color = if (diagnostic.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = makeDefault, onCheckedChange = { makeDefault = it })
                    Text("Use as the default AI provider", style = MaterialTheme.typography.bodySmall)
                }
                Text("Keys are encrypted at rest with Android Keystore and sent only to this provider when you make a request. They are never added to prompts or exported project ZIPs.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(config, makeDefault) }) { Text("Save provider") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
