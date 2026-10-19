package com.lightreader.ui.library

import com.lightreader.R
import com.lightreader.core.util.AppStrings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lightreader.AppContainer
import com.lightreader.core.model.Book
import com.lightreader.core.model.AppThemeMode
import com.lightreader.core.model.LibraryLayout
import com.lightreader.core.model.LibrarySort
import com.lightreader.core.model.ShelfKind
import com.lightreader.core.util.Formatters
import com.lightreader.core.util.ImageLoader
import com.lightreader.ui.components.BookCover
import com.lightreader.ui.components.EmptyState
import com.lightreader.ui.theme.LocalAppIcons

/** Widest the shelf ever gets: keeps a tablet from stretching covers to silly sizes. */
private val ShelfMaxWidth = 1040.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    container: AppContainer,
    onOpenBook: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: LibraryViewModel = viewModel(
        factory = viewModelFactory { initializer { LibraryViewModel(container) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var searchVisible by remember { mutableStateOf(false) }
    var sortMenuVisible by remember { mutableStateOf(false) }
    var overflowVisible by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<Book?>(null) }
    var removeCandidate by remember { mutableStateOf<Book?>(null) }
    var deleteCandidate by remember { mutableStateOf<Book?>(null) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        viewModel.import(uris) { bookId ->
            if (bookId != null) onOpenBook(bookId)
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Without this the whole header sat *under* the status bar: the title was
                        // squeezed against the clock and the buttons in that strip never received a
                        // touch (the status bar swallows them).
                        .statusBarsPadding()
                        .padding(start = 22.dp, end = 12.dp, top = 18.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (state.palette == com.lightreader.ui.theme.AppPalette.JIARAN) {
                        JiaranMark()
                        Spacer(Modifier.width(6.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = AppStrings.get(R.string.settings_library),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val shelfBooks = state.shelfBooks
                        Text(
                            text = if (shelfBooks.isEmpty()) {
                                if (state.shelf == ShelfKind.COMIC) {
                                    AppStrings.get(R.string.ui_no_comics_yet_2)
                                } else {
                                    AppStrings.get(R.string.ui_no_books_imported_yet_3)
                                }
                            } else {
                                val unit = if (state.shelf == ShelfKind.COMIC) AppStrings.get(R.string.ui_comics_4) else AppStrings.get(R.string.ui_books_5)
                                "${shelfBooks.size} $unit · " +
                                    Formatters.relativeTime(
                                        shelfBooks.maxOfOrNull { it.lastOpenedAt ?: it.addedAt },
                                    )
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = {
                        searchVisible = !searchVisible
                        if (!searchVisible) viewModel.onQueryChange("")
                    }) {
                        Icon(
                            if (searchVisible) LocalAppIcons.current.close else LocalAppIcons.current.search,
                            contentDescription = AppStrings.get(R.string.reader_search),
                        )
                    }
                    val dark = when (state.themeMode) {
                        AppThemeMode.DARK -> true
                        AppThemeMode.LIGHT -> false
                        AppThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                    }
                    // Narrow phones only get search plus an overflow menu: four visible actions
                    // squeezed the book count and the title into unreadable slivers.
                    if (LocalConfiguration.current.screenWidthDp >= 600) {
                        Box {
                            IconButton(onClick = { sortMenuVisible = true }) {
                                Icon(LocalAppIcons.current.sort, contentDescription = AppStrings.get(R.string.library_sort))
                            }
                            SortMenu(
                                expanded = sortMenuVisible,
                                onDismiss = { sortMenuVisible = false },
                                onSelect = { sort ->
                                    viewModel.setSort(sort)
                                    sortMenuVisible = false
                                },
                            )
                        }
                        IconButton(onClick = viewModel::toggleThemeMode) {
                            Icon(
                                imageVector = if (dark) {
                                    LocalAppIcons.current.light
                                } else {
                                    LocalAppIcons.current.dark
                                },
                                contentDescription = if (dark) AppStrings.get(R.string.ui_switch_to_light_mode_8) else AppStrings.get(R.string.ui_switch_to_dark_mode_9),
                            )
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(LocalAppIcons.current.settings, contentDescription = AppStrings.get(R.string.settings_title))
                        }
                    } else {
                        Box {
                            IconButton(onClick = { overflowVisible = true }) {
                                Icon(LocalAppIcons.current.more, contentDescription = AppStrings.get(R.string.action_more))
                            }
                            DropdownMenu(
                                expanded = overflowVisible,
                                onDismissRequest = { overflowVisible = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (dark) AppStrings.get(R.string.ui_switch_to_light_mode_12) else AppStrings.get(R.string.ui_switch_to_dark_mode_13)) },
                                    onClick = {
                                        overflowVisible = false
                                        viewModel.toggleThemeMode()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(AppStrings.get(R.string.ui_sort_14, sortLabel(state.sort))) },
                                    onClick = {
                                        overflowVisible = false
                                        sortMenuVisible = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(AppStrings.get(R.string.settings_title)) },
                                    onClick = {
                                        overflowVisible = false
                                        onOpenSettings()
                                    },
                                )
                            }
                            SortMenu(
                                expanded = sortMenuVisible,
                                onDismiss = { sortMenuVisible = false },
                                onSelect = { sort ->
                                    viewModel.setSort(sort)
                                    sortMenuVisible = false
                                },
                            )
                        }
                    }
                }
                // 书籍 / 漫画: two shelves, one switch at the very top. Text formats land on the
                // book shelf, comic archives on the comic shelf, and anything ambiguous (PDF,
                // images, a picture heavy EPUB) can be moved by hand from the long press menu.
                ShelfKindSwitch(
                    shelf = state.shelf,
                    countOf = state::countOn,
                    onChange = viewModel::setShelf,
                    modifier = Modifier.padding(start = 20.dp, top = 2.dp, bottom = 6.dp),
                )
                if (state.layout == LibraryLayout.GRID) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (state.shelf == ShelfKind.COMIC) AppStrings.get(R.string.ui_comic_display_15) else AppStrings.get(R.string.ui_book_display_16),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        ColumnCountMenu(state.selectedColumns, viewModel::setColumns)
                    }
                }
                if (searchVisible) {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 2.dp),
                        placeholder = { Text(AppStrings.get(R.string.library_search_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                    )
                }
                val formats = remember(state.shelfBooks) {
                    state.shelfBooks.map { it.format }.distinct().sortedBy { it.displayName }
                }
                if (formats.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            com.lightreader.ui.components.AppChip(
                                label = AppStrings.get(R.string.ui_all_18),
                                selected = state.formatFilter == null,
                                onClick = { viewModel.setFormatFilter(null) },
                            )
                        }
                        items(formats.size) { index ->
                            val format = formats[index]
                            com.lightreader.ui.components.AppChip(
                                label = format.displayName,
                                selected = state.formatFilter == format,
                                onClick = { viewModel.setFormatFilter(format) },
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { picker.launch(importMimeTypes) },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                // The extended FAB reads as a bare "Button" to accessibility services otherwise:
                // its label is not exposed in the merged semantics, so the icon carries the name.
                icon = { Icon(LocalAppIcons.current.add, contentDescription = AppStrings.get(R.string.library_import)) },
                text = { Text(AppStrings.get(R.string.library_import)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (state.palette == com.lightreader.ui.theme.AppPalette.JIARAN) {
                // Diana / 嘉然 limited theme: the character sits behind the whole shelf at low
                // opacity instead of being a small mark in the corner.
                JiaranBackdrop()
            }
            when {
                state.loading -> Unit
                state.shelfBooks.isEmpty() -> EmptyState(
                    title = if (state.shelf == ShelfKind.COMIC) AppStrings.get(R.string.ui_your_comic_shelf_is_empty_20) else AppStrings.get(R.string.library_empty_title),
                    message = if (state.shelf == ShelfKind.COMIC) {
                        AppStrings.get(R.string.ui_use_the_bottom_right_button_to_import_cbz_zip_co_22)
                    } else {
                        AppStrings.get(R.string.ui_use_the_bottom_right_button_to_import_local_epub_23)
                    },
                    modifier = Modifier.fillMaxSize(),
                    icon = {
                        Icon(
                            imageVector = LocalAppIcons.current.add,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.outline,
                        )
                    },
                )

                state.filtered.isEmpty() -> EmptyState(
                    title = AppStrings.get(R.string.library_no_match),
                    message = AppStrings.get(R.string.ui_try_other_keywords_or_clear_the_filters_25),
                    modifier = Modifier.fillMaxSize(),
                    icon = {
                        Icon(
                            imageVector = LocalAppIcons.current.search,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.outline,
                        )
                    },
                )

                else -> LibraryContent(
                    state = state,
                    imageLoader = container.imageLoader,
                    onOpenBook = onOpenBook,
                    onLayoutChange = viewModel::setLayout,
                    onLongPress = { book -> actionsFor = book },
                )
            }
        }
    }

    actionsFor?.let { book ->
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { actionsFor = null }, sheetState = sheetState) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                ListItem(
                    headlineContent = {
                        Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
                    supportingContent = {
                        Text(
                            "${book.format.displayName} · ${Formatters.fileSize(book.fileSize)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                )
                ListItem(
                    headlineContent = { Text(if (book.progressPercent > 0f) AppStrings.get(R.string.menu_continue) else AppStrings.get(R.string.menu_open)) },
                    modifier = Modifier.clickable {
                        actionsFor = null
                        onOpenBook(book.id)
                    },
                )
                val otherShelf = if (book.shelfKind == ShelfKind.COMIC) ShelfKind.BOOK else ShelfKind.COMIC
                ListItem(
                    headlineContent = { Text(AppStrings.get(R.string.ui_move_to_28, otherShelf.displayName)) },
                    supportingContent = {
                        Text(
                            text = if (otherShelf == ShelfKind.COMIC) {
                                AppStrings.get(R.string.ui_read_as_a_continuous_comic_including_pdf_and_ima_29)
                            } else {
                                AppStrings.get(R.string.ui_return_to_the_book_shelf_and_the_original_paging_30)
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    modifier = Modifier.clickable {
                        actionsFor = null
                        viewModel.setBookShelfKind(book, otherShelf)
                    },
                )
                ListItem(
                    headlineContent = { Text(AppStrings.get(R.string.ui_bookmark_last_position_31)) },
                    modifier = Modifier.clickable {
                        viewModel.addBookmarkAtLastPosition(book)
                        actionsFor = null
                    },
                )
                ListItem(
                    headlineContent = { Text(AppStrings.get(R.string.menu_remove_shelf)) },
                    supportingContent = {
                        Text(AppStrings.get(R.string.ui_keeps_the_original_file_on_your_device_33), style = MaterialTheme.typography.bodySmall)
                    },
                    modifier = Modifier.clickable {
                        actionsFor = null
                        removeCandidate = book
                    },
                )
                ListItem(
                    headlineContent = {
                        Text(AppStrings.get(R.string.menu_delete_file), color = MaterialTheme.colorScheme.error)
                    },
                    supportingContent = {
                        Text(AppStrings.get(R.string.ui_permanently_deletes_the_original_file_this_canno_35), style = MaterialTheme.typography.bodySmall)
                    },
                    modifier = Modifier.clickable {
                        actionsFor = null
                        deleteCandidate = book
                    },
                )
            }
        }
    }

    removeCandidate?.let { book ->
        AlertDialog(
            onDismissRequest = { removeCandidate = null },
            title = { Text(AppStrings.get(R.string.remove_shelf_confirm_title)) },
            text = { Text(AppStrings.get(R.string.ui_removes_the_library_entry_progress_and_bookmarks_37)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeFromShelf(book)
                    removeCandidate = null
                }) { Text(AppStrings.get(R.string.ui_remove_38)) }
            },
            dismissButton = {
                TextButton(onClick = { removeCandidate = null }) { Text(AppStrings.get(R.string.action_cancel)) }
            },
        )
    }

    deleteCandidate?.let { book ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(AppStrings.get(R.string.delete_file_confirm_title)) },
            text = { Text(AppStrings.get(R.string.ui_permanently_delete_from_your_device_this_cannot__41, book.fileName)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteLocalFile(book)
                    deleteCandidate = null
                }) { Text(AppStrings.get(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text(AppStrings.get(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun LibraryContent(
    state: LibraryViewModel.UiState,
    imageLoader: ImageLoader,
    onOpenBook: (Long) -> Unit,
    onLayoutChange: (LibraryLayout) -> Unit,
    onLongPress: (Book) -> Unit,
) {
    val books = remember(state.books, state.shelf, state.query, state.formatFilter, state.sort) {
        state.filtered
    }
    val continuing = remember(state.books, state.shelf) { state.recent.firstOrNull() }

    Surface(
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = ShelfMaxWidth),
    ) {
        BoxWithConstraints {
            if (state.layout == LibraryLayout.GRID) {
                // Columns are computed instead of left to GridCells.Adaptive: on a 360dp phone the
                // adaptive rule collapsed to a single column, which is what made the shelf look odd.
                val widthDp = maxWidth.value.toInt()
                val usable = (widthDp - 40).coerceAtLeast(240)
                val columns = if (state.selectedColumns == 0) {
                    ((usable + 18) / (136 + 18)).coerceIn(2, 6)
                } else state.selectedColumns.coerceIn(2, 6)
                val compact = columns >= 4
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    contentPadding = PaddingValues(
                        start = if (compact) 12.dp else 20.dp,
                        end = if (compact) 12.dp else 20.dp,
                        top = 8.dp,
                        bottom = 104.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(if (columns >= 3) 8.dp else 18.dp),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 20.dp),
                ) {
                    if (continuing != null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ContinueReadingCard(
                                book = continuing,
                                imageLoader = imageLoader,
                                onOpen = { onOpenBook(continuing.id) },
                            )
                        }
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionRow(
                            title = if (state.shelf == ShelfKind.COMIC) AppStrings.get(R.string.ui_all_comics_43) else AppStrings.get(R.string.section_all),
                            layout = state.layout,
                            onLayoutChange = onLayoutChange,
                        )
                    }
                    items(books, key = { it.id }) { book ->
                        BookGridItem(
                            book = book,
                            showAuthor = state.showAuthor,
                            showProgress = state.showProgress,
                            imageLoader = imageLoader,
                            onClick = { onOpenBook(book.id) },
                            onLongClick = { onLongPress(book) },
                            compact = compact,
                        )
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 104.dp)) {
                    if (continuing != null) {
                        item {
                            ContinueReadingCard(
                                book = continuing,
                                imageLoader = imageLoader,
                                onOpen = { onOpenBook(continuing.id) },
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            )
                        }
                    }
                    item {
                        SectionRow(
                            title = if (state.shelf == ShelfKind.COMIC) AppStrings.get(R.string.ui_all_comics_43) else AppStrings.get(R.string.section_all),
                            layout = state.layout,
                            onLayoutChange = onLayoutChange,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                    items(books.size, key = { books[it].id }) { index ->
                        val book = books[index]
                        BookListItem(
                            book = book,
                            showAuthor = state.showAuthor,
                            showProgress = state.showProgress,
                            imageLoader = imageLoader,
                            onClick = { onOpenBook(book.id) },
                            onLongClick = { onLongPress(book) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnCountMenu(columns: Int, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.semantics { contentDescription = AppStrings.get(R.string.ui_change_items_per_row_45) },
        ) {
            Icon(LocalAppIcons.current.grid, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (columns == 0) AppStrings.get(R.string.ui_items_per_row_auto_46) else AppStrings.get(R.string.ui_per_row_47, columns))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (listOf(0) + (2..6)).forEach { count ->
                DropdownMenuItem(
                    text = { Text(if (count == 0) AppStrings.get(R.string.ui_fit_to_screen_automatically_48) else AppStrings.get(R.string.ui_per_row_47, count)) },
                    modifier = Modifier.semantics { selected = columns == count },
                    onClick = { onSelect(count); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun SectionRow(
    title: String,
    layout: LibraryLayout,
    onLayoutChange: (LibraryLayout) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        LayoutSwitch(layout = layout, onChange = onLayoutChange)
    }
}

/**
 * The 书籍 / 漫画 switch at the top of the shelf.
 *
 * The library is really two shelves, and this is the control that decides which one is on screen.
 * The counts ride along so an empty shelf is obvious before switching to it.
 */
@Composable
private fun ShelfKindSwitch(
    shelf: ShelfKind,
    countOf: (ShelfKind) -> Int,
    onChange: (ShelfKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = modifier,
    ) {
        Row(modifier = Modifier.padding(3.dp)) {
            ShelfKind.entries.forEach { kind ->
                val selected = kind == shelf
                val count = countOf(kind)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.surface
                            } else {
                                androidx.compose.ui.graphics.Color.Transparent
                            },
                        )
                        .clickable { if (!selected) onChange(kind) }
                        .semantics {
                            contentDescription = AppStrings.get(R.string.ui_switch_to_50, kind.displayName)
                            this.selected = selected
                        }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = kind.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    if (count > 0) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LayoutSwitch(layout: LibraryLayout, onChange: (LibraryLayout) -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(modifier = Modifier.padding(2.dp)) {
            listOf(
                LibraryLayout.GRID to LocalAppIcons.current.grid,
                LibraryLayout.LIST to LocalAppIcons.current.list,
            ).forEach { (value, icon) ->
                val selected = value == layout
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.surface
                            } else {
                                androidx.compose.ui.graphics.Color.Transparent
                            },
                        )
                        .clickable { onChange(value) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = if (value == LibraryLayout.GRID) AppStrings.get(R.string.library_grid) else AppStrings.get(R.string.library_list),
                        modifier = Modifier.size(16.dp),
                        tint = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

/**
 * Diana / 嘉然 limited theme: the character mark beside the shelf title. The artwork ships inside
 * the APK (`assets/brand`) and is only decoded when that theme is actually selected.
 */
@Composable
private fun JiaranMark() {
    val context = LocalContext.current
    val bitmap = remember {
        runCatching {
            context.assets.open("brand/jiaran.png").use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()
    }
    if (bitmap == null) return
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = null,
        modifier = Modifier.height(58.dp),
    )
}

/**
 * The full shelf backdrop of the 嘉然 limited theme: the same artwork, low opacity, anchored to the
 * bottom-right so it never fights with the book titles. It does not take touches.
 */
@Composable
private fun JiaranBackdrop() {
    val context = LocalContext.current
    val bitmap = remember {
        runCatching {
            context.assets.open("brand/jiaran.png").use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()
    }
    if (bitmap == null) return
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            alpha = 0.12f,
            modifier = Modifier
                .fillMaxHeight(0.82f)
                .widthIn(max = 460.dp),
        )
    }
}

@Composable
private fun ContinueReadingCard(

    book: Book,
    imageLoader: ImageLoader,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(
                book = book,
                imageLoader = imageLoader,
                modifier = Modifier
                    .width(58.dp)
                    .height(84.dp),
                cornerRadius = 10,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = AppStrings.get(R.string.menu_continue),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = buildString {
                        book.author?.takeIf { it.isNotBlank() }?.let { append(it).append(" · ") }
                        append(Formatters.percent(book.progressPercent))
                        book.lastOpenedAt?.let { append(" · ").append(Formatters.relativeTime(it)) }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(book.progressPercent.coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

private val importMimeTypes = arrayOf(
    "application/epub+zip",
    "application/pdf",
    "text/plain",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/rtf",
    "text/html",
    "text/markdown",
    "image/*",
    "*/*",
)

private fun sortLabel(sort: LibrarySort): String = when (sort) {
    LibrarySort.RECENT -> AppStrings.get(R.string.section_recent)
    LibrarySort.ADDED -> AppStrings.get(R.string.sort_added)
    LibrarySort.TITLE -> AppStrings.get(R.string.sort_title)
    LibrarySort.AUTHOR -> AppStrings.get(R.string.sort_author)
    LibrarySort.PROGRESS -> AppStrings.get(R.string.sort_progress)
}

@Composable
private fun SortMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSelect: (LibrarySort) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        LibrarySort.entries.forEach { sort ->
            DropdownMenuItem(
                text = { Text(sortLabel(sort)) },
                onClick = { onSelect(sort) },
            )
        }
    }
}
