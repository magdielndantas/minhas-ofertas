package com.minhasofertas.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Paleta Minimalista com Verde Primário #38AD3C
val Primary = Color(0xFF38AD3C)
val OnPrimary = Color(0xFFFFFFFF)
val PrimaryContainer = Color(0xFFC7F5C8)
val Surface = Color(0xFFFBFDFB)
val OnSurface = Color(0xFF191C19)
val SurfaceContainerLow = Color(0xFFF1F5F1)
val SurfaceContainerLowest = Color(0xFFFFFFFF)
val OutlineVariant = Color(0xFFD6DDD6)
val OnSurfaceVariant = Color(0xFF5C635C)
val TertiaryContainer = Color(0xFFE8F5E9)
val OnTertiaryContainer = Color(0xFF1B5E20)

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
