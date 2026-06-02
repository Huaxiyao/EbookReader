package com.ebookreader.data.db

import androidx.room.*
import com.ebookreader.data.model.Book
import com.ebookreader.data.model.BookFormat
import com.ebookreader.data.model.ReaderTheme
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    /** 获取所有书籍，按最后阅读时间降序排列 */
    @Query("SELECT * FROM books ORDER BY lastReadAt DESC, importedAt DESC")
    fun getAllBooks(): Flow<List<Book>>

    /** 根据格式筛选书籍 */
    @Query("SELECT * FROM books WHERE format = :format ORDER BY lastReadAt DESC")
    fun getBooksByFormat(format: BookFormat): Flow<List<Book>>

    /** 搜索书名或作者 */
    @Query("SELECT * FROM books WHERE title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%' ORDER BY lastReadAt DESC")
    fun searchBooks(query: String): Flow<List<Book>>

    /** 根据 ID 获取单本书 */
    @Query("SELECT * FROM books WHERE id = :bookId")
    suspend fun getBookById(bookId: Long): Book?

    /** 根据文件路径查找（防止重复导入） */
    @Query("SELECT * FROM books WHERE filePath = :filePath LIMIT 1")
    suspend fun getBookByPath(filePath: String): Book?

    /** 插入新书，返回自增 ID */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: Book): Long

    /** 批量插入 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooks(books: List<Book>)

    /** 更新书籍信息 */
    @Update
    suspend fun updateBook(book: Book)

    /** 更新阅读进度 */
    @Query("UPDATE books SET currentPage = :page, progress = :progress, lastReadAt = :timestamp WHERE id = :bookId")
    suspend fun updateProgress(bookId: Long, page: Int, progress: Float, timestamp: Long = System.currentTimeMillis())

    /** 更新阅读设置（主题、字号、亮度） */
    @Query("UPDATE books SET readerTheme = :theme, fontSize = :fontSize, brightnessOverride = :brightness WHERE id = :bookId")
    suspend fun updateReaderSettings(bookId: Long, theme: ReaderTheme, fontSize: Int, brightness: Float)

    /** 删除书籍 */
    @Delete
    suspend fun deleteBook(book: Book)

    /** 删除所有书籍 */
    @Query("DELETE FROM books")
    suspend fun deleteAll()

    /** 统计书籍总数 */
    @Query("SELECT COUNT(*) FROM books")
    suspend fun getBookCount(): Int

    /** 最近阅读的一本书 */
    @Query("SELECT * FROM books ORDER BY lastReadAt DESC LIMIT 1")
    suspend fun getLastReadBook(): Book?
}
