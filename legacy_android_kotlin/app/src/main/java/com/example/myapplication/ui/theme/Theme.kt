package com.example.myapplication.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = PurplePrimary,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = SlateSecondary,
    surface = SurfaceLight,
    background = SurfaceLight,
    error = ErrorRed,
)

private val DarkColors = darkColorScheme(
    primary = PurplePrimaryDark,
    onPrimary = androidx.compose.ui.graphics.Color.Black,
    secondary = SlateSecondary,
    surface = SurfaceDark,
    background = SurfaceDark,
    error = ErrorRed,
)

@Composable
fun BlacklistClientTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}

