package com.ebookreader.reader.settings

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ebookreader.data.model.ReaderTheme

/**
 * 阅读器底部设置面板
 */
@Composable
fun ReaderSettingsPanel(
    currentTheme: ReaderTheme,
    currentFontSize: Int,
    currentBrightness: Float,
    onThemeChange: (ReaderTheme) -> Unit,
    onFontSizeChange: (Int) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            // 手柄指示条
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
            )

            Spacer(Modifier.height(16.dp))

            // ── 主题切换 ──
            Text(
                text = "主题",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ThemeButton(
                    label = "白昼",
                    icon = Icons.Default.LightMode,
                    isSelected = currentTheme == ReaderTheme.DAY,
                    onClick = { onThemeChange(ReaderTheme.DAY) },
                )
                ThemeButton(
                    label = "羊皮纸",
                    icon = Icons.Default.WbTwilight,
                    isSelected = currentTheme == ReaderTheme.SEPIA,
                    onClick = { onThemeChange(ReaderTheme.SEPIA) },
                )
                ThemeButton(
                    label = "夜间",
                    icon = Icons.Default.DarkMode,
                    isSelected = currentTheme == ReaderTheme.NIGHT,
                    onClick = { onThemeChange(ReaderTheme.NIGHT) },
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── 字号调整 ──
            Text(
                text = "字号",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(
                    onClick = { onFontSizeChange(currentFontSize - 2) },
                    enabled = currentFontSize > 12,
                ) {
                    Icon(Icons.Default.TextDecrease, contentDescription = "缩小字号")
                }

                Slider(
                    value = currentFontSize.toFloat(),
                    onValueChange = { onFontSizeChange(it.toInt()) },
                    valueRange = 12f..32f,
                    steps = 9,
                    modifier = Modifier.weight(1f),
                )

                IconButton(
                    onClick = { onFontSizeChange(currentFontSize + 2) },
                    enabled = currentFontSize < 32,
                ) {
                    Icon(Icons.Default.TextIncrease, contentDescription = "增大字号")
                }
            }

            // 当前字号预览
            Text(
                text = "Aa",
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )

            Spacer(Modifier.height(16.dp))

            // ── 亮度调整 ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Default.BrightnessLow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = if (currentBrightness < 0) 0.5f else currentBrightness,
                    onValueChange = { onBrightnessChange(it) },
                    valueRange = 0.05f..1f,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Default.BrightnessHigh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ThemeButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant

    val textColor = if (isSelected)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .background(bgColor)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = textColor,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
        )
    }
}
