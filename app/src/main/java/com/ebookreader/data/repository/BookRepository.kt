package com.ebookreader.data.repository

import com.ebookreader.data.db.BookDao
import com.ebookreader.data.model.Book
import com.ebookreader.data.model.BookFormat
import com.ebookreader.data.model.ReaderTheme
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 书籍仓库 — 数据层的统一入口
 */
@Singleton
class BookRepository @Inject constructor(
    private val bookDao: BookDao
) {

    /** 观察所有书籍（书架用） */
    val allBooks: Flow<List<Book>> = bookDao.getAllBooks()

    /** 根据格式筛选 */
    fun getBooksByFormat(format: BookFormat): Flow<List<Book>> =
        bookDao.getBooksByFormat(format)

    /** 搜索 */
    fun searchBooks(query: String): Flow<List<Book>> =
        bookDao.searchBooks(query)

    /** 根据 ID 获取 */
    suspend fun getBookById(bookId: Long): Book? =
        bookDao.getBookById(bookId)

    /** 查找文件是否已导入 */
    suspend fun getBookByPath(filePath: String): Book? =
        bookDao.getBookByPath(filePath)

    /** 插入新书 */
    suspend fun insertBook(book: Book): Long =
        bookDao.insertBook(book)

    /** 更新书籍 */
    suspend fun updateBook(book: Book) =
        bookDao.updateBook(book)

    /** 更新阅读进度 */
    suspend fun updateProgress(bookId: Long, page: Int, progress: Float) =
        bookDao.updateProgress(bookId, page, progress)

    /** 更新阅读设置 */
    suspend fun updateReaderSettings(
        bookId: Long,
        theme: ReaderTheme,
        fontSize: Int,
        brightness: Float,
    ) = bookDao.updateReaderSettings(bookId, theme, fontSize, brightness)

    /** 删除书籍（注：不会删除源文件） */
    suspend fun deleteBook(book: Book) =
        bookDao.deleteBook(book)

    /** 最近阅读 */
    suspend fun getLastReadBook(): Book? =
        bookDao.getLastReadBook()

    /** 书籍总数 */
    suspend fun getBookCount(): Int =
        bookDao.getBookCount()
}
