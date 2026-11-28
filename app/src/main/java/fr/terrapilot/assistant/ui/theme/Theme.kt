package fr.terrapilot.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFE56F21),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE6D6),
    onPrimaryContainer = Color(0xFF3B1A08),
    secondary = Color(0xFF245B63),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8EEF0),
    onSecondaryContainer = Color(0xFF102E33),
    tertiary = Color(0xFF527A42),
    background = Color(0xFFF2F4F6),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8ECEF),
    onBackground = Color(0xFF172127),
    onSurface = Color(0xFF172127),
    onSurfaceVariant = Color(0xFF52616A),
    error = Color(0xFFBA3737),
    errorContainer = Color(0xFFFCE8E6),
    onErrorContainer = Color(0xFF5D1717)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF9A5A),
    onPrimary = Color(0xFF351607),
    primaryContainer = Color(0xFF6D3213),
    onPrimaryContainer = Color(0xFFFFDBCA),
    secondary = Color(0xFF9BD2D7),
    onSecondary = Color(0xFF13383D),
    secondaryContainer = Color(0xFF28545A),
    onSecondaryContainer = Color(0xFFD7F0F2),
    tertiary = Color(0xFFB2D39F),
    background = Color(0xFF11191E),
    surface = Color(0xFF192329),
    surfaceVariant = Color(0xFF29363D),
    onBackground = Color(0xFFE9EFF2),
    onSurface = Color(0xFFE9EFF2),
    onSurfaceVariant = Color(0xFFBAC6CB),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF5D1918),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun TerraPilotTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
