package com.drdev.faphousepro.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkScheme = darkColorScheme(
    primary = FhGold,
    onPrimary = FhBlack,
    primaryContainer = FhGoldHover,
    onPrimaryContainer = FhBlack,
    secondary = FhGold,
    onSecondary = FhBlack,
    background = FhBg,
    onBackground = FhText,
    surface = FhCard,
    onSurface = FhText,
    surfaceVariant = FhCard,
    onSurfaceVariant = FhMuted,
    outline = FhBorder,
    outlineVariant = FhBorder,
    error = FhDanger,
    onError = FhBlack
)

@Composable
fun FapHouseProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkScheme,
        typography = FapTypography,
        content = content
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = FhBg.toArgb()
            window.navigationBarColor = FhBg.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
}