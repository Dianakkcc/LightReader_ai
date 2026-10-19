package com.lightreader.ui.library

import com.lightreader.R
import com.lightreader.core.util.AppStrings
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightreader.AppContainer
import com.lightreader.core.model.Book
import com.lightreader.core.model.BookFormat
import com.lightreader.core.model.AppThemeMode
import com.lightreader.core.model.LibraryLayout
import com.lightreader.core.model.LibrarySettings
import com.lightreader.core.model.LibrarySort
import com.lightreader.core.model.ShelfKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val importing: Boolean = false,
        val books: List<Book> = emptyList(),
        val query: String = "",
        val sort: LibrarySort = LibrarySort.RECENT,
        val formatFilter: BookFormat? = null,
        /** Which shelf is on screen: 书籍 or 漫画. */
        val shelf: ShelfKind = ShelfKind.BOOK,
        val layout: LibraryLayout = LibraryLayout.GRID,
        val gridColumns: Int = 0,
        val comicGridColumns: Int = 3,
        val showAuthor: Boolean = true,
        val showProgress: Boolean = true,
        val message: String? = null,
        val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
        val palette: com.lightreader.ui.theme.AppPalette = com.lightreader.ui.theme.AppPalette.BLUE,
    ) {
        val selectedColumns: Int
            get() = if (shelf == ShelfKind.COMIC) comicGridColumns else gridColumns
        /** Everything on the shelf that is currently selected, before search / format filters. */
        val shelfBooks: List<Book>
            get() = books.filter { it.shelfKind == shelf }

        fun countOn(kind: ShelfKind): Int = books.count { it.shelfKind == kind }

        val filtered: List<Book>
            get() {
                val needle = query.trim()
                var list = shelfBooks
                if (needle.isNotEmpty()) {
                    list = list.filter { book ->
                        book.title.contains(needle, ignoreCase = true) ||
                            book.author?.contains(needle, ignoreCase = true) == true ||
                            book.fileName.contains(needle, ignoreCase = true)
                    }
                }
                formatFilter?.let { format -> list = list.filter { it.format == format } }
                return when (sort) {
                    LibrarySort.RECENT -> list.sortedWith(
                        compareByDescending<Book> { it.lastOpenedAt ?: 0L }
                            .thenByDescending { it.addedAt },
                    )

                    LibrarySort.ADDED -> list.sortedByDescending { it.addedAt }
                    LibrarySort.TITLE -> list.sortedBy { it.title.lowercase() }
                    LibrarySort.AUTHOR -> list.sortedBy { it.author?.lowercase() ?: "\uFFFF" }
                    LibrarySort.PROGRESS -> list.sortedByDescending { it.progressPercent }
                }
            }

        val recent: List<Book>
            get() = shelfBooks
                .filter { (it.lastOpenedAt ?: 0L) > 0L && it.progressPercent > 0.001f }
                .sortedByDescending { it.lastOpenedAt }
                .take(6)
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.libraryRepository.observeBooks().collect { books ->
                _state.update { it.copy(books = books, loading = false) }
            }
        }
        viewModelScope.launch {
            container.settingsStore.settings.collect { settings ->
                val library: LibrarySettings = settings.library
                _state.update {
                    it.copy(
                        layout = library.layout,
                        gridColumns = library.gridColumns,
                        comicGridColumns = library.comicGridColumns,
                        sort = library.sort,
                        showAuthor = library.showAuthor,
                        showProgress = library.showProgress,
                        shelf = library.shelf,
                        themeMode = settings.themeMode,
                        palette = settings.palette,
                    )
                }
            }
        }
    }

    fun onQueryChange(query: String) = _state.update { it.copy(query = query) }

    fun setSort(sort: LibrarySort) {
        _state.update { it.copy(sort = sort) }
        viewModelScope.launch { container.settingsStore.updateLibrary { it.copy(sort = sort) } }
    }

    fun setLayout(layout: LibraryLayout) {
        _state.update { it.copy(layout = layout) }
        viewModelScope.launch { container.settingsStore.updateLibrary { it.copy(layout = layout) } }
    }

    fun setColumns(columns: Int) {
        val safe = if (columns == 0) 0 else columns.coerceIn(2, 6)
        val shelf = _state.value.shelf
        _state.update {
            if (shelf == ShelfKind.COMIC) it.copy(comicGridColumns = safe)
            else it.copy(gridColumns = safe)
        }
        viewModelScope.launch {
            container.settingsStore.updateLibrary {
                if (shelf == ShelfKind.COMIC) it.copy(comicGridColumns = safe)
                else it.copy(gridColumns = safe)
            }
        }
    }

    fun setFormatFilter(format: BookFormat?) = _state.update { it.copy(formatFilter = format) }

    /**
     * Switches between the 书籍 and 漫画 shelf. The format chip is cleared with it: a chip that
     * belongs to the other shelf would leave the user staring at an empty list for no reason.
     */
    fun setShelf(kind: ShelfKind) {
        _state.update { it.copy(shelf = kind, formatFilter = null) }
        viewModelScope.launch { container.settingsStore.updateLibrary { it.copy(shelf = kind) } }
    }

    /** Long press action: move a book between the two shelves. */
    fun setBookShelfKind(book: Book, kind: ShelfKind) {
        viewModelScope.launch {
            container.libraryRepository.setShelfKind(book.id, kind)
            _state.update {
                it.copy(message = AppStrings.get(R.string.ui_moved_to_58, book.title, kind.displayName))
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    /** One tap switch used by the shelf header; SYSTEM is offered in the settings screen. */
    fun toggleThemeMode() {
        val next = when (_state.value.themeMode) {
            AppThemeMode.DARK -> AppThemeMode.LIGHT
            AppThemeMode.LIGHT, AppThemeMode.SYSTEM -> AppThemeMode.DARK
        }
        _state.update { it.copy(themeMode = next) }
        viewModelScope.launch { container.settingsStore.updateThemeMode(next) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        _state.update { it.copy(themeMode = mode) }
        viewModelScope.launch { container.settingsStore.updateThemeMode(mode) }
    }

    fun import(uris: List<Uri>, onImported: (Long?) -> Unit = {}) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(importing = true) }
            val known = _state.value.books.map { it.uri }.toSet()
            val result = runCatching {
                container.libraryRepository.import(uris)
            }.getOrDefault(
                com.lightreader.core.data.BookImporter.Result(0, 0, emptyList()),
            )
            val importedBooks = container.libraryRepository.observeBooks().first()
                .filter { it.uri !in known }
            val message = when {
                result.imported > 0 -> AppStrings.get(R.string.ui_imported_books_59, result.imported)
                result.duplicates > 0 && result.failures.isEmpty() -> AppStrings.get(R.string.ui_these_books_are_already_in_your_library_60)
                result.failures.isNotEmpty() -> AppStrings.get(R.string.ui_import_failed_61, result.failures.first().message)
                else -> null
            }
            _state.update { it.copy(importing = false, message = message) }
            onImported(importedBooks.singleOrNull()?.id)
        }
    }

    fun removeFromShelf(book: Book) {
        viewModelScope.launch {
            container.libraryRepository.removeFromShelf(book)
            _state.update { it.copy(message = AppStrings.get(R.string.ui_removed_from_the_library_62, book.title)) }
        }
    }

    fun deleteLocalFile(book: Book) {
        viewModelScope.launch {
            val deleted = container.libraryRepository.deleteLocalFile(book)
            _state.update {
                it.copy(
                    message = if (deleted) {
                        AppStrings.get(R.string.ui_deleted_local_file_63, book.title)
                    } else {
                        AppStrings.get(R.string.ui_this_provider_does_not_support_direct_deletion_d_64)
                    },
                )
            }
        }
    }

    fun addBookmarkAtLastPosition(book: Book) {
        viewModelScope.launch {
            val location = container.readingRepository.loadProgress(book.id)
            container.readingRepository.addBookmark(
                bookId = book.id,
                location = location,
                title = AppStrings.get(R.string.ui_last_reading_position_65),
                snippet = "",
            )
            _state.update { it.copy(message = AppStrings.get(R.string.ui_added_a_bookmark_in_66, book.title)) }
        }
    }
}
