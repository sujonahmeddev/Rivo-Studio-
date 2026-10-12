package com.rivo.studio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RivoLight = lightColorScheme(
    primary = Color(0xFF1D746C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7EEE8),
    onPrimaryContainer = Color(0xFF103D38),
    secondary = Color(0xFF91622F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFE6C5),
    onSecondaryContainer = Color(0xFF38210B),
    background = Color(0xFFF5F8F6),
    onBackground = Color(0xFF17242B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17242B),
    surfaceVariant = Color(0xFFE9EFEC),
    onSurfaceVariant = Color(0xFF52625E),
    outline = Color(0xFFCBD6D1),
    error = Color(0xFFB3261E),
)

private val RivoDark = darkColorScheme(
    primary = Color(0xFF83CDC0),
    onPrimary = Color(0xFF063A35),
    primaryContainer = Color(0xFF20564E),
    onPrimaryContainer = Color(0xFFD5F4ED),
    secondary = Color(0xFFE7BC83),
    onSecondary = Color(0xFF3C250A),
    secondaryContainer = Color(0xFF61441F),
    onSecondaryContainer = Color(0xFFFFE8C8),
    background = Color(0xFF101A19),
    onBackground = Color(0xFFE1EAE6),
    surface = Color(0xFF172321),
    onSurface = Color(0xFFE1EAE6),
    surfaceVariant = Color(0xFF263430),
    onSurfaceVariant = Color(0xFFB9C9C2),
    outline = Color(0xFF50615C),
    error = Color(0xFFFFB4AB),
)

@Composable
fun RivoTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) RivoDark else RivoLight,
        typography = MaterialTheme.typography,
        content = content,
    )
}
