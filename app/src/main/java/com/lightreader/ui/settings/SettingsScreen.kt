package com.lightreader.ui.settings

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
                title = { Text(if (showFontManager) "字体管理" else "设置") },
                navigationIcon = {
                    IconButton(onClick = { if (showFontManager) showFontManager = false else onBack() }) {
                        Icon(LocalAppIcons.current.back, contentDescription = "返回")
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
                SectionTitle("阅读")
            }
            item(key = "setting2") {
                ChipGroup(
                    label = "应用主题",
                    options = listOf(
                        com.lightreader.core.model.AppThemeMode.SYSTEM to "跟随系统",
                        com.lightreader.core.model.AppThemeMode.LIGHT to "浅色",
                        com.lightreader.core.model.AppThemeMode.DARK to "深色",
                    ),
                    selected = state.appThemeMode,
                    onSelect = viewModel::setThemeMode,
                )
            }
            item(key = "setting3") {
                ChipGroup(
                    label = "配色",
                    options = com.lightreader.ui.theme.AppPalette.entries.map { it to it.label },
                    selected = state.appPalette,
                    onSelect = viewModel::setPalette,
                )
            }
            item(key = "setting4") {
                ChipGroup(
                    label = "书架底色",
                    options = com.lightreader.ui.theme.BackgroundTone.entries.map { it to it.label },
                    selected = state.appBackground,
                    onSelect = viewModel::setBackground,
                )
            }
            item(key = "setting5") {
                ChipGroup(
                    label = "整体风格",
                    options = com.lightreader.ui.theme.ThemeStyle.entries.map { it to it.label },
                    selected = state.appStyle,
                    onSelect = viewModel::setStyle,
                )
            }
            item(key = "setting6") {
                CommitSlider("默认字号", state.reader.fontSizeSp, 12f..34f, { "${it.toInt()} sp" }) {
                    value -> viewModel.updateReader { it.copy(fontSizeSp = value) }
                }
            }
            item(key = "setting7") {
                CommitSlider("默认行距", state.reader.lineHeightMultiplier, 1.1f..2.6f, { "%.1f".format(it) }) {
                    value -> viewModel.updateReader { it.copy(lineHeightMultiplier = value) }
                }
            }
            item(key = "setting8") {
                CommitSlider("默认页边距", state.reader.marginDp.toFloat(), 8f..48f, { "${it.toInt()} dp" }) {
                    value -> viewModel.updateReader { it.copy(marginDp = value.toInt()) }
                }
            }
            item(key = "setting9") {
                ChipGroup(
                    label = "默认主题",
                    options = listOf(
                        ThemePreference.SYSTEM to "跟随系统",
                        ThemePreference.PAPER to "纸白",
                        ThemePreference.OAT to "燕麦",
                        ThemePreference.SAGE to "灰绿",
                        ThemePreference.ROSE to "樱粉",
                        ThemePreference.MINT to "薄荷",
                        ThemePreference.AZURE to "湖蓝",
                        ThemePreference.LILAC to "香芋",
                        ThemePreference.NIGHT to "夜灰",
                        ThemePreference.DUSK to "紫夜",
                        ThemePreference.FOREST to "墨绿",
                        ThemePreference.BLACK to "黑色",
                        ThemePreference.JIARAN to "嘉然",
                    ),
                    selected = state.reader.theme,
                    onSelect = { theme -> viewModel.updateReader { it.copy(theme = theme) } },
                )
            }
            item(key = "setting10") {
                ChipGroup(
                    label = "翻页方式",
                    options = listOf(
                        ReadingMode.PAGED to "翻页",
                        ReadingMode.SCROLL to "滚动",
                    ),
                    selected = state.reader.readingMode,
                    onSelect = { mode -> viewModel.updateReader { it.copy(readingMode = mode) } },
                )
            }
            item(key = "setting11") {
                ChipGroup(
                    label = "首行缩进",
                    options = listOf(
                        IndentMode.AUTO to "自动",
                        IndentMode.ON to "缩进两字",
                        IndentMode.OFF to "不缩进",
                    ),
                    selected = state.reader.indentMode,
                    onSelect = { mode -> viewModel.updateReader { it.copy(indentMode = mode) } },
                )
            }
            item(key = "setting12") {
                SwitchRow(
                    title = "使用宋体（衬线字体）",
                    subtitle = "正文默认使用系统中文字体，开启后改用衬线字体",
                    checked = state.reader.useSerif,
                    onChange = { value -> viewModel.updateReader { it.copy(useSerif = value) } },
                )
            }
            item(key = "setting13") {
                SwitchRow(
                    title = "双页模式",
                    subtitle = "平板或横屏时并排显示两页（PDF 与图片）",
                    checked = state.reader.dualPage,
                    onChange = { value -> viewModel.updateReader { it.copy(dualPage = value) } },
                )
            }
            item(key = "setting14") {
                ListItem(
                    headlineContent = { Text("字体管理") },
                    supportingContent = {
                        Text(
                            text = state.reader.fontDisplayName?.let { "当前：$it" } ?: "当前：系统默认字体",
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
                SectionTitle("书架")
            }
            item(key = "setting17") {
                ChipGroup(
                    label = "默认布局",
                    options = listOf(
                        LibraryLayout.GRID to "网格",
                        LibraryLayout.LIST to "列表",
                    ),
                    selected = state.library.layout,
                    onSelect = { layout -> viewModel.updateLibrary { it.copy(layout = layout) } },
                )
            }
            item(key = "setting18") {
                ChipGroup(
                    label = "默认排序",
                    options = listOf(
                        LibrarySort.RECENT to "最近阅读",
                        LibrarySort.ADDED to "最近添加",
                        LibrarySort.TITLE to "按书名",
                    ),
                    selected = state.library.sort,
                    onSelect = { sort -> viewModel.updateLibrary { it.copy(sort = sort) } },
                )
            }
            item(key = "setting19") {
                ChipGroup(
                    label = "书籍每行数量",
                    options = listOf(0 to "自动") + (2..6).map { it to "$it 个" },
                    selected = state.library.gridColumns,
                    onSelect = { count -> viewModel.updateLibrary { it.copy(gridColumns = count) } },
                )
            }
            item(key = "setting20") {
                ChipGroup(
                    label = "漫画每行数量",
                    options = listOf(0 to "自动") + (2..6).map { it to "$it 个" },
                    selected = state.library.comicGridColumns,
                    onSelect = { count -> viewModel.updateLibrary { it.copy(comicGridColumns = count) } },
                )
            }
            item(key = "setting21") {
                SwitchRow(
                    title = "显示作者",
                    checked = state.library.showAuthor,
                    onChange = { value -> viewModel.updateLibrary { it.copy(showAuthor = value) } },
                )
            }
            item(key = "setting22") {
                SwitchRow(
                    title = "显示阅读进度",
                    checked = state.library.showProgress,
                    onChange = { value -> viewModel.updateLibrary { it.copy(showProgress = value) } },
                )

            }
            item(key = "setting23") {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            item(key = "setting24") {
                SectionTitle("数据")
            }
            item(key = "setting25") {
                ListItem(
                    headlineContent = { Text("清理缓存") },
                    supportingContent = {
                        Text(
                            text = "当前占用 ${Formatters.fileSize(state.cacheBytes)}，不会删除书籍",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    modifier = Modifier.clickable { viewModel.clearCache() },
                )
            }
            item(key = "setting26") {
                ListItem(
                    headlineContent = { Text("清理阅读历史") },
                    supportingContent = {
                        Text("清除阅读进度与最近阅读记录", style = MaterialTheme.typography.bodySmall)
                    },
                    modifier = Modifier.clickable { viewModel.clearHistory() },
                )
            }
            item(key = "setting27") {
                ListItem(
                    headlineContent = { Text("导出阅读数据") },
                    supportingContent = {
                        Text("导出进度、书签与设置为 JSON 文件", style = MaterialTheme.typography.bodySmall)
                    },
                    modifier = Modifier.clickable {
                        exportLauncher.launch("lightreader-backup.json")
                    },
                )
            }
            item(key = "setting28") {
                ListItem(
                    headlineContent = { Text("导入阅读数据") },
                    supportingContent = {
                        Text("从之前导出的 JSON 文件恢复", style = MaterialTheme.typography.bodySmall)
                    },
                    modifier = Modifier.clickable { importLauncher.launch(arrayOf("application/json", "*/*")) },
                )

            }
            item(key = "setting29") {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
            item(key = "setting30") {
                SectionTitle("关于")
            }
            item(key = "setting31") {
                ListItem(
                    headlineContent = { Text("版本") },
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
                    headlineContent = { Text("屏幕刷新率") },
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
                    headlineContent = { Text("隐私") },
                    supportingContent = {
                        Text(
                            text = "应用未申请网络权限，不主动上传数据。系统备份、换机迁移及所选文件提供程序按各自设置运行。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                )
            }
            item(key = "setting34") {
                ListItem(
                    headlineContent = { Text("许可与声明") },
                    supportingContent = {
                        Text(
                            text = "查看免费分发条款、第三方组件许可、字体和角色素材的权利声明。",
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
            text = "支持 .ttf / .otf / .ttc 字体文件。导入后字体保存在应用私有目录，" +
                "即使原始文件被删除也能继续使用。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Icon(LocalAppIcons.current.upload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("导入字体")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onUseSystem, modifier = Modifier.fillMaxWidth()) {
            Text("使用系统默认字体")
        }
        Spacer(Modifier.height(16.dp))
        if (fonts.isEmpty()) {
            Text("还没有导入字体", style = MaterialTheme.typography.bodyMedium)
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
            title = { Text("删除字体？") },
            text = { Text("将删除字体「${font.displayName}」，使用该字体的阅读设置会回到系统字体。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(font)
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
                                    text = "内置",
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
                    Text("默认", style = MaterialTheme.typography.labelMedium)
                }
                IconButton(onClick = onDelete) {
                    Icon(LocalAppIcons.current.delete, contentDescription = "删除字体")
                }
            }
            Text(
                text = "Aa Bb Cc 你好，世界 The quick brown fox 0123456789",
                style = TextStyle(
                    fontFamily = family ?: FontFamily.Default,
                    fontSize = 18.sp,
                ),
                modifier = Modifier.padding(vertical = 8.dp),
            )
            TextButton(onClick = onSetDefault) { Text("设为默认") }
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
    val display = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        context.display
    } else {
        @Suppress("DEPRECATION")
        (context.getSystemService(android.content.Context.WINDOW_SERVICE) as? android.view.WindowManager)
            ?.defaultDisplay
    }
    val current = display?.refreshRate ?: return "未知"
    val modes = runCatching { display.supportedModes }.getOrNull()
        .orEmpty()
        .map { it.refreshRate }
        .distinct()
        .sorted()
    val supported = if (modes.isEmpty()) "?" else modes.joinToString("/") { "%.0f".format(it) }
    return "当前 %.0f Hz · 支持 %s Hz".format(current, supported)
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
