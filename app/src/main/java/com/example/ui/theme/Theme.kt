package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme =
  darkColorScheme(
    primary = KavyaCoral,
    onPrimary = Color(0xFF2B070E),
    primaryContainer = KavyaCoralDarkContainer,
    onPrimaryContainer = Color(0xFFFFD9DD),
    secondary = KavyaIndigo,
    onSecondary = Color.White,
    secondaryContainer = KavyaIndigoDarkContainer,
    onSecondaryContainer = Color(0xFFE0DCFF),
    tertiary = KavyaMint,
    onTertiary = Color(0xFF002824),
    tertiaryContainer = KavyaMintDark,
    onTertiaryContainer = Color(0xFFAEF4EC),
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnBackground,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = Color(0xFFFF8A8A),
    onError = Color(0xFF42080E),
  )

private val LightColorScheme =
  lightColorScheme(
    primary = KavyaCoralDeep,
    onPrimary = Color.White,
    primaryContainer = KavyaCoralSoft,
    onPrimaryContainer = Color(0xFF56101B),
    secondary = KavyaIndigo,
    onSecondary = Color.White,
    secondaryContainer = KavyaIndigoSoft,
    onSecondaryContainer = Color(0xFF221566),
    tertiary = KavyaMint,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD5F7F3),
    onTertiaryContainer = Color(0xFF003B35),
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnBackground,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = Color(0xFFC62828),
    onError = Color.White,
  )

val KavyaShapes =
  Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

@Composable
fun KavyaTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    shapes = KavyaShapes,
    content = content,
  )
}
