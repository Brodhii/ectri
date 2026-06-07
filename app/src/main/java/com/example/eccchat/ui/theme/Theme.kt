package com.example.eccchat.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryPurple,
    onPrimary = Color.White,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    surface = DarkSurface,
    onSurface = OnDarkSurface,
    background = DarkBackground,
    onBackground = OnDarkSurface,
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0)
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryPurple,
    onPrimary = Color.White,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    surface = Color.White,
    onSurface = Color.Black,
    background = Color(0xFFFEF7FF),
    onBackground = Color.Black,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceLight
)

@Composable
fun ECCChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Force dynamicColor to false to maintain the specific purple aesthetic and avoid fading
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}