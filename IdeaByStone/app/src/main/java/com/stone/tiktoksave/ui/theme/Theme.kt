package com.stone.tiktoksave.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.stone.tiktoksave.data.SettingsRepository
import com.stone.tiktoksave.data.ThemeChoice
import kotlinx.coroutines.flow.map

/** Giao diện đang áp dụng (đã tính cả trường hợp theo hệ thống). */
val LocalThemeChoice = staticCompositionLocalOf { ThemeChoice.WHITE }

private data class ThemeState(val choice: ThemeChoice?)

/**
 * Đọc lựa chọn theme từ DataStore và áp dụng tức thì, không cần khởi động lại.
 * Lần đầu mở app (chưa chọn): theo hệ thống, sáng là Trắng, tối là Đen.
 */
@Composable
fun ThemedContent(settings: SettingsRepository, content: @Composable () -> Unit) {
    val flow = remember(settings) { settings.theme.map { ThemeState(it) } }
    val state by flow.collectAsState(initial = null)
    val loaded = state
    if (loaded != null) {
        val effective = loaded.choice ?: if (isSystemInDarkTheme()) ThemeChoice.BLACK else ThemeChoice.WHITE
        AppTheme(effective, content)
    }
}

@Composable
fun AppTheme(choice: ThemeChoice, content: @Composable () -> Unit) {
    val palette = Palettes.of(choice)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = palette.darkStatusIcons
                controller.isAppearanceLightNavigationBars = palette.darkStatusIcons
            }
        }
    }
    CompositionLocalProvider(LocalThemeChoice provides choice) {
        MaterialTheme(colorScheme = palette.toColorScheme(), content = content)
    }
}

private fun Palette.toColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = surface,
        onPrimaryContainer = onSurface,
        secondary = primary,
        onSecondary = onPrimary,
        secondaryContainer = surface,
        onSecondaryContainer = onSurface,
        tertiary = primary,
        onTertiary = onPrimary,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surface,
        onSurfaceVariant = onSurface.copy(alpha = 0.65f),
        surfaceTint = primary,
        outline = outline,
        outlineVariant = outline,
        error = error,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surface
    )
}
