package com.ebookreader.reader

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.barteksc.pdfviewer.PDFView
import com.ebookreader.data.model.BookFormat
import com.ebookreader.data.model.ReaderTheme
import com.ebookreader.reader.renderer.ComicViewer
import com.ebookreader.reader.renderer.EpubRenderer
import com.ebookreader.reader.renderer.Fb2Renderer
import com.ebookreader.reader.renderer.MobiRenderer
import com.ebookreader.reader.renderer.PdfRenderer
import com.ebookreader.reader.settings.ReaderSettingsPanel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 创建各格式渲染器
    val epubRenderer = remember { EpubRenderer(context) }
    val pdfRenderer = remember { PdfRenderer(context) }
    val mobiRenderer = remember { MobiRenderer(context) }
    val fb2Renderer = remember { Fb2Renderer(context) }

    // 亮度覆盖
    val window = remember { (context as? android.app.Activity)?.window }

    LaunchedEffect(uiState.brightness) {
        if (uiState.brightness >= 0f) {
            val lp = window?.attributes
            if (lp != null) {
                lp.screenBrightness = uiState.brightness
                window.attributes = lp
            }
        }
    }

    // 清理
    DisposableEffect(Unit) {
        onDispose {
            epubRenderer.destroy()
            pdfRenderer.destroy()
            mobiRenderer.destroy()
            fb2Renderer.destroy()
            val lp = window?.attributes
            if (lp != null) {
                lp.screenBrightness = -1f
                window.attributes = lp
            }
        }
    }

    // 主题背景色
    val bgColor = when (uiState.theme) {
        ReaderTheme.DAY -> Color(0xFFF8F9FA)
        ReaderTheme.SEPIA -> Color(0xFFF5F0E8)
        ReaderTheme.NIGHT -> Color(0xFF0D1117)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val w = size.width
                    if (offset.x > w * 0.2f && offset.x < w * 0.8f) {
                        viewModel.onScreenTap()
                    }
                }
            }
    ) {
        when {
            uiState.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = when (uiState.theme) {
                        ReaderTheme.NIGHT -> Color.White
                        else -> MaterialTheme.colorScheme.primary
                    },
                )
            }

            // ── ePub ──
            uiState.format == BookFormat.EPUB -> {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            epubRenderer.initialize(this, uiState.filePath)
                            epubRenderer.setTheme(uiState.theme)
                            epubRenderer.setFontSize(uiState.fontSize)
                            epubRenderer.setOnPageChangeListener { page, total ->
                                viewModel.updateProgress(page, total)
                            }
                            if (uiState.progress > 0f) {
                                epubRenderer.seekTo(uiState.progress)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                LaunchedEffect(uiState.theme, uiState.fontSize) {
                    epubRenderer.setTheme(uiState.theme)
                    epubRenderer.setFontSize(uiState.fontSize)
                }
            }

            // ── PDF ──
            uiState.format == BookFormat.PDF -> {
                AndroidView(
                    factory = { ctx ->
                        PDFView(ctx, null).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            pdfRenderer.initialize(this, uiState.filePath)
                            pdfRenderer.setOnPageChangeListener { page, total ->
                                viewModel.updateProgress(page, total)
                            }
                            if (uiState.progress > 0f) {
                                pdfRenderer.seekTo(uiState.progress)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // ── MOBI / AZW3 ──
            uiState.format == BookFormat.MOBI || uiState.format == BookFormat.AZW3 -> {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            mobiRenderer.initialize(this, uiState.filePath)
                            mobiRenderer.setTheme(uiState.theme)
                            mobiRenderer.setFontSize(uiState.fontSize)
                            mobiRenderer.setOnPageChangeListener { page, total ->
                                viewModel.updateProgress(page, total)
                            }
                            if (uiState.progress > 0f) {
                                mobiRenderer.seekTo(uiState.progress)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                LaunchedEffect(uiState.theme, uiState.fontSize) {
                    mobiRenderer.setTheme(uiState.theme)
                    mobiRenderer.setFontSize(uiState.fontSize)
                }
            }

            // ── FB2 ──
            uiState.format == BookFormat.FB2 -> {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            fb2Renderer.initialize(this, uiState.filePath)
                            fb2Renderer.setTheme(uiState.theme)
                            fb2Renderer.setFontSize(uiState.fontSize)
                            fb2Renderer.setOnPageChangeListener { page, total ->
                                viewModel.updateProgress(page, total)
                            }
                            if (uiState.progress > 0f) {
                                fb2Renderer.seekTo(uiState.progress)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                LaunchedEffect(uiState.theme, uiState.fontSize) {
                    fb2Renderer.setTheme(uiState.theme)
                    fb2Renderer.setFontSize(uiState.fontSize)
                }
            }

            // ── CBZ / CBR 漫画 ──
            uiState.format == BookFormat.CBZ || uiState.format == BookFormat.CBR -> {
                ComicViewer(
                    filePath = uiState.filePath,
                    modifier = Modifier.fillMaxSize(),
                    onPageChange = { page, total ->
                        viewModel.updateProgress(page, total)
                    },
                )
            }

            // ── 未知格式 ──
            else -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Construction,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = if (uiState.theme == ReaderTheme.NIGHT)
                                Color.White.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "此格式（${uiState.format?.displayName ?: "未知"}）暂不支持",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (uiState.theme == ReaderTheme.NIGHT)
                                Color.White.copy(alpha = 0.7f)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ── 顶部工具栏 ──
        AnimatedVisibility(
            visible = uiState.showToolbar,
            enter = slideInVertically(initialOffsetY = { -it }),
            exit = slideOutVertically(targetOffsetY = { -it }),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = when (uiState.theme) {
                    ReaderTheme.NIGHT -> Color(0xFF1A1A2E).copy(alpha = 0.95f)
                    else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                },
                shadowElevation = 4.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = if (uiState.theme == ReaderTheme.NIGHT) Color.White
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.book?.title ?: "",
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            color = if (uiState.theme == ReaderTheme.NIGHT) Color.White
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        if (uiState.totalPages > 0) {
                            Text(
                                text = "${uiState.currentPage} / ${uiState.totalPages}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (uiState.theme == ReaderTheme.NIGHT)
                                    Color.White.copy(alpha = 0.6f)
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    IconButton(onClick = { viewModel.toggleSettings() }) {
                        Icon(
                            Icons.Default.TextFields,
                            contentDescription = "阅读设置",
                            tint = if (uiState.theme == ReaderTheme.NIGHT) Color.White
                            else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        // ── 底部设置面板 ──
        AnimatedVisibility(
            visible = uiState.showSettings,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ReaderSettingsPanel(
                currentTheme = uiState.theme,
                currentFontSize = uiState.fontSize,
                currentBrightness = uiState.brightness,
                onThemeChange = { viewModel.setTheme(it) },
                onFontSizeChange = { viewModel.setFontSize(it) },
                onBrightnessChange = { viewModel.setBrightness(it) },
                onClose = { viewModel.hideSettings() },
            )
        }

        // ── 底部进度条 ──
        if (!uiState.showToolbar && !uiState.showSettings && uiState.totalPages > 0) {
            LinearProgressIndicator(
                progress = uiState.progress,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
            )
        }
    }
}
