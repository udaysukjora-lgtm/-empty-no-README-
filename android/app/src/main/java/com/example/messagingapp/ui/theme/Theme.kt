package com.example.messagingapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF0D9488)
private val TealDark = Color(0xFF2DD4BF)

private val LightColors = lightColorScheme(
    primary = Teal,
    secondary = TealDark
)

private val DarkColors = darkColorScheme(
    primary = TealDark,
    secondary = Teal
)

@Composable
fun MessagingAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
