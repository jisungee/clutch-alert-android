package com.clutchalarm.app.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.lerp
import com.clutchalarm.app.domain.model.TeamBrands
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object ClutchStyle {
    val page = 16.dp
    val gap = 12.dp
    val card = RoundedCornerShape(20.dp)
    val control = RoundedCornerShape(16.dp)
    val red = Color(0xFFBE1536)
    val deepRed = Color(0xFF800F2C)
    val navy = Color(0xFF172238)
}

private val brandColors = lightColorScheme(
    primary = ClutchStyle.red, onPrimary = Color.White,
    error = Color(0xFFB3261E), onError = Color.White,
    primaryContainer = Color(0xFFFFE4EA), onPrimaryContainer = Color(0xFF65001D),
    secondary = ClutchStyle.red, onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE4EA), onSecondaryContainer = Color(0xFF65001D),
    tertiary = ClutchStyle.navy, onTertiary = Color.White,
    background = Color(0xFFF7F8FA), surface = Color.White,
    surfaceVariant = Color(0xFFF0F1F5), surfaceContainer = Color(0xFFF7F8FA),
    surfaceContainerHigh = Color(0xFFF0F1F5), surfaceContainerHighest = Color(0xFFF0F1F5), onSurface = ClutchStyle.navy,
    onBackground = ClutchStyle.navy, onSurfaceVariant = Color(0xFF555E70),
    outline = Color(0xFF8A909D), outlineVariant = Color(0xFFDEE1E8),
)

internal data class TeamPalette(
    val brandColor: Color, val primaryColor: Color, val onPrimaryColor: Color,
    val primaryContainer: Color, val onPrimaryContainer: Color,
)
internal fun teamPalette(teamId: String?): TeamPalette {
    val spec = TeamBrands.find(teamId)
    fun opaque(rgb: Int) = Color(0xFF000000L or rgb.toLong())
    return if (spec == null) TeamPalette(ClutchStyle.red, ClutchStyle.red, Color.White,
        brandColors.primaryContainer, brandColors.onPrimaryContainer)
    else TeamPalette(opaque(spec.brandColor), opaque(spec.primaryColor), opaque(spec.onPrimaryColor),
        opaque(spec.primaryContainer), opaque(spec.onPrimaryContainer))
}
internal val LocalTeamPalette = staticCompositionLocalOf { teamPalette(null) }

private fun type(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.SansSerif, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
)

@Composable
internal fun ClutchTheme(teamId: String? = null, content: @Composable () -> Unit) {
    val palette = teamPalette(teamId)
    val colors = brandColors.copy(primary = palette.primaryColor, onPrimary = palette.onPrimaryColor,
        primaryContainer = palette.primaryContainer, onPrimaryContainer = palette.onPrimaryContainer,
        secondary = palette.primaryColor, onSecondary = palette.onPrimaryColor,
        secondaryContainer = palette.primaryContainer, onSecondaryContainer = palette.onPrimaryContainer)
    MaterialTheme(colorScheme = colors, shapes = Shapes(
        small = ClutchStyle.control, medium = ClutchStyle.card, large = ClutchStyle.card,
    ), typography = Typography(
        headlineLarge = type(30, 38, FontWeight.Bold), headlineMedium = type(26, 34, FontWeight.Bold),
        headlineSmall = type(22, 30, FontWeight.Bold), titleLarge = type(20, 28, FontWeight.SemiBold),
        titleMedium = type(16, 24, FontWeight.SemiBold), titleSmall = type(14, 20, FontWeight.SemiBold),
        bodyLarge = type(16, 24), bodyMedium = type(14, 22), bodySmall = type(12, 18),
        labelLarge = type(14, 20, FontWeight.Medium), labelMedium = type(12, 16, FontWeight.Medium),
        labelSmall = type(11, 16, FontWeight.Medium),
    )) {
        CompositionLocalProvider(LocalContentColor provides colors.onBackground, LocalTeamPalette provides palette, content = content)
    }
}

@Composable
internal fun ClutchButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ClutchStyle.control, content: @Composable RowScope.() -> Unit) {
    Button(onClick = onClick, modifier = modifier.heightIn(min = 52.dp), enabled = enabled, shape = shape, content = content)
}

@Composable
internal fun ClutchOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    shape: Shape = ClutchStyle.control, content: @Composable RowScope.() -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 52.dp), enabled = enabled, shape = shape, content = content)
}
