package com.lightreader.ui.library

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
                            text = "书架",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val shelfBooks = state.shelfBooks
                        Text(
                            text = if (shelfBooks.isEmpty()) {
                                if (state.shelf == ShelfKind.COMIC) {
                                    "还没有漫画"
                                } else {
                                    "还没有导入书籍"
                                }
                            } else {
                                val unit = if (state.shelf == ShelfKind.COMIC) "部" else "本"
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
                            contentDescription = "搜索",
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
                                Icon(LocalAppIcons.current.sort, contentDescription = "排序")
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
                                contentDescription = if (dark) "切换到浅色模式" else "切换到深色模式",
                            )
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(LocalAppIcons.current.settings, contentDescription = "设置")
                        }
                    } else {
                        Box {
                            IconButton(onClick = { overflowVisible = true }) {
                                Icon(LocalAppIcons.current.more, contentDescription = "更多")
                            }
                            DropdownMenu(
                                expanded = overflowVisible,
                                onDismissRequest = { overflowVisible = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (dark) "切换到浅色" else "切换到深色") },
                                    onClick = {
                                        overflowVisible = false
                                        viewModel.toggleThemeMode()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("排序：${sortLabel(state.sort)}") },
                                    onClick = {
                                        overflowVisible = false
                                        sortMenuVisible = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("设置") },
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
                            if (state.shelf == ShelfKind.COMIC) "漫画显示" else "书籍显示",
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
                        placeholder = { Text("搜索书名或作者") },
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
                                label = "全部",
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
                icon = { Icon(LocalAppIcons.current.add, contentDescription = "导入书籍") },
                text = { Text("导入书籍") },
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
                    title = if (state.shelf == ShelfKind.COMIC) "漫画架还是空的" else "书架还是空的",
                    message = if (state.shelf == ShelfKind.COMIC) {
                        "点右下角按钮导入 CBZ / ZIP 漫画包：根目录算一章，子文件夹按顺序算章节"
                    } else {
                        "点击右下角按钮，从本地导入 EPUB、PDF、TXT、DOCX 等文件"
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
                    title = "没有找到匹配的书籍",
                    message = "试试其他关键词或清除筛选条件",
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
                    headlineContent = { Text(if (book.progressPercent > 0f) "继续阅读" else "打开") },
                    modifier = Modifier.clickable {
                        actionsFor = null
                        onOpenBook(book.id)
                    },
                )
                val otherShelf = if (book.shelfKind == ShelfKind.COMIC) ShelfKind.BOOK else ShelfKind.COMIC
                ListItem(
                    headlineContent = { Text("移到${otherShelf.displayName}书架") },
                    supportingContent = {
                        Text(
                            text = if (otherShelf == ShelfKind.COMIC) {
                                "在漫画书架里按连续条漫阅读（PDF、图片也适用）"
                            } else {
                                "回到书籍书架，按原来的分页方式阅读"
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
                    headlineContent = { Text("添加书签（上次位置）") },
                    modifier = Modifier.clickable {
                        viewModel.addBookmarkAtLastPosition(book)
                        actionsFor = null
                    },
                )
                ListItem(
                    headlineContent = { Text("从书架移除") },
                    supportingContent = {
                        Text("不会删除设备上的原文件", style = MaterialTheme.typography.bodySmall)
                    },
                    modifier = Modifier.clickable {
                        actionsFor = null
                        removeCandidate = book
                    },
                )
                ListItem(
                    headlineContent = {
                        Text("删除本地文件", color = MaterialTheme.colorScheme.error)
                    },
                    supportingContent = {
                        Text("永久删除原文件，无法撤销", style = MaterialTheme.typography.bodySmall)
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
            title = { Text("从书架移除？") },
            text = { Text("只移除书架记录、阅读进度和书签，不会删除设备上的原文件。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeFromShelf(book)
                    removeCandidate = null
                }) { Text("移除") }
            },
            dismissButton = {
                TextButton(onClick = { removeCandidate = null }) { Text("取消") }
            },
        )
    }

    deleteCandidate?.let { book ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("删除本地文件？") },
            text = { Text("将永久删除设备上的原文件「${book.fileName}」，此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteLocalFile(book)
                    deleteCandidate = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) { Text("取消") }
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
                            title = if (state.shelf == ShelfKind.COMIC) "全部漫画" else "全部书籍",
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
                            title = if (state.shelf == ShelfKind.COMIC) "全部漫画" else "全部书籍",
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
            modifier = Modifier.semantics { contentDescription = "调节每行显示数量" },
        ) {
            Icon(LocalAppIcons.current.grid, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (columns == 0) "每行：自动" else "每行 $columns 个")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (listOf(0) + (2..6)).forEach { count ->
                DropdownMenuItem(
                    text = { Text(if (count == 0) "自动适应屏幕" else "每行 $count 个") },
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
                            contentDescription = "切换到${kind.displayName}书架"
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
                        contentDescription = if (value == LibraryLayout.GRID) "网格" else "列表",
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
                    text = "继续阅读",
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
    LibrarySort.RECENT -> "最近阅读"
    LibrarySort.ADDED -> "最近添加"
    LibrarySort.TITLE -> "按书名"
    LibrarySort.AUTHOR -> "按作者"
    LibrarySort.PROGRESS -> "按进度"
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
