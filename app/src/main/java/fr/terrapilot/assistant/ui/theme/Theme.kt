package fr.terrapilot.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFD96B00),
    onPrimary = Color.White,
    secondary = Color(0xFF1E5F74),
    onSecondary = Color.White,
    background = Color(0xFFF6F7F9),
    surface = Color.White,
    onBackground = Color(0xFF121417),
    onSurface = Color(0xFF121417),
    error = Color(0xFFC62828)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF9800),
    onPrimary = Color.Black,
    secondary = Color(0xFF4FC3F7),
    onSecondary = Color.Black,
    background = Color(0xFF121417),
    surface = Color(0xFF1E2228),
    onBackground = Color(0xFFF2F4F7),
    onSurface = Color(0xFFF2F4F7),
    error = Color(0xFFEF5350)
)

@Composable
fun TerraPilotTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
