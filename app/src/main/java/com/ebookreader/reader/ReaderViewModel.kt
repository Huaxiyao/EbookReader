package com.ebookreader.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ebookreader.data.model.Book
import com.ebookreader.data.model.BookFormat
import com.ebookreader.data.model.ReaderTheme
import com.ebookreader.data.repository.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReaderUiState(
    val book: Book? = null,
    val isLoading: Boolean = true,
    val format: BookFormat? = null,
    val filePath: String = "",
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val progress: Float = 0f,
    // 阅读设置
    val theme: ReaderTheme = ReaderTheme.DAY,
    val fontSize: Int = 18,
    val brightness: Float = -1f,
    // UI 状态
    val showSettings: Boolean = false,
    val showToolbar: Boolean = true,
    val isScrolling: Boolean = false,
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: BookRepository,
) : ViewModel() {

    private val bookId: Long = savedStateHandle.get<Long>("bookId") ?: -1L

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var saveProgressJob: Job? = null
    private var hideToolbarJob: Job? = null

    init {
        loadBook()
    }

    private fun loadBook() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val book = repository.getBookById(bookId)
            if (book != null) {
                _uiState.update {
                    it.copy(
                        book = book,
                        isLoading = false,
                        format = book.format,
                        filePath = book.filePath,
                        currentPage = book.currentPage,
                        totalPages = book.totalPages,
                        progress = book.progress,
                        theme = book.readerTheme,
                        fontSize = book.fontSize,
                        brightness = book.brightnessOverride,
                    )
                }
                // 恢复上次的阅读位置
                if (book.progress > 0f) {
                    _uiState.update { it.copy(progress = book.progress) }
                }
            } else {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * 更新阅读进度（由渲染器回调）
     */
    fun updateProgress(currentPage: Int, totalPages: Int) {
        val progress = if (totalPages > 0) {
            (currentPage.toFloat() / totalPages).coerceIn(0f, 1f)
        } else 0f

        _uiState.update {
            it.copy(
                currentPage = currentPage,
                totalPages = totalPages,
                progress = progress,
            )
        }

        // 延迟保存（防抖，1 秒内多次回调只保存一次）
        saveProgressJob?.cancel()
        saveProgressJob = viewModelScope.launch {
            delay(1000)
            repository.updateProgress(bookId, currentPage, progress)
        }
    }

    /**
     * 切换阅读主题
     */
    fun setTheme(theme: ReaderTheme) {
        _uiState.update { it.copy(theme = theme) }
        viewModelScope.launch {
            repository.updateReaderSettings(bookId, theme, _uiState.value.fontSize, _uiState.value.brightness)
        }
    }

    /**
     * 调整字号
     */
    fun setFontSize(size: Int) {
        val clamped = size.coerceIn(12, 32)
        _uiState.update { it.copy(fontSize = clamped) }
        viewModelScope.launch {
            repository.updateReaderSettings(bookId, _uiState.value.theme, clamped, _uiState.value.brightness)
        }
    }

    /**
     * 设置亮度覆盖
     */
    fun setBrightness(brightness: Float) {
        _uiState.update { it.copy(brightness = brightness) }
    }

    /**
     * 切换设置面板
     */
    fun toggleSettings() {
        _uiState.update { it.copy(showSettings = !it.showSettings) }
    }

    fun hideSettings() {
        _uiState.update { it.copy(showSettings = false) }
    }

    /**
     * 切换工具栏显示
     */
    fun toggleToolbar() {
        _uiState.update { it.copy(showToolbar = !it.showToolbar) }
    }

    /**
     * 点击屏幕中间区域 — 切换工具栏
     */
    fun onScreenTap() {
        if (_uiState.value.showSettings) {
            hideSettings()
        } else {
            toggleToolbar()
        }

        // 3 秒后自动隐藏工具栏
        if (_uiState.value.showToolbar) {
            hideToolbarJob?.cancel()
            hideToolbarJob = viewModelScope.launch {
                delay(3000)
                _uiState.update { it.copy(showToolbar = false) }
            }
        }
    }

    /**
     * 跳转到指定进度
     */
    fun seekTo(progress: Float) {
        _uiState.update { it.copy(progress = progress) }
    }

    override fun onCleared() {
        super.onCleared()
        // 离开页面时保存一次
        viewModelScope.launch {
            val state = _uiState.value
            repository.updateProgress(bookId, state.currentPage, state.progress)
        }
    }
}
