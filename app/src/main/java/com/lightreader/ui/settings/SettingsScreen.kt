package com.lightreader.ui.settings

import com.lightreader.R
import com.lightreader.core.util.AppStrings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lightreader.AppContainer
import com.lightreader.BuildConfig
import com.lightreader.core.model.FontItem
import com.lightreader.core.model.LibraryLayout
import com.lightreader.core.model.LibrarySort
import com.lightreader.core.model.IndentMode
import com.lightreader.core.model.ReadingMode
import com.lightreader.core.model.ThemePreference
import com.lightreader.core.util.Formatters
import java.io.File
import com.lightreader.ui.theme.LocalAppIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory { initializer { SettingsViewModel(container) } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showFontManager by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { viewModel.export(it) } }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.restore(it) } }
    val fontPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.importFont(it) } }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    if (showLicenses) {
        LicensesScreen(onBack = { showLicenses = false })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (showFontManager) AppStrings.get(R.string.settings_font_manager) else AppStrings.get(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (showFontManager) showFontManager = false else onBack() }) {
                        Icon(LocalAppIcons.current.back, contentDescription = AppStrings.get(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (showFontManager) {
            FontManagerContent(
                fonts = state.fonts,
                defaultFontId = state.defaultFontId,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onImport = { fontPicker.launch(arrayOf("font/*", "*/*")) },
                onSetDefault = viewModel::setDefaultFont,
                onDelete = viewModel::deleteFont,
                onUseSystem = viewModel::useSystemFont,
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item(key = "setting1") {
                SectionTitle(AppStrings.get(R.string.settings_reading))
            }
            item(key = "setting2") {
                ChipGroup(
                    label = AppStrings.get(R.string.ui_app_theme_168),
                    options = listOf(
                        com.lightreader.core.model.AppThemeMode.SYSTEM to AppStrings.get(R.string.settings_theme_system),
                        com.lightreader.core.model.AppThemeMode.LIGHT to AppStrings.get(R.string.ui_light_170),
                        com.lightreader.core.model.AppThemeMode.DARK to AppStrings.get(R.string.settings_theme_dark),
                    ),
                    selected = state.appThemeMode,
                    onSelect = viewModel::setThemeMode,
                )
            }
            item(key = "setting3") {
                ChipGroup(
                    label = AppStrings.get(R.string.ui_color_palette_172),
                    options = com.lightreader.ui.theme.AppPalette.entries.map { it to it.label },
                    selected = state.appPalette,
                    onSelect = viewModel::setPalette,
                )
            }
            item(key = "setting4") {
                ChipGroup(
                    label = AppStrings.get(R.string.ui_library_background_173),
                    options = com.lightreader.ui.theme.BackgroundTone.entries.map { it to it.label },
                    selected = state.appBackground,
                    onSelect = viewModel::setBackground,
                )
            }
            item(key = "setting5") {
                ChipGroup(
                    label = AppStrings.get(R.string.ui_visual_style_174),
                    options = com.lightreader.ui.theme.ThemeStyle.entries.map { it to it.label },
                    selected = state.appStyle,
                    onSelect = viewModel::setStyle,
                )
            }
            item(key = "setting6") {
                CommitSlider(AppStrings.get(R.string.ui_default_font_size_175), state.reader.fontSizeSp, 12f..34f, { "${it.toInt()} sp" }) {
                    value -> viewModel.updateReader { it.copy(fontSizeSp = value) }
                }
            }
            item(key = "setting7") {
                CommitSlider(AppStrings.get(R.string.ui_default_line_spacing_176), state.reader.lineHeightMultiplier, 1.1f..2.6f, { "%.1f".format(it) }) {
                    value -> viewModel.updateReader { it.copy(lineHeightMultiplier = value) }
                }
            }
            item(key = "setting8") {
                CommitSlider(AppStrings.get(R.string.ui_default_margins_177), state.reader.marginDp.toFloat(), 8f..48f, { "${it.toInt()} dp" }) {
                    value -> viewModel.updateReader { it.copy(marginDp = value.toInt()) }
                }
            }
            item(key = "setting9") {
                ChipGroup(
                    label = AppStrings.get(R.string.ui_default_paper_theme_178),
                    options = listOf(
                        ThemePreference.SYSTEM to AppStrings.get(R.string.settings_theme_system),
                        ThemePreference.PAPER to AppStrings.get(R.string.ui_paper_179),
                        ThemePreference.OAT to AppStrings.get(R.string.ui_oat_180),
                        ThemePreference.SAGE to AppStrings.get(R.string.ui_sage_181),
                        ThemePreference.ROSE to AppStrings.get(R.string.ui_rose_182),
                        ThemePreference.MINT to AppStrings.get(R.string.ui_mint_183),
                        ThemePreference.AZURE to AppStrings.get(R.string.ui_lake_184),
                        ThemePreference.LILAC to AppStrings.get(R.string.ui_lilac_185),
                        ThemePreference.NIGHT to AppStrings.get(R.string.ui_night_gray_186),
                        ThemePreference.DUSK to AppStrings.get(R.string.ui_violet_night_187),
                        ThemePreference.FOREST to AppStrings.get(R.string.ui_deep_green_188),
                        ThemePreference.BLACK to AppStrings.get(R.string.settings_theme_black),
                        ThemePreference.JIARAN to AppStrings.get(R.string.ui_diana_190),
                    ),
                    selected = state.reader.theme,
                    onSelect = { theme -> viewModel.updateReader { it.copy(theme = theme) } },
                )
            }
            item(key = "setting10") {
                ChipGroup(
                    label = AppStrings.get(R.string.settings_reading_mode),
                    options = listOf(
                        ReadingMode.PAGED to AppStrings.get(R.string.reader_paged_mode),
                        ReadingMode.SCROLL to AppStrings.get(R.string.reader_reflow_mode),
                    ),
                    selected = state.reader.readingMode,
                    onSelect = { mode -> viewModel.updateReader { it.copy(readingMode = mode) } },
                )
            }
            item(key = "setting11") {
                ChipGroup(
                    label = AppStrings.get(R.string.ui_first_line_indent_89),
                    options = listOf(
                        IndentMode.AUTO to AppStrings.get(R.string.ui_auto_90),
                        IndentMode.ON to AppStrings.get(R.string.ui_two_characters_91),
                        IndentMode.OFF to AppStrings.get(R.string.ui_no_indent_92),
                    ),
                    selected = state.reader.indentMode,
                    onSelect = { mode -> viewModel.updateReader { it.copy(indentMode = mode) } },
                )
            }
            item(key = "setting12") {
                SwitchRow(
                    title = AppStrings.get(R.string.ui_use_serif_font_193),
                    subtitle = AppStrings.get(R.string.ui_use_a_serif_face_instead_of_the_default_system_f_194),
                    checked = state.reader.useSerif,
                    onChange = { value -> viewModel.updateReader { it.copy(useSerif = value) } },
                )
            }
            item(key = "setting13") {
                SwitchRow(
                    title = AppStrings.get(R.string.settings_dual_page),
                    subtitle = AppStrings.get(R.string.ui_display_two_pdf_or_image_pages_side_by_side_on_t_195),
                    checked = state.reader.dualPage,
                    onChange = { value -> viewModel.updateReader { it.copy(dualPage = value) } },
                )
            }
            item(key = "setting14") {
                ListItem(
                    headlineContent = { Text(AppStrings.get(R.string.settings_font_manager)) },
                    supportingContent = {
                        Text(
                            text = state.reader.fontDisplayName?.let { AppStrings.get(R.string.ui_current_196, it) } ?: AppStrings.get(R.string.ui_current_system_font_197),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    modifier = Modifier.clickable { showFontManager = true },
                )

            }
            item(key = "setting15") {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            item(key = "setting16") {
                SectionTitle(AppStrings.get(R.string.settings_library))
            }
            item(key = "setting17") {
                ChipGroup(
                    label = AppStrings.get(R.string.settings_default_layout),
                    options = listOf(
                        LibraryLayout.GRID to AppStrings.get(R.string.library_grid),
                        LibraryLayout.LIST to AppStrings.get(R.string.library_list),
                    ),
                    selected = state.library.layout,
                    onSelect = { layout -> viewModel.updateLibrary { it.copy(layout = layout) } },
                )
            }
            item(key = "setting18") {
                ChipGroup(
                    label = AppStrings.get(R.string.ui_default_sort_order_199),
                    options = listOf(
                        LibrarySort.RECENT to AppStrings.get(R.string.section_recent),
                        LibrarySort.ADDED to AppStrings.get(R.string.sort_added),
                        LibrarySort.TITLE to AppStrings.get(R.string.sort_title),
                    ),
                    selected = state.library.sort,
                    onSelect = { sort -> viewModel.updateLibrary { it.copy(sort = sort) } },
                )
            }
            item(key = "setting19") {
                ChipGroup(
                    label = AppStrings.get(R.string.ui_books_per_row_200),
                    options = listOf(0 to AppStrings.get(R.string.ui_auto_90)) + (2..6).map { it to AppStrings.get(R.string.ui_items_201, it) },
                    selected = state.library.gridColumns,
                    onSelect = { count -> viewModel.updateLibrary { it.copy(gridColumns = count) } },
                )
            }
            item(key = "setting20") {
                ChipGroup(
                    label = AppStrings.get(R.string.ui_comics_per_row_202),
                    options = listOf(0 to AppStrings.get(R.string.ui_auto_90)) + (2..6).map { it to AppStrings.get(R.string.ui_items_201, it) },
                    selected = state.library.comicGridColumns,
                    onSelect = { count -> viewModel.updateLibrary { it.copy(comicGridColumns = count) } },
                )
            }
            item(key = "setting21") {
                SwitchRow(
                    title = AppStrings.get(R.string.settings_show_author),
                    checked = state.library.showAuthor,
                    onChange = { value -> viewModel.updateLibrary { it.copy(showAuthor = value) } },
                )
            }
            item(key = "setting22") {
                SwitchRow(
                    title = AppStrings.get(R.string.settings_show_progress),
                    checked = state.library.showProgress,
                    onChange = { value -> viewModel.updateLibrary { it.copy(showProgress = value) } },
                )

            }
            item(key = "setting23") {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            item(key = "setting24") {
                SectionTitle(AppStrings.get(R.string.settings_data))
            }
            item(key = "setting25") {
                ListItem(
                    headlineContent = { Text(AppStrings.get(R.string.settings_clear_cache)) },
                    supportingContent = {
                        Text(
                            text = AppStrings.get(R.string.ui_using_books_are_kept_207, Formatters.fileSize(state.cacheBytes)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    modifier = Modifier.clickable { viewModel.clearCache() },
                )
            }
            item(key = "setting26") {
                ListItem(
                    headlineContent = { Text(AppStrings.get(R.string.settings_clear_history)) },
                    supportingContent = {
                        Text(AppStrings.get(R.string.settings_clear_history_summary), style = MaterialTheme.typography.bodySmall)
                    },
                    modifier = Modifier.clickable { viewModel.clearHistory() },
                )
            }
            item(key = "setting27") {
                ListItem(
                    headlineContent = { Text(AppStrings.get(R.string.settings_export)) },
                    supportingContent = {
                        Text(AppStrings.get(R.string.ui_export_progress_bookmarks_and_settings_as_json_211), style = MaterialTheme.typography.bodySmall)
                    },
                    modifier = Modifier.clickable {
                        exportLauncher.launch("lightreader-backup.json")
                    },
                )
            }
            item(key = "setting28") {
                ListItem(
                    headlineContent = { Text(AppStrings.get(R.string.settings_import)) },
                    supportingContent = {
                        Text(AppStrings.get(R.string.ui_restore_a_previously_exported_json_backup_213), style = MaterialTheme.typography.bodySmall)
                    },
                    modifier = Modifier.clickable { importLauncher.launch(arrayOf("application/json", "*/*")) },
                )

            }
            item(key = "setting29") {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            item(key = "setting30") {
                SectionTitle(AppStrings.get(R.string.settings_about))
            }
            item(key = "setting31") {
                ListItem(
                    headlineContent = { Text(AppStrings.get(R.string.settings_version)) },
                    supportingContent = {
                        Text(
                            text = "${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                )
            }
            item(key = "setting32") {
                ListItem(
                    headlineContent = { Text(AppStrings.get(R.string.ui_display_refresh_rate_216)) },
                    supportingContent = {
                        Text(
                            text = displayRefreshLabel(),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                )
            }
            item(key = "setting33") {
                ListItem(
                    headlineContent = { Text(AppStrings.get(R.string.ui_privacy_217)) },
                    supportingContent = {
                        Text(
                            text = AppStrings.get(R.string.ui_the_app_has_no_network_permission_and_does_not_u_218),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                )
            }
            item(key = "setting34") {
                ListItem(
                    headlineContent = { Text(AppStrings.get(R.string.ui_licenses_and_notices_162)) },
                    supportingContent = {
                        Text(
                            text = AppStrings.get(R.string.ui_view_free_distribution_terms_and_notices_for_lib_219),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    modifier = Modifier.clickable { showLicenses = true },
                )
            }
            item(key = "setting35") {
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun FontManagerContent(
    fonts: List<FontItem>,
    defaultFontId: Long?,
    modifier: Modifier = Modifier,
    onImport: () -> Unit,
    onSetDefault: (FontItem) -> Unit,
    onDelete: (FontItem) -> Unit,
    onUseSystem: () -> Unit,
) {
    var deleteCandidate by remember { mutableStateOf<FontItem?>(null) }

    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = AppStrings.get(R.string.ui_supports_ttf_otf_ttc_fonts_imported_fonts_are_st_220) +
                AppStrings.get(R.string.ui_and_keep_working_after_the_original_file_is_dele_221),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Icon(LocalAppIcons.current.upload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(AppStrings.get(R.string.settings_font_import))
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onUseSystem, modifier = Modifier.fillMaxWidth()) {
            Text(AppStrings.get(R.string.ui_use_system_font_223))
        }
        Spacer(Modifier.height(16.dp))
        if (fonts.isEmpty()) {
            Text(AppStrings.get(R.string.settings_font_empty), style = MaterialTheme.typography.bodyMedium)
        }
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            fonts.forEach { font ->
                FontCard(
                    font = font,
                    isDefault = font.id == defaultFontId,
                    onSetDefault = { onSetDefault(font) },
                    onDelete = { deleteCandidate = font },
                )
                Spacer(Modifier.height(10.dp))
            }
        }
    }

    deleteCandidate?.let { font ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text(AppStrings.get(R.string.ui_delete_font_225)) },
            text = { Text(AppStrings.get(R.string.ui_delete_reading_settings_using_this_font_will_swi_226, font.displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(font)
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
private fun FontCard(
    font: FontItem,
    isDefault: Boolean,
    onSetDefault: () -> Unit,
    onDelete: () -> Unit,
) {
    val family = remember(font.path) {
        runCatching { FontFamily(Font(File(font.path))) }.getOrNull()
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(font.displayName, style = MaterialTheme.typography.titleSmall)
                        if (font.isBuiltin) {
                            Surface(
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.padding(start = 8.dp),
                            ) {
                                Text(
                                    text = AppStrings.get(R.string.ui_built_in_227),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                    Text(
                        text = font.fileName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isDefault) {
                    Text(AppStrings.get(R.string.ui_default_228), style = MaterialTheme.typography.labelMedium)
                }
                IconButton(onClick = onDelete) {
                    Icon(LocalAppIcons.current.delete, contentDescription = AppStrings.get(R.string.settings_font_delete))
                }
            }
            Text(
                text = AppStrings.get(R.string.ui_aa_bb_cc_the_quick_brown_fox_0123456789_230),
                style = TextStyle(
                    fontFamily = family ?: FontFamily.Default,
                    fontSize = 18.sp,
                ),
                modifier = Modifier.padding(vertical = 8.dp),
            )
            TextButton(onClick = onSetDefault) { Text(AppStrings.get(R.string.settings_font_set_default)) }
        }
    }
}

/**
 * What the screen is doing right now, and what it could do.
 *
 * Some phones keep an app at 60 Hz unless it asks for more; the WebView still scrolls smoothly (it
 * has its own compositor) while Compose animation and scrolling runs on the app's frame clock and
 * looks like it is dropping every other frame. This line makes that visible without adb.
 */
@Composable
private fun displayRefreshLabel(): String {
    val context = androidx.compose.ui.platform.LocalContext.current
    val display = runCatching { if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        context.display
    } else {
        @Suppress("DEPRECATION")
        (context.getSystemService(android.content.Context.WINDOW_SERVICE) as? android.view.WindowManager)
            ?.defaultDisplay
    } }.getOrNull()
    val current = display?.refreshRate ?: return AppStrings.get(R.string.ui_unknown_232)
    val modes = runCatching { display.supportedModes }.getOrNull()
        .orEmpty()
        .map { it.refreshRate }
        .distinct()
        .sorted()
    val supported = if (modes.isEmpty()) "?" else modes.joinToString("/") { "%.0f".format(it) }
    return AppStrings.get(R.string.ui_current_1_0f_hz_supported_2_hz_233).format(current, supported)
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun CommitSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueLabel: (Float) -> String,
    onCommit: (Float) -> Unit,
) {
    var draft by remember(value) { mutableFloatStateOf(value) }
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(valueLabel(draft), style = MaterialTheme.typography.labelMedium)
        }
        androidx.compose.material3.Slider(
            value = draft,
            onValueChange = { draft = it },
            onValueChangeFinished = { if (draft != value) onCommit(draft) },
            valueRange = range,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    subtitle: String? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> ChipGroup(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    // Delegates to the shared option row: one line, horizontally scrollable, identical chips
    // everywhere (see ui/components/OptionRow.kt).
    com.lightreader.ui.components.OptionRow(
        label = label,
        options = options,
        selected = selected,
        onSelect = onSelect,
    )
}
