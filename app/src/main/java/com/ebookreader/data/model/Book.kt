package com.ebookreader.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 支持的电子书格式枚举
 */
enum class BookFormat(val extension: String, val displayName: String) {
    EPUB("epub", "ePub"),
    PDF("pdf", "PDF"),
    MOBI("mobi", "MOBI"),
    AZW3("azw3", "AZW3"),
    FB2("fb2", "FB2"),
    CBZ("cbz", "CBZ"),
    CBR("cbr", "CBR");

    companion object {
        /** 根据文件扩展名推断格式，不区分大小写 */
        fun fromExtension(ext: String): BookFormat? =
            entries.find { it.extension.equals(ext, ignoreCase = true) }

        /** 所有可导入的扩展名列表（小写） */
        val importableExtensions: List<String> =
            entries.map { it.extension }
    }
}

/**
 * 阅读主题
 */
enum class ReaderTheme(val label: String) {
    DAY("白昼"),
    SEPIA("羊皮纸"),
    NIGHT("夜间"),
}

/**
 * 书籍实体 — Room 数据库表
 */
@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 书名 */
    val title: String,

    /** 作者（无作者时为 "未知作者"） */
    val author: String = "未知作者",

    /** 文件格式 */
    val format: BookFormat,

    /** 文件在设备上的绝对路径 */
    val filePath: String,

    /** 文件大小（字节） */
    val fileSize: Long = 0,

    /** 封面图片路径（存为缓存文件） */
    val coverPath: String? = null,

    /** 总页数（不同格式计算方式不同） */
    val totalPages: Int = 0,

    /** 当前读到第几页 */
    val currentPage: Int = 0,

    /** 阅读进度 0.0 ~ 1.0 */
    val progress: Float = 0f,

    /** 当前使用的阅读主题 */
    val readerTheme: ReaderTheme = ReaderTheme.DAY,

    /** 字号（sp） */
    val fontSize: Int = 18,

    /** 阅读器亮度覆盖（-1 = 跟随系统） */
    val brightnessOverride: Float = -1f,

    /** 导入时间戳（毫秒） */
    val importedAt: Long = System.currentTimeMillis(),

    /** 最后阅读时间戳 */
    val lastReadAt: Long = 0L,

    /** 用户自定义标签（JSON 数组字符串） */
    val tags: String = "[]",
)
