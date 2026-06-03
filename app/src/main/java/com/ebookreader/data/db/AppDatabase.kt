package com.ebookreader.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ebookreader.data.model.Book
import com.ebookreader.data.model.BookFormat
import com.ebookreader.data.model.ReaderTheme

/**
 * Room 类型转换器 — 将枚举转为字符串存储
 */
class Converters {
    @androidx.room.TypeConverter
    fun fromBookFormat(value: BookFormat): String = value.name

    @androidx.room.TypeConverter
    fun toBookFormat(value: String): BookFormat = BookFormat.valueOf(value)

    @androidx.room.TypeConverter
    fun fromReaderTheme(value: ReaderTheme): String = value.name

    @androidx.room.TypeConverter
    fun toReaderTheme(value: String): ReaderTheme = ReaderTheme.valueOf(value)
}

@Database(
    entities = [Book::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao

    companion object {
        private const val DB_NAME = "ebook_reader.db"

        /** 检测是否为 Release 构建（防止 release 版静默删数据） */
        private fun isReleaseBuild(context: Context): Boolean {
            return try {
                val info = context.packageManager.getApplicationInfo(
                    context.packageName, 0
                )
                (info.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) == 0
            } catch (_: Exception) {
                true
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    // Debug 模式下 schema 变更时静默重建（开发便捷）
                    // 生产环境有数据迁移需求请实现 Migration
                    .fallbackToDestructiveMigration(
                        !isReleaseBuild(context)
                    )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
