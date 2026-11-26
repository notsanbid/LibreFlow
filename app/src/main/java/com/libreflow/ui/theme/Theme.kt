package com.libreflow.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.libreflow.R

val FlowBackground = Color(0xFFFBF9F5)
val FlowSurface = Color(0xFFFFFFFF)
val FlowSurfaceSubtle = Color(0xFFF3F0EA)
val FlowInk = Color(0xFF121316)
val FlowMuted = Color(0xFF64676E)
val FlowBorder = Color(0xFFEAE6DE)
val FlowEmerald = Color(0xFF059669)
val FlowEmeraldTint = Color(0xFFECFDF5)
val FlowAccent = Color(0xFF18181B)
val FlowDanger = Color(0xFFDC2626)

@OptIn(ExperimentalTextApi::class)
val FigtreeFamily = FontFamily(
    Font(
        resId = R.font.figtree_variable,
        weight = FontWeight.Light,
        variationSettings = FontVariation.Settings(FontVariation.weight(300))
    ),
    Font(
        resId = R.font.figtree_variable,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
        resId = R.font.figtree_variable,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
    Font(
        resId = R.font.figtree_variable,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))
    ),
    Font(
        resId = R.font.figtree_variable,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    ),
    Font(
        resId = R.font.figtree_variable,
        weight = FontWeight.ExtraBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(800))
    )
)

@OptIn(ExperimentalTextApi::class)
val GaramondFamily = FontFamily(
    Font(
        resId = R.font.eb_garamond_variable,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
        resId = R.font.eb_garamond_variable,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
    Font(
        resId = R.font.eb_garamond_variable,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))
    )
)

private val FlowLightColorScheme = lightColorScheme(
    primary = FlowInk,
    onPrimary = Color.White,
    secondary = FlowEmerald,
    onSecondary = Color.White,
    background = FlowBackground,
    onBackground = FlowInk,
    surface = FlowSurface,
    onSurface = FlowInk,
    surfaceVariant = FlowSurfaceSubtle,
    onSurfaceVariant = FlowMuted,
    outline = FlowBorder,
    error = FlowDanger
)

private val FlowTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = GaramondFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 32.sp,
        lineHeight = 38.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = GaramondFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 26.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FigtreeFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FigtreeFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FigtreeFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FigtreeFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        color = FlowMuted
    ),
    labelMedium = TextStyle(
        fontFamily = FigtreeFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FigtreeFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
)

@Composable
fun LibreFlowTheme(content: @Composable () -> Unit) {
    // Enforced light color scheme for consistent contrast across OEM skins
    MaterialTheme(
        colorScheme = FlowLightColorScheme,
        typography = FlowTypography,
        content = content
    )
}
