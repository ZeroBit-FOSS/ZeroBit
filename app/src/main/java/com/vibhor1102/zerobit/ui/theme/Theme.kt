package com.vibhor1102.zerobit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = ZeroBitGreen,
    background = ZeroBitBackground,
    surface = ZeroBitSurface,
    onSurface = ZeroBitOnSurface,
)

private val LightColors = lightColorScheme(
    primary = ZeroBitGreenDark,
)

@Composable
fun ZeroBitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

