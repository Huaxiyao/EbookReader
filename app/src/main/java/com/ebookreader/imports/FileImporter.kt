package com.ebookreader.imports

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.ebookreader.data.model.Book
import com.ebookreader.data.model.BookFormat
import com.ebookreader.data.repository.BookRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 文件导入管理器 — 将外部文件复制到应用私有目录并入库
 */
@Singleton
class FileImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: BookRepository,
) {

    /** 书籍存储根目录 */
    private val booksDir: File
        get() = File(context.filesDir, "books").also { it.mkdirs() }

    /** 封面缓存目录 */
    private val coversDir: File
        get() = File(context.cacheDir, "covers").also { it.mkdirs() }

    /**
     * 从 content:// URI 导入一本书
     * @return 导入后的 Book，若已存在返回 null
     */
    suspend fun importFromUri(uri: Uri): Book? {
        val fileName = getFileName(uri) ?: return null
        val format = detectFormat(fileName) ?: return null

        // 检查是否已导入
        val destFile = File(booksDir, "${System.currentTimeMillis()}_$fileName")
        if (!copyUriToFile(uri, destFile)) return null

        // 检查重复
        val existing = repository.getBookByPath(destFile.absolutePath)
        if (existing != null) return null

        val title = fileNameWithoutExt(fileName)
        val fileSize = destFile.length()

        val book = Book(
            title = title,
            format = format,
            filePath = destFile.absolutePath,
            fileSize = fileSize,
            totalPages = estimatePageCount(format, fileSize),
        )

        val bookId = repository.insertBook(book)
        return book.copy(id = bookId)
    }

    /**
     * 从绝对文件路径导入（用于文件管理器直接打开）
     */
    suspend fun importFromPath(filePath: String): Book? {
        val file = File(filePath)
        if (!file.exists()) return null

        val format = detectFormat(file.name) ?: return null
        val existing = repository.getBookByPath(file.absolutePath)
        if (existing != null) return existing

        // 复制到应用私有目录
        val destFile = File(booksDir, "${System.currentTimeMillis()}_${file.name}")
        file.copyTo(destFile, overwrite = false)

        val title = fileNameWithoutExt(file.name)

        val book = Book(
            title = title,
            format = format,
            filePath = destFile.absolutePath,
            fileSize = destFile.length(),
            totalPages = estimatePageCount(format, destFile.length()),
        )

        val bookId = repository.insertBook(book)
        return book.copy(id = bookId)
    }

    /** 删除书籍的本地文件 + 封面缓存 */
    suspend fun deleteBookFiles(book: Book) {
        File(book.filePath).delete()
        book.coverPath?.let { File(it).delete() }
        repository.deleteBook(book)
    }

    // ── 私有辅助方法 ──────────────────────────────────────

    private fun detectFormat(fileName: String): BookFormat? {
        val ext = fileName.substringAfterLast('.', "")
        return BookFormat.fromExtension(ext)
    }

    private fun fileNameWithoutExt(fileName: String): String {
        return fileName.substringBeforeLast('.')
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
        // fallback: 从 URI 路径提取
        if (name == null) {
            name = uri.path?.substringAfterLast('/')
        }
        return name
    }

    private fun copyUriToFile(uri: Uri, dest: File): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 粗略估算页数（不同格式不同算法）
     * ePub: 按 1024 字节 ≈ 1 页估算
     * PDF: 按 4096 字节 ≈ 1 页估算
     * MOBI/AZW3: 按 2048 字节 ≈ 1 页
     * FB2: XML 文本，按 1024 字节 ≈ 1 页
     */
    private fun estimatePageCount(format: BookFormat, fileSize: Long): Int {
        val bytesPerPage = when (format) {
            BookFormat.EPUB, BookFormat.FB2 -> 1024L
            BookFormat.MOBI, BookFormat.AZW3 -> 2048L
            BookFormat.PDF -> 4096L
            BookFormat.CBZ, BookFormat.CBR -> 512 * 1024L // 每页约 512KB
        }
        return maxOf(1, (fileSize / bytesPerPage).toInt())
    }
}
