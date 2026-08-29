package com.dynorixz.smarthome.ui

import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.dynorixz.smarthome.data.local.ThemePreference
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme

@Composable
fun DynorixzTheme(
    dynamicColor: Boolean = true,
    theme: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit,
) {
    val useDark = when (theme) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    val fallback = if (useDark) darkColorScheme() else lightColorScheme()
    // An explicit light/dark choice must win over the system palette; otherwise
    // the theme selector appears to do nothing on devices with dynamic colors.
    val colors = if (dynamicColor && theme == ThemePreference.SYSTEM) {
        dynamicColorScheme(LocalContext.current) ?: fallback
    } else {
        fallback
    }
    MaterialTheme(colorScheme = colors) {
        Box(Modifier.fillMaxSize().background(colors.background)) { content() }
    }
}

private fun darkColorScheme(): ColorScheme = ColorScheme().copy(
    primary = Color(0xFF8BCDBB),
    primaryDim = Color(0xFF66AA99),
    primaryContainer = Color(0xFF173D35),
    onPrimary = Color(0xFF002019),
    onPrimaryContainer = Color(0xFFB9F2E1),
    secondary = Color(0xFFAFC7E8),
    secondaryContainer = Color(0xFF28394F),
    onSecondary = Color(0xFF102238),
    onSecondaryContainer = Color(0xFFD6E4FA),
    surfaceContainerLow = Color(0xFF111416),
    surfaceContainer = Color(0xFF181C1F),
    surfaceContainerHigh = Color(0xFF22272A),
    onSurface = Color(0xFFE3E7E5),
    onSurfaceVariant = Color(0xFFBEC9C5),
    background = Color(0xFF090B0C),
    onBackground = Color(0xFFE3E7E5),
)

private fun lightColorScheme(): ColorScheme = ColorScheme().copy(
    primary = Color(0xFF315E9B),
    primaryDim = Color(0xFF244C82),
    primaryContainer = Color(0xFFD8E7FF),
    onPrimary = Color.White,
    onPrimaryContainer = Color(0xFF0A2F5C),
    secondary = Color(0xFF4E6078),
    secondaryContainer = Color(0xFFD9E4F7),
    onSecondary = Color.White,
    onSecondaryContainer = Color(0xFF172B42),
    surfaceContainerLow = Color(0xFFF5F7FA),
    surfaceContainer = Color(0xFFECEFF3),
    surfaceContainerHigh = Color(0xFFE1E5EA),
    onSurface = Color(0xFF191C20),
    onSurfaceVariant = Color(0xFF43474E),
    background = Color(0xFFFBFCFF),
    onBackground = Color(0xFF191C20),
)
