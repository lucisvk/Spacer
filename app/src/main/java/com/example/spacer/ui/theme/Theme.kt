package com.example.spacer.ui.theme

import android.app.Activity
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

private val DarkColorScheme = darkColorScheme(
    primary = SpacerPurplePrimary,
    onPrimary = SpacerPurpleOnPrimary,
    secondary = SpacerPurplePrimary,
    tertiary = Purple80,
    background = SpacerPurpleBackground,
    surface = SpacerPurpleBackground,
    surfaceVariant = SpacerPurpleSurface,
    outline = SpacerPurpleOutline,
    onBackground = Color(0xFFF4EEFF),
    onSurface = Color(0xFFF4EEFF)
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

)

@Composable
fun SpacerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // keep most colors consistant through pages
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}