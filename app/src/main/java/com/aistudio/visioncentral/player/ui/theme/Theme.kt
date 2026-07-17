package com.aistudio.visioncentral.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = VisionPrimary,
    secondary = VisionSecondary,
    background = VisionBackground,
    surface = VisionSurface,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = VisionText,
    onSurface = VisionText
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
