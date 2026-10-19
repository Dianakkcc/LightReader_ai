package com.lightreader.ui.reader

import com.lightreader.R
import com.lightreader.core.util.AppStrings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightreader.core.model.Bookmark
import com.lightreader.core.model.FontItem
import com.lightreader.core.model.IndentMode
import com.lightreader.core.model.PageFit
import com.lightreader.core.model.ReaderSettings
import com.lightreader.core.model.ReadingMode
import com.lightreader.core.model.SearchHit
import com.lightreader.core.model.TextAlignment
import com.lightreader.core.model.ThemePreference
import com.lightreader.core.model.TocEntry
import com.lightreader.core.util.Formatters
import com.lightreader.ui.theme.ReaderTheme
import com.lightreader.ui.theme.ThemeMapping
import com.lightreader.ui.theme.readerPalette
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.lightreader.ui.theme.LocalAppIcons

@Composable
internal fun PanelHeader(title: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onClose) {
            Icon(LocalAppIcons.current.close, contentDescription = AppStrings.get(R.string.action_close))
        }
    }
}

// --------------------------------------------------------------------------- 目录

@Composable
internal fun TocPanel(
    entries: List<TocEntry>,
    currentChapterIndex: Int,
    analysing: Boolean,
    onSelect: (TocEntry) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column {
            PanelHeader(title = AppStrings.get(R.string.reader_toc), onClose = onClose)
            if (analysing) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(AppStrings.get(R.string.ui_analyzing_chapters_70), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (entries.isEmpty()) {
                Text(
                    text = AppStrings.get(R.string.reader_no_toc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(entries.size) { index ->
                        val entry = entries[index]
                        val selected = entry.chapterIndex == currentChapterIndex
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = entry.title,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selected) {
                                        FontWeight.SemiBold
                                    } else {
                                        FontWeight.Normal
                                    },
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            },
                            modifier = Modifier
                                .padding(start = (entry.level.coerceAtMost(3) * 12).dp)
                                .clickable { onSelect(entry) },
                        )
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------------------- 搜索

@Composable
internal fun SearchPanel(
    query: String,
    results: List<SearchHit>,
    searching: Boolean,
    currentIndex: Int,
    onQueryChange: (String) -> Unit,
    onSelect: (SearchHit) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column {
            PanelHeader(title = AppStrings.get(R.string.ui_search_book_72), onClose = onClose)
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text(AppStrings.get(R.string.ui_enter_keywords_73)) },
                leadingIcon = { Icon(LocalAppIcons.current.search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
            when {
                searching -> Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(AppStrings.get(R.string.reader_searching), style = MaterialTheme.typography.bodySmall)
                }

                query.isNotBlank() && results.isEmpty() -> Text(
                    text = AppStrings.get(R.string.ui_no_matching_text_75),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )

                results.isNotEmpty() -> Text(
                    text = AppStrings.get(R.string.ui_results_76, results.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(results.size) { index ->
                    val hit = results[index]
                    ListItem(
                        headlineContent = {
                            Text(
                                text = hit.title.ifBlank { AppStrings.get(R.string.ui_section_77, hit.chapterIndex + 1) },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = hit.snippet,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                        modifier = Modifier.clickable { onSelect(hit) },
                    )
                }
            }
        }
    }
}

// --------------------------------------------------------------------------- 书签

@Composable
internal fun BookmarksPanel(
    bookmarks: List<Bookmark>,
    onSelect: (Bookmark) -> Unit,
    onDelete: (Bookmark) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column {
            PanelHeader(title = AppStrings.get(R.string.reader_bookmarks), onClose = onClose)
            if (bookmarks.isEmpty()) {
                Text(
                    text = AppStrings.get(R.string.ui_no_bookmarks_yet_use_the_bookmark_button_in_the__79),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(bookmarks.size) { index ->
                        val bookmark = bookmarks[index]
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = bookmark.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            },
                            supportingContent = {
                                Column {
                                    if (bookmark.snippet.isNotBlank()) {
                                        Text(
                                            text = bookmark.snippet,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                    bookmark.note?.takeIf { it.isNotBlank() }?.let { note ->
                                        Text(
                                            text = AppStrings.get(R.string.ui_note_80, note),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text(
                                        text = Formatters.dateTime(bookmark.createdAt),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            trailingContent = {
                                IconButton(onClick = { onDelete(bookmark) }) {
                                    Icon(LocalAppIcons.current.delete, contentDescription = AppStrings.get(R.string.ui_delete_bookmark_81))
                                }
                            },
                            modifier = Modifier.clickable { onSelect(bookmark) },
                        )
                    }
                }
            }
        }
    }
}

// --------------------------------------------------------------------------- 主题

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReaderThemePanel(
    current: ThemePreference,
    onSelect: (ThemePreference) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column {
            PanelHeader(title = AppStrings.get(R.string.ui_background_82), onClose = onClose)
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                maxItemsInEachRow = 4,
            ) {
                ThemePreference.entries.forEach { preference ->
                    ThemeCard(
                        preference = preference,
                        selected = preference == current,
                        onClick = { onSelect(preference) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/**
 * A paper sample instead of a coloured dot: the card shows the page colour, two sample text lines
 * in that theme's own ink and the accent dot. A whole row of these reads like a set of papers, and
 * the names wrap into rows instead of being squeezed into one line.
 */
@Composable
private fun ThemeCard(
    preference: ThemePreference,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = readerPalette(ThemeMapping.readerThemeOf(preference))
    val label = ThemeMapping.labelOf(preference)
    val shape = RoundedCornerShape(12.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 72.dp, height = 84.dp)
                .clip(shape)
                .background(palette.background)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outlineVariant
                    },
                    shape = shape,
                ),
            contentAlignment = Alignment.TopStart,
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(palette.text.copy(alpha = 0.72f)),
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(palette.text.copy(alpha = 0.42f)),
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(palette.accent),
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .width(72.dp)
                .padding(top = 6.dp),
        )
    }
}

// --------------------------------------------------------------------------- 阅读设置

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReaderSettingsPanel(
    settings: ReaderSettings,
    fonts: List<FontItem>,
    onUpdate: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onSelectFont: (FontItem) -> Unit,
    onImportFont: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        LazyColumn(modifier = Modifier.fillMaxSize().testTag("readerSettingsList")) {
            item(key = "header") {
                PanelHeader(title = AppStrings.get(R.string.reader_settings), onClose = onClose)
            }
            item(key = "preview") {
                FontPreview(settings = settings)
            }
            item(key = "size") {
                FontSizeRow(
                    value = settings.fontSizeSp,
                    onChange = { value -> onUpdate { it.copy(fontSizeSp = value) } },
                )
            }
            item(key = "lineHeight") {
                SettingSlider(
                    label = AppStrings.get(R.string.settings_line_spacing),
                    value = settings.lineHeightMultiplier,
                    range = 1.2f..2.6f,
                    steps = 13,
                    valueLabel = String.format("%.1f", settings.lineHeightMultiplier),
                    onChange = { value -> onUpdate { it.copy(lineHeightMultiplier = value) } },
                )
            }
            item(key = "paragraphSpacing") {
                SettingSlider(
                    label = AppStrings.get(R.string.settings_paragraph_spacing),
                    value = settings.paragraphSpacingEm,
                    range = 0f..2f,
                    steps = 19,
                    valueLabel = String.format(AppStrings.get(R.string.ui_1f_em_86), settings.paragraphSpacingEm),
                    onChange = { value -> onUpdate { it.copy(paragraphSpacingEm = value) } },
                )
            }
            item(key = "letterSpacing") {
                SettingSlider(
                    label = AppStrings.get(R.string.settings_letter_spacing),
                    value = settings.letterSpacingEm,
                    range = 0f..0.2f,
                    steps = 9,
                    valueLabel = String.format("%.2f", settings.letterSpacingEm),
                    onChange = { value -> onUpdate { it.copy(letterSpacingEm = value) } },
                )
            }
            item(key = "margin") {
                SettingSlider(
                    label = AppStrings.get(R.string.settings_margin),
                    value = settings.marginDp.toFloat(),
                    range = 8f..48f,
                    steps = 19,
                    valueLabel = "${settings.marginDp}",
                    onChange = { value -> onUpdate { it.copy(marginDp = value.toInt()) } },
                )
            }
            item(key = "indent") {
                ChipRow(
                    label = AppStrings.get(R.string.ui_first_line_indent_89),
                    options = listOf(
                        IndentMode.AUTO to AppStrings.get(R.string.ui_auto_90),
                        IndentMode.ON to AppStrings.get(R.string.ui_two_characters_91),
                        IndentMode.OFF to AppStrings.get(R.string.ui_no_indent_92),
                    ),
                    selected = settings.indentMode,
                    onSelect = { value -> onUpdate { it.copy(indentMode = value) } },
                )
            }
            item(key = "alignment") {
                ChipRow(
                    label = AppStrings.get(R.string.settings_alignment),
                    options = listOf(
                        TextAlignment.JUSTIFY to AppStrings.get(R.string.settings_align_justify),
                        TextAlignment.START to AppStrings.get(R.string.settings_align_left),
                    ),
                    selected = settings.alignment,
                    onSelect = { value -> onUpdate { it.copy(alignment = value) } },
                )
            }
            item(key = "readingMode") {
                ChipRow(
                    label = AppStrings.get(R.string.settings_reading_mode),
                    options = listOf(
                        ReadingMode.PAGED to AppStrings.get(R.string.ui_paged_97),
                        ReadingMode.SCROLL to AppStrings.get(R.string.ui_continuous_scroll_98),
                    ),
                    selected = settings.readingMode,
                    onSelect = { value -> onUpdate { it.copy(readingMode = value) } },
                )
            }
            item(key = "pageFit") {
                ChipRow(
                    label = AppStrings.get(R.string.ui_image_and_pdf_scaling_99),
                    options = listOf(
                        PageFit.WIDTH to AppStrings.get(R.string.settings_fit_width),
                        PageFit.PAGE to AppStrings.get(R.string.settings_fit_page),
                        PageFit.ACTUAL to AppStrings.get(R.string.settings_actual_size),
                    ),
                    selected = settings.pageFit,
                    onSelect = { value -> onUpdate { it.copy(pageFit = value) } },
                )
            }
            item(key = "dualPage") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(AppStrings.get(R.string.settings_dual_page))
                        Text(
                            text = AppStrings.get(R.string.ui_on_tablets_or_in_landscape_pdf_images_use_facing_104),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.dualPage,
                        onCheckedChange = { value -> onUpdate { it.copy(dualPage = value) } },
                    )
                }
            }
            item(key = "fontsHeader") {
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                Text(
                    text = AppStrings.get(R.string.settings_font),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            item(key = "fonts") {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    // Same chip style and same "one scrollable line" rule as every other option row.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 6.dp),
                    ) {
                        com.lightreader.ui.components.AppChip(
                            label = AppStrings.get(R.string.ui_system_font_106),
                            selected = settings.fontId == null && !settings.useSerif,
                            onClick = {
                                onUpdate {
                                    it.copy(
                                        fontId = null,
                                        fontFamily = null,
                                        fontPath = null,
                                        fontDisplayName = null,
                                        useSerif = false,
                                    )
                                }
                            },
                        )
                        com.lightreader.ui.components.AppChip(
                            label = AppStrings.get(R.string.ui_serif_107),
                            selected = settings.fontId == null && settings.useSerif,
                            onClick = {
                                onUpdate {
                                    it.copy(
                                        fontId = null,
                                        fontFamily = null,
                                        fontPath = null,
                                        fontDisplayName = null,
                                        useSerif = true,
                                    )
                                }
                            },
                        )
                        fonts.forEach { font ->
                            com.lightreader.ui.components.AppChip(
                                label = font.displayName,
                                selected = settings.fontId == font.id,
                                onClick = { onSelectFont(font) },
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = onImportFont,
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        Icon(LocalAppIcons.current.upload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(AppStrings.get(R.string.ui_import_font_ttf_otf_ttc_108))
                    }
                }
            }
            item(key = "bottomSpace") {
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun FontPreview(settings: ReaderSettings) {
    val fallback: FontFamily = if (settings.useSerif) FontFamily.Serif else FontFamily.SansSerif
    val family by produceState(fallback, settings.fontPath, settings.useSerif) {
        value = fallback
        settings.fontPath?.let { path ->
            value = withContext(Dispatchers.IO) {
                runCatching { FontFamily(android.graphics.Typeface.createFromFile(path)) }
                    .getOrDefault(fallback)
            }
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = AppStrings.get(R.string.ui_the_spring_day_lingers_trees_and_flowers_flouris_109),
                style = TextStyle(
                    fontFamily = family ?: FontFamily.Default,
                    fontSize = settings.fontSizeSp.sp,
                    lineHeight = (settings.fontSizeSp * settings.lineHeightMultiplier).sp,
                    textAlign = TextAlign.Start,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            Text(
                text = AppStrings.get(R.string.ui_aa_bb_0123_font_size_110, settings.fontSizeSp.toInt()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun FontSizeRow(value: Float, onChange: (Float) -> Unit) {
    var draft by remember(value) { mutableFloatStateOf(value) }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(AppStrings.get(R.string.settings_font_size), modifier = Modifier.weight(1f))
            Text("${draft.toInt()}", style = MaterialTheme.typography.labelMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange((value - 1f).coerceAtLeast(12f)) }) {
                Icon(LocalAppIcons.current.remove, contentDescription = AppStrings.get(R.string.ui_decrease_font_size_112))
            }
            Slider(
                value = draft.coerceIn(12f, 34f),
                onValueChange = { draft = it },
                onValueChangeFinished = { if (draft != value) onChange(draft) },
                valueRange = 12f..34f,
                steps = 21,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onChange((value + 1f).coerceAtMost(34f)) }) {
                Icon(LocalAppIcons.current.add, contentDescription = AppStrings.get(R.string.ui_increase_font_size_113))
            }
        }
    }
}

@Composable
private fun SettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: String,
    onChange: (Float) -> Unit,
) {
    var draft by remember(value) { mutableFloatStateOf(value) }
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f))
            Text(
                if (draft == value) valueLabel else when (label) {
                    AppStrings.get(R.string.settings_line_spacing) -> "%.1f".format(draft)
                    AppStrings.get(R.string.settings_paragraph_spacing) -> AppStrings.get(R.string.ui_1f_em_86).format(draft)
                    AppStrings.get(R.string.settings_letter_spacing) -> "%.2f".format(draft)
                    else -> draft.toInt().toString()
                },
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Slider(
            value = draft.coerceIn(range.start, range.endInclusive),
            onValueChange = { draft = it },
            onValueChangeFinished = { if (draft != value) onChange(draft) },
            valueRange = range,
            steps = steps,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    // Shared option row: one horizontally scrollable line with the app wide chip look.
    com.lightreader.ui.components.OptionRow(
        label = label,
        options = options,
        selected = selected,
        onSelect = onSelect,
        horizontalPadding = 20,
    )
}
