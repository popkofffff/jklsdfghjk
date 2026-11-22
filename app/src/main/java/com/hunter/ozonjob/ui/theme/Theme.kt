package com.hunter.ozonjob.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OzonBlue = Color(0xFF005BFF)
private val HotOrange = Color(0xFFF91155)

private val LightColors = lightColorScheme(
    primary = OzonBlue,
    error = HotOrange
)

private val DarkColors = darkColorScheme(
    primary = OzonBlue,
    error = HotOrange
)

@Composable
fun HotShiftTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
