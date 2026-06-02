package com.ebookreader.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ebookreader.data.model.Book
import com.ebookreader.data.model.BookFormat
import com.ebookreader.data.repository.BookRepository
import com.ebookreader.imports.FileImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val isLoading: Boolean = true,
    val filterFormat: BookFormat? = null,
    val searchQuery: String = "",
    val showImportPicker: Boolean = false,
    val importResult: String? = null,
    val isImporting: Boolean = false,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: BookRepository,
    private val fileImporter: FileImporter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        loadBooks()
    }

    private fun loadBooks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val flow = if (_uiState.value.filterFormat != null) {
                repository.getBooksByFormat(_uiState.value.filterFormat!!)
            } else if (_uiState.value.searchQuery.isNotBlank()) {
                repository.searchBooks(_uiState.value.searchQuery)
            } else {
                repository.allBooks
            }

            flow.collect { books ->
                _uiState.update {
                    it.copy(books = books, isLoading = false)
                }
            }
        }
    }

    fun setFilterFormat(format: BookFormat?) {
        _uiState.update { it.copy(filterFormat = format) }
        loadBooks()
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        loadBooks()
    }

    fun showImportPicker() {
        _uiState.update { it.copy(showImportPicker = true) }
    }

    fun hideImportPicker() {
        _uiState.update { it.copy(showImportPicker = false) }
    }

    /**
     * 导入选中的文件
     */
    fun importBooks(uris: List<Uri>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true, importResult = null) }

            var successCount = 0
            var skipCount = 0

            for (uri in uris) {
                val result = fileImporter.importFromUri(uri)
                if (result != null) successCount++ else skipCount++
            }

            val message = when {
                successCount > 0 && skipCount > 0 ->
                    "导入了 $successCount 本，$skipCount 本已存在"
                successCount > 0 -> "成功导入 $successCount 本书"
                skipCount > 0 -> "这 $skipCount 本书已在书架中"
                else -> "未能导入任何文件"
            }

            _uiState.update {
                it.copy(
                    isImporting = false,
                    showImportPicker = false,
                    importResult = message,
                )
            }
        }
    }

    fun clearImportResult() {
        _uiState.update { it.copy(importResult = null) }
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch {
            fileImporter.deleteBookFiles(book)
        }
    }
}
