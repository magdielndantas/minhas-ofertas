package com.minhasofertas.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Primary = Color(0xFFAF2700)
val OnPrimary = Color(0xFFFFEFEC)
val PrimaryContainer = Color(0xFFFF7856)
val Surface = Color(0xFFFFF4F3)
val OnSurface = Color(0xFF4E2122)
val SurfaceContainerLow = Color(0xFFFFEDEC)
val SurfaceContainerLowest = Color(0xFFFFFFFF)
val OutlineVariant = Color(0xFFDF9C9B)
val OnSurfaceVariant = Color(0xFF834C4C)
val TertiaryContainer = Color(0xFFFDC003)
val OnTertiaryContainer = Color(0xFF553E00)

val AppColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    surface = Surface,
    onSurface = OnSurface,
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainerLowest = SurfaceContainerLowest,
    outlineVariant = OutlineVariant,
    onSurfaceVariant = OnSurfaceVariant,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer
)

val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp
    )
)
