package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = Color(0xFF8F4C38),
    secondary = Color(0xFFF5DED8),
    tertiary = Color(0xFFE7EBD1),
    background = Color(0xFF201A19),
    surface = Color(0xFF322826),
    onPrimary = Color.White,
    onSecondary = Color(0xFF8F4C38),
    onTertiary = Color(0xFF454D1E),
    onBackground = Color(0xFFFDF8F6),
    onSurface = Color(0xFFFDF8F6)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Color(0xFF8F4C38),
    secondary = Color(0xFFF5DED8),
    tertiary = Color(0xFFE7EBD1),
    background = Color(0xFFFDF8F6),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color(0xFF8F4C38),
    onTertiary = Color(0xFF454D1E),
    onBackground = Color(0xFF201A19),
    onSurface = Color(0xFF201A19),
    outlineVariant = Color(0xFFF5DED8)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disable dynamic color to maintain consistent visual design layout
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
