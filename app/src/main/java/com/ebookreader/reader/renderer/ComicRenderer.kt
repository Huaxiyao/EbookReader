package com.ebookreader.reader.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.junrar.Junrar
import com.github.junrar.extract.ExtractArchive
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

/**
 * 漫画/图册渲染器 — 支持 CBZ (ZIP) 和 CBR (RAR)
 *
 * 将压缩包内的图片逐页显示，支持缩放和平移。
 */
class ComicRenderer(private val context: Context) {

    private var pages: List<File> = emptyList()
    private var currentIndex: Int = 0

    /**
     * 解压并加载漫画文件
     * @return 图片页数
     */
    fun load(filePath: String): Int {
        val file = File(filePath)
        if (!file.exists()) return 0

        // 解压到缓存目录
        val cacheDir = File(context.cacheDir, "comics/${file.nameWithoutExtension}")
        if (cacheDir.exists()) cacheDir.deleteRecursively()
        cacheDir.mkdirs()

        when {
            filePath.endsWith(".cbz", ignoreCase = true) ||
            filePath.endsWith(".zip", ignoreCase = true) -> {
                extractCbz(file, cacheDir)
            }
            filePath.endsWith(".cbr", ignoreCase = true) ||
            filePath.endsWith(".rar", ignoreCase = true) -> {
                extractCbr(file, cacheDir)
            }
        }

        // 按文件名排序
        pages = cacheDir.listFiles()
            ?.filter { it.extension.lowercase() in imageExtensions }
            ?.sortedBy { it.name }
            ?: emptyList()

        return pages.size
    }

    /**
     * 获取指定页的 Bitmap
     */
    fun getPageBitmap(index: Int): Bitmap? {
        if (index < 0 || index >= pages.size) return null
        currentIndex = index
        return try {
            BitmapFactory.decodeFile(pages[index].absolutePath)
        } catch (e: Exception) {
            null
        }
    }

    fun getPageCount(): Int = pages.size
    fun getCurrentIndex(): Int = currentIndex

    fun cleanup() {
        // 清理缓存（保留最近的）
        pages.forEach { it.delete() }
    }

    // ── 解压 ──

    private fun extractCbz(file: File, dest: File) {
        try {
            ZipInputStream(FileInputStream(file)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val outputFile = File(dest, entry.name)
                        outputFile.parentFile?.mkdirs()
                        outputFile.outputStream().use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ComicRenderer", "CBZ extract error", e)
        }
    }

    private fun extractCbr(file: File, dest: File) {
        try {
            val archive = com.github.junrar.Archive(file)
            for (fh in archive.fileHeaders) {
                if (!fh.isDirectory) {
                    val outputFile = File(dest, fh.fileName)
                    outputFile.parentFile?.mkdirs()
                    outputFile.outputStream().use { fos ->
                        archive.extractFile(fh, fos)
                    }
                }
            }
            archive.close()
        } catch (e: Exception) {
            android.util.Log.e("ComicRenderer", "CBR extract error", e)
        }
    }

    companion object {
        val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "avif")
    }
}

/**
 * 漫画阅读器 Compose 组件
 */
@Composable
fun ComicViewer(
    filePath: String,
    modifier: Modifier = Modifier,
    onPageChange: (Int, Int) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val renderer = remember { ComicRenderer(context) }
    var totalPages by remember { mutableIntStateOf(0) }
    var currentPage by remember { mutableIntStateOf(0) }

    LaunchedEffect(filePath) {
        totalPages = renderer.load(filePath)
        onPageChange(1, totalPages)
    }

    DisposableEffect(Unit) {
        onDispose { renderer.cleanup() }
    }

    if (totalPages == 0) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Text("加载中…")
        }
    } else {
        val scrollState = rememberScrollState()

        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 当前页
            val bitmap = remember(currentPage, filePath) {
                renderer.getPageBitmap(currentPage)
            }

            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "漫画第 ${currentPage + 1} 页",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Text("无法加载此页")
                }
            }

            // 页码导航
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.material3.TextButton(
                    onClick = {
                        if (currentPage > 0) {
                            currentPage--
                            onPageChange(currentPage + 1, totalPages)
                        }
                    },
                    enabled = currentPage > 0,
                ) {
                    androidx.compose.material3.Text("上一页")
                }

                Spacer(Modifier.width(16.dp))

                androidx.compose.material3.Text(
                    text = "${currentPage + 1} / $totalPages",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )

                Spacer(Modifier.width(16.dp))

                androidx.compose.material3.TextButton(
                    onClick = {
                        if (currentPage < totalPages - 1) {
                            currentPage++
                            onPageChange(currentPage + 1, totalPages)
                        }
                    },
                    enabled = currentPage < totalPages - 1,
                ) {
                    androidx.compose.material3.Text("下一页")
                }
            }
        }
    }
}
