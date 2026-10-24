package app.rodalog.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F766E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCF5EE),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFFE8590C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE3D1),
    onSecondaryContainer = Color(0xFF3A1500),
    tertiary = Color(0xFF2F6DA8),
    background = Color(0xFFF3F7F6),
    onBackground = Color(0xFF151C1B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF151C1B),
    surfaceVariant = Color(0xFFE0EBE8),
    onSurfaceVariant = Color(0xFF3D4846),
    outline = Color(0xFF6D7977),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF5EEAD4),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF0F4F49),
    onPrimaryContainer = Color(0xFFCCF5EE),
    secondary = Color(0xFFFFA070),
    onSecondary = Color(0xFF4A1C00),
    secondaryContainer = Color(0xFF6B2D05),
    onSecondaryContainer = Color(0xFFFFE3D1),
    tertiary = Color(0xFF9CC9F2),
    background = Color(0xFF0D1413),
    onBackground = Color(0xFFE0E9E7),
    surface = Color(0xFF151D1C),
    onSurface = Color(0xFFE0E9E7),
    surfaceVariant = Color(0xFF263230),
    onSurfaceVariant = Color(0xFFBBC8C5),
    outline = Color(0xFF879391),
)

@Composable
fun RodaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = Typography(),
        shapes = Shapes(
            small = RoundedCornerShape(10.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp),
        ),
        content = content,
    )
}
