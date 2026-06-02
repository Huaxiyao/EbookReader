package com.ebookreader.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── 调色板 ──────────────────────────────────────────────
val Blue80 = Color(0xFFB3C5FF)
val Blue40 = Color(0xFF1A73E8)
val BlueGrey80 = Color(0xFFB0BEC5)
val BlueGrey40 = Color(0xFF546E7A)

val WarmBeige = Color(0xFFF5F0E8)      // 羊皮纸背景
val WarmBeigeDark = Color(0xFF3D3929)   // 羊皮纸深色文字

val NightBg = Color(0xFF1A1A2E)
val NightSurface = Color(0xFF16213E)

// ── 配色方案 ────────────────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary = Blue40,
    onPrimary = Color.White,
    primaryContainer = Blue80,
    background = Color(0xFFF8F9FA),
    surface = Color.White,
    onSurface = Color(0xFF202124),
    surfaceVariant = Color(0xFFE8EAED),
    onSurfaceVariant = Color(0xFF5F6368),
    outline = Color(0xFFDADCE0),
)

private val DarkColorScheme = darkColorScheme(
    primary = Blue80,
    onPrimary = Color(0xFF003D99),
    primaryContainer = Blue40,
    background = Color(0xFF1A1C1E),
    surface = Color(0xFF2D2F31),
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF3C4043),
    onSurfaceVariant = Color(0xFFBDC1C6),
    outline = Color(0xFF5F6368),
)

// ── 阅读器专用配色 ──────────────────────────────────────
object ReaderColors {
    /** 白昼模式 */
    val DayBg = Color(0xFFF8F9FA)
    val DayText = Color(0xFF202124)

    /** 羊皮纸模式 */
    val SepiaBg = Color(0xFFF5F0E8)
    val SepiaText = Color(0xFF3D3929)

    /** 夜间模式 */
    val NightBg = Color(0xFF0D1117)
    val NightText = Color(0xFFC9D1D9)
}

// ── Composable 主题 ─────────────────────────────────────
@Composable
fun EbookReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content,
    )
}
