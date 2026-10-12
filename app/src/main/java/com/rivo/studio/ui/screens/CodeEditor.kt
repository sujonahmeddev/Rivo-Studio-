package com.rivo.studio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rivo.studio.EditorJump
import com.rivo.studio.StudioUiState
import com.rivo.studio.StudioViewModel
import com.rivo.studio.core.editor.LanguageDetector
import com.rivo.studio.core.editor.SyntaxHighlightingTransformation

@Composable
fun CodeEditor(
    state: StudioUiState,
    viewModel: StudioViewModel,
    jump: EditorJump?,
    modifier: Modifier = Modifier,
) {
    val tab = state.tabs.firstOrNull { it.relativePath == state.activePath }
    if (tab == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Choose a file from the project explorer, or create a new one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    var fieldValue by remember(tab.relativePath) { mutableStateOf(TextFieldValue(tab.text)) }
    val scrollState = remember(tab.relativePath) { androidx.compose.foundation.ScrollState(0) }
    LaunchedEffect(tab.text) {
        if (fieldValue.text != tab.text) {
            val position = fieldValue.selection.end.coerceAtMost(tab.text.length)
            fieldValue = TextFieldValue(tab.text, selection = TextRange(position))
        }
    }
    LaunchedEffect(jump?.sequence, tab.relativePath) {
        if (jump?.path == tab.relativePath) {
            val start = jump.start.coerceIn(0, tab.text.length)
            val end = jump.end.coerceIn(start, tab.text.length)
            fieldValue = TextFieldValue(tab.text, selection = TextRange(start, end))
        }
    }

    val lineHeight = (state.fontSize * 1.55f).sp
    val density = androidx.compose.ui.platform.LocalDensity.current
    val lineHeightDp = with(density) { lineHeight.toDp() }
    val lineCount = remember(tab.text) { tab.text.count { it == '\n' } + 1 }
    val showGutter = lineCount <= MAX_NUMBERED_LINES
    val lineNumbers = remember(lineCount, showGutter) {
        if (!showGutter) "" else buildString(lineCount * 4) {
            for (line in 1..lineCount) {
                if (line > 1) append('\n')
                append(line)
            }
        }
    }
    val safeCount = if (showGutter) lineCount else 1

    Surface(
        modifier = modifier.fillMaxSize().clip(RoundedCornerShape(17.dp)),
        color = MaterialTheme.colorScheme.surface,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val contentHeight = maxOf(maxHeight, lineHeightDp * safeCount + 24.dp)
            Row(
                modifier = Modifier.fillMaxWidth()
                    .height(contentHeight)
                    .background(MaterialTheme.colorScheme.surface)
                    .verticalScroll(scrollState)
                    .padding(top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                if (showGutter) {
                    Text(
                        text = lineNumbers,
                        modifier = Modifier.width(42.dp).fillMaxHeight().padding(start = 3.dp, end = 9.dp),
                        style = TextStyle(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = state.fontSize.sp,
                            lineHeight = lineHeight,
                            textAlign = TextAlign.End,
                        ),
                        softWrap = false,
                        maxLines = Int.MAX_VALUE,
                    )
                } else {
                    Text("…", modifier = Modifier.width(42.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = fieldValue,
                    onValueChange = { proposed ->
                        val (adjusted, insertedIndent) = viewModel.onEditorInput(tab.relativePath, fieldValue.text, proposed.text)
                        val caret = (proposed.selection.end + insertedIndent).coerceIn(0, adjusted.length)
                        fieldValue = if (adjusted == proposed.text) proposed else TextFieldValue(adjusted, selection = TextRange(caret))
                    },
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(end = 12.dp),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace,
                        fontSize = state.fontSize.sp,
                        lineHeight = lineHeight,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    visualTransformation = SyntaxHighlightingTransformation(
                        language = LanguageDetector.fromPath(tab.relativePath),
                        darkTheme = state.darkTheme,
                        caret = fieldValue.selection.end,
                    ),
                    singleLine = false,
                    maxLines = Int.MAX_VALUE,
                    softWrap = false,
                    decorationBox = { innerTextField ->
                        Box {
                            if (fieldValue.text.isEmpty()) {
                                Text(
                                    "Start writing…",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = state.fontSize.sp,
                                    lineHeight = lineHeight,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }
            if (!showGutter) {
                Text(
                    "Line numbers hidden above ${MAX_NUMBERED_LINES / 1000}k lines",
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private const val MAX_NUMBERED_LINES = 50_000
