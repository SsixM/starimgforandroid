package ru.starimg.ai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.starimg.ai.R

/** Colors that Material's scheme has no slot for: bubbles, code, quotes, the glow. */
data class StarPalette(
    val bg: Color,
    val surface: Color,
    val raised: Color,
    val card: Color,
    val overlay: Color,
    val line: Color,
    val faint: Color,
    val scrim: Color,
    val userBubble: Color,
    val onUserBubble: Color,
    val assistant: Color,
    val codeBg: Color,
    val codeFg: Color,
    val quote: Color,
    val glow: Color,
    val accent: Color
)

val LocalStarPalette = staticCompositionLocalOf {
    StarPalette(
        Color.Black, Color.Black, Color.Black, Color.Black, Color.Black, Color.Black, Color.Black, Color.Black,
        Color.Black, Color.White, Color.White, Color.Black, Color.White, Color.Gray, Color.Transparent, Color.Black
    )
}

private val DarkPalette = StarPalette(
    bg = Color(0xFF0C0B11),
    surface = Color(0xFF13121A),
    raised = Color(0xFF1B1924),
    card = Color(0xFF23202E),
    overlay = Color(0xFF2C2838),
    line = Color(0xFF3A3448),
    faint = Color(0xFF8E85A3),
    scrim = Color(0xCC09080E),
    userBubble = Color(0xFF6E4BFF),
    onUserBubble = Color(0xFFF7F4FF),
    assistant = Color(0xFFEDE8F8),
    codeBg = Color(0xFF16141E),
    codeFg = Color(0xFFE4DCF8),
    quote = Color(0xFFB69CFF),
    glow = Color(0xFF7C5CFC),
    accent = Color(0xFFB69CFF)
)

private val LightPalette = StarPalette(
    bg = Color(0xFFF5F3FA),
    surface = Color(0xFFFFFFFF),
    raised = Color(0xFFFBFBFE),
    card = Color(0xFFFFFFFF),
    overlay = Color(0xFFE9E4F4),
    line = Color(0xFFDDD6EC),
    faint = Color(0xFF6E6680),
    scrim = Color(0xCCF5F3FA),
    userBubble = Color(0xFF6E4BFF),
    onUserBubble = Color(0xFFFFFFFF),
    assistant = Color(0xFF1A1722),
    codeBg = Color(0xFF221F2C),
    codeFg = Color(0xFFEDE8F8),
    quote = Color(0xFF6E4BFF),
    glow = Color(0xFF7C5CFC),
    accent = Color(0xFF6E4BFF)
)

private val Sans = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold)
)

val Mono = FontFamily(Font(R.font.jetbrainsmono_regular, FontWeight.Normal))

private fun style(size: Int, weight: FontWeight, line: Int, tracking: Double = 0.0) =
    TextStyle(fontFamily = Sans, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.sp)

private val Type = Typography(
    displaySmall = style(36, FontWeight.Bold, 44, -0.8),
    headlineLarge = style(30, FontWeight.Bold, 38, -0.6),
    headlineMedium = style(26, FontWeight.Bold, 34, -0.4),
    headlineSmall = style(22, FontWeight.SemiBold, 30, -0.3),
    titleLarge = style(20, FontWeight.SemiBold, 28, -0.2),
    titleMedium = style(16, FontWeight.SemiBold, 24),
    titleSmall = style(14, FontWeight.SemiBold, 20),
    bodyLarge = style(16, FontWeight.Normal, 24),
    bodyMedium = style(14, FontWeight.Normal, 21),
    bodySmall = style(12, FontWeight.Normal, 17),
    labelLarge = style(14, FontWeight.Medium, 20),
    labelMedium = style(12, FontWeight.Medium, 16),
    labelSmall = style(11, FontWeight.Medium, 15)
)

private fun StarPalette.asDarkScheme() = darkColorScheme(
    primary = accent, onPrimary = Color(0xFF1C1233), primaryContainer = Color(0xFF3A2A66), onPrimaryContainer = Color(0xFFE7DEFF),
    secondary = Color(0xFF9BE7C4), background = bg, onBackground = assistant, surface = surface, onSurface = assistant,
    surfaceVariant = card, onSurfaceVariant = faint, outline = line, error = Color(0xFFFF8C9F),
    errorContainer = Color(0xFF3B1D2A), onErrorContainer = Color(0xFFFFD9E0)
)

private fun StarPalette.asLightScheme() = lightColorScheme(
    primary = accent, onPrimary = Color.White, primaryContainer = Color(0xFFE7DEFF), onPrimaryContainer = Color(0xFF241452),
    secondary = Color(0xFF0E8F62), background = bg, onBackground = assistant, surface = surface, onSurface = assistant,
    surfaceVariant = overlay, onSurfaceVariant = faint, outline = line, error = Color(0xFFB4234A),
    errorContainer = Color(0xFFFFE1E7), onErrorContainer = Color(0xFF5C1024)
)

@Composable
fun StarTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (dark) DarkPalette else LightPalette
    androidx.compose.runtime.CompositionLocalProvider(LocalStarPalette provides palette) {
        MaterialTheme(colorScheme = if (dark) palette.asDarkScheme() else palette.asLightScheme(), typography = Type, content = content)
    }
}

/** The spacing and radius scale. Screens read these instead of writing raw numbers. */
object StarDim {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val radiusSm = 8.dp
    val radius = 12.dp
    val radiusLg = 16.dp
    val radiusXl = 24.dp
    val bubble = 22.dp
    val composer = 26.dp
}
