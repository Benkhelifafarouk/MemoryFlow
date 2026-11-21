package com.memoryflow.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.memoryflow.app.data.SettingsStore

val Indigo = Color(0xFF6366F1)
val IndigoDeep = Color(0xFF4F46E5)
val Cyan = Color(0xFF22D3EE)
val Violet = Color(0xFFA855F7)
val Mint = Color(0xFF2DD4BF)
val Amber = Color(0xFFF59E0B)
val Rose = Color(0xFFFB7185)

private val LightScheme = lightColorScheme(
    primary = IndigoDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Color(0xFF0891B2),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color(0xFF083344),
    tertiary = Color(0xFF7C3AED),
    onTertiary = Color.White,
    background = Color(0xFFF6F7FC),
    onBackground = Color(0xFF11142A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF11142A),
    surfaceVariant = Color(0xFFEDEFF8),
    onSurfaceVariant = Color(0xFF565C7A),
    outline = Color(0xFFB9BED6),
    error = Color(0xFFDC2626),
    onError = Color.White
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFA5B4FC),
    onPrimary = Color(0xFF151A3A),
    primaryContainer = Color(0xFF2A2F63),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFF67E8F9),
    onSecondary = Color(0xFF062A33),
    secondaryContainer = Color(0xFF0E3F4A),
    onSecondaryContainer = Color(0xFFC3F5FC),
    tertiary = Color(0xFFC4B5FD),
    onTertiary = Color(0xFF241447),
    background = Color(0xFF070A18),
    onBackground = Color(0xFFE7E9F6),
    surface = Color(0xFF0E1330),
    onSurface = Color(0xFFE7E9F6),
    surfaceVariant = Color(0xFF191F3E),
    onSurfaceVariant = Color(0xFFA9AFC9),
    outline = Color(0xFF3A4166),
    error = Color(0xFFF87171),
    onError = Color(0xFF2A0A0A)
)

private val AppTypography = Typography(
    displaySmall = Typography().displaySmall.copy(fontSize = 34.sp, lineHeight = 40.sp),
    headlineMedium = Typography().headlineMedium.copy(fontSize = 27.sp, lineHeight = 34.sp),
    titleLarge = Typography().titleLarge.copy(fontSize = 21.sp, lineHeight = 28.sp),
    bodyLarge = Typography().bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
    labelLarge = Typography().labelLarge.copy(fontSize = 14.sp)
)

@Composable
fun MemoryFlowTheme(themeMode: String = SettingsStore.THEME_SYSTEM, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        SettingsStore.THEME_DARK -> true
        SettingsStore.THEME_LIGHT -> false
        else -> systemDark
    }
    val scheme = if (dark) DarkScheme else LightScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !dark
        }
    }

    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}
