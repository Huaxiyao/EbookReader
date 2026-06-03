package com.ebookreader.reader.renderer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * 漫画/图册渲染器 — 支持 CBZ (ZIP) 和 CBR (RAR)
 */
class ComicRenderer(private val context: Context) {

    private var pages: List<File> = emptyList()
    private var currentIndex: Int = 0

    fun load(filePath: String): Int {
        val file = File(filePath)
        if (!file.exists()) return 0

        val cacheDir = File(context.cacheDir, "comics/${file.nameWithoutExtension}")
        if (!cacheDir.exists()) cacheDir.mkdirs()

        // 仅当缓存为空时才解压
        val existingFiles = cacheDir.listFiles { f -> f.extension.lowercase() in imageExtensions }
        if (!existingFiles.isNullOrEmpty()) {
            pages = existingFiles.sortedBy { it.name }
            return pages.size
        }

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

        pages = cacheDir.listFiles()
            ?.filter { it.extension.lowercase() in imageExtensions }
            ?.sortedBy { it.name }
            ?: emptyList()

        return pages.size
    }

    fun getPageBitmap(index: Int): Bitmap? {
        if (index < 0 || index >= pages.size) return null
        currentIndex = index
        return try {
            BitmapFactory.decodeFile(pages[index].absolutePath)
        } catch (_: Exception) {
            null
        }
    }

    fun getPageCount(): Int = pages.size
    fun getCurrentIndex(): Int = currentIndex

    fun cleanup() {
        pages.forEach { it.delete() }
    }

    private fun extractCbz(file: File, dest: File) {
        try {
            ZipInputStream(file.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val outputFile = File(dest, entry.name.substringAfterLast('/'))
                        outputFile.parentFile?.mkdirs()
                        FileOutputStream(outputFile).use { fos ->
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
            // 使用 junrar 库 (v7.5.5) 的 Archive API
            val archive = com.github.junrar.Archive(file)
            for (fh in archive.fileHeaders) {
                if (!fh.isDirectory) {
                    val outputFile = File(dest, fh.fileName.substringAfterLast('/'))
                    outputFile.parentFile?.mkdirs()
                    FileOutputStream(outputFile).use { fos ->
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
    var totalPages by remember { mutableStateOf(0) }
    var currentPage by remember { mutableStateOf(0) }

    LaunchedEffect(filePath) {
        totalPages = renderer.load(filePath)
        onPageChange(1, totalPages)
    }

    DisposableEffect(Unit) {
        onDispose { renderer.cleanup() }
    }

    if (totalPages == 0) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("加载中…")
        }
    } else {
        val scrollState = rememberScrollState()

        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
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
                    Text("无法加载此页")
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = {
                        if (currentPage > 0) {
                            currentPage--
                            onPageChange(currentPage + 1, totalPages)
                        }
                    },
                    enabled = currentPage > 0,
                ) {
                    Text("上一页")
                }

                Spacer(Modifier.width(16.dp))

                Text(
                    text = "${currentPage + 1} / $totalPages",
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(Modifier.width(16.dp))

                TextButton(
                    onClick = {
                        if (currentPage < totalPages - 1) {
                            currentPage++
                            onPageChange(currentPage + 1, totalPages)
                        }
                    },
                    enabled = currentPage < totalPages - 1,
                ) {
                    Text("下一页")
                }
            }
        }
    }
}
