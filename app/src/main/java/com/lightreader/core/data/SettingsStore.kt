package com.lightreader.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lightreader.core.model.AppSettings
import com.lightreader.core.model.AppThemeMode
import com.lightreader.ui.theme.AppPalette
import com.lightreader.ui.theme.BackgroundTone
import com.lightreader.ui.theme.ThemeStyle
import com.lightreader.core.model.LibraryLayout
import com.lightreader.core.model.LibrarySettings
import com.lightreader.core.model.LibrarySort
import com.lightreader.core.model.IndentMode
import com.lightreader.core.model.PageFit
import com.lightreader.core.model.ReaderSettings
import com.lightreader.core.model.ReadingMode
import com.lightreader.core.model.ShelfKind
import com.lightreader.core.model.TextAlignment
import com.lightreader.core.model.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Persisted user preferences (reading defaults, shelf layout). Small and reactive. */
class SettingsStore(
    context: Context,
    private val store: DataStore<Preferences> = context.applicationContext.settingsDataStore,
) {

    val settings: Flow<AppSettings> = store.data.map { preferences -> preferences.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    suspend fun updateReader(transform: (ReaderSettings) -> ReaderSettings) {
        store.edit { preferences ->
            val updated = transform(preferences.toSettings().reader)
            preferences[Keys.FONT_ID] = updated.fontId ?: -1L
            preferences[Keys.FONT_FAMILY] = updated.fontFamily.orEmpty()
            preferences[Keys.FONT_PATH] = updated.fontPath.orEmpty()
            preferences[Keys.FONT_NAME] = updated.fontDisplayName.orEmpty()
            preferences[Keys.FONT_SERIF] = updated.useSerif
            preferences[Keys.FONT_SIZE] = updated.fontSizeSp
            preferences[Keys.LINE_HEIGHT] = updated.lineHeightMultiplier
            preferences[Keys.LETTER_SPACING] = updated.letterSpacingEm
            preferences[Keys.PARAGRAPH_SPACING] = updated.paragraphSpacingEm
            preferences[Keys.MARGIN] = updated.marginDp
            preferences[Keys.ALIGNMENT] = updated.alignment.id
            preferences[Keys.INDENT] = updated.indentMode.id
            preferences[Keys.THEME] = updated.theme.id
            preferences[Keys.READING_MODE] = updated.readingMode.id
            preferences[Keys.PAGE_FIT] = updated.pageFit.id
            preferences[Keys.DUAL_PAGE] = updated.dualPage
        }
    }

    suspend fun updateLibrary(transform: (LibrarySettings) -> LibrarySettings) {
        store.edit { preferences ->
            val updated = transform(preferences.toSettings().library)
            preferences[Keys.LAYOUT] = updated.layout.id
            preferences[Keys.SORT] = updated.sort.id
            preferences[Keys.SHOW_AUTHOR] = updated.showAuthor
            preferences[Keys.SHOW_PROGRESS] = updated.showProgress
            preferences[Keys.SHELF] = updated.shelf.id
            preferences[Keys.GRID_COLUMNS] = updated.gridColumns.coerceIn(0, 6)
            preferences[Keys.COMIC_GRID_COLUMNS] = updated.comicGridColumns.coerceIn(0, 6)
        }
    }

    suspend fun updateThemeMode(mode: AppThemeMode) {
        store.edit { preferences -> preferences[Keys.THEME_MODE] = mode.id }
    }

    suspend fun updatePalette(palette: AppPalette) {
        store.edit { preferences -> preferences[Keys.PALETTE] = palette.id }
    }

    suspend fun updateBackground(tone: BackgroundTone) {
        store.edit { preferences -> preferences[Keys.BACKGROUND] = tone.id }
    }

    suspend fun updateStyle(style: ThemeStyle) {
        store.edit { preferences -> preferences[Keys.STYLE] = style.id }
    }

    suspend fun replace(settings: AppSettings) {
        updateReader { settings.reader }
        updateLibrary { settings.library }
    }

    /** Names of built-in fonts the user deleted on purpose; they are not reinstalled. */
    suspend fun removedBuiltinFonts(): Set<String> = store.data.map { preferences ->
        preferences[Keys.REMOVED_BUILTINS] ?: emptySet()
    }.first()

    suspend fun markBuiltinRemoved(assetName: String) {
        store.edit { preferences ->
            val current = preferences[Keys.REMOVED_BUILTINS] ?: emptySet()
            preferences[Keys.REMOVED_BUILTINS] = current + assetName
        }
    }

    private fun Preferences.toSettings(): AppSettings = AppSettings(
        reader = ReaderSettings(
            fontId = this[Keys.FONT_ID]?.takeIf { it > 0 },
            fontFamily = this[Keys.FONT_FAMILY]?.takeIf { it.isNotBlank() },
            fontPath = this[Keys.FONT_PATH]?.takeIf { it.isNotBlank() },
            fontDisplayName = this[Keys.FONT_NAME]?.takeIf { it.isNotBlank() },
            useSerif = this[Keys.FONT_SERIF] ?: false,
            fontSizeSp = this[Keys.FONT_SIZE] ?: 19f,
            lineHeightMultiplier = this[Keys.LINE_HEIGHT] ?: 1.8f,
            letterSpacingEm = this[Keys.LETTER_SPACING] ?: 0.02f,
            paragraphSpacingEm = this[Keys.PARAGRAPH_SPACING] ?: 0.6f,
            marginDp = this[Keys.MARGIN] ?: 22,
            alignment = TextAlignment.fromId(this[Keys.ALIGNMENT]),
            indentMode = IndentMode.fromId(this[Keys.INDENT]),
            theme = ThemePreference.fromId(this[Keys.THEME]),
            readingMode = ReadingMode.fromId(this[Keys.READING_MODE]),
            pageFit = PageFit.fromId(this[Keys.PAGE_FIT]),
            dualPage = this[Keys.DUAL_PAGE] ?: false,
        ),
        library = LibrarySettings(
            layout = LibraryLayout.fromId(this[Keys.LAYOUT]),
            sort = LibrarySort.fromId(this[Keys.SORT]),
            showAuthor = this[Keys.SHOW_AUTHOR] ?: true,
            showProgress = this[Keys.SHOW_PROGRESS] ?: true,
            shelf = ShelfKind.fromId(this[Keys.SHELF]),
            gridColumns = (this[Keys.GRID_COLUMNS] ?: 0).coerceIn(0, 6),
            comicGridColumns = (this[Keys.COMIC_GRID_COLUMNS] ?: 3).coerceIn(0, 6),
        ),
        themeMode = AppThemeMode.fromId(this[Keys.THEME_MODE]),
        palette = AppPalette.fromId(this[Keys.PALETTE]),
        background = BackgroundTone.fromId(this[Keys.BACKGROUND]),
        style = ThemeStyle.fromId(this[Keys.STYLE]),
    )

    private object Keys {
        val FONT_ID = longPreferencesKey("reader_font_id")
        val FONT_FAMILY = stringPreferencesKey("reader_font_family")
        val FONT_PATH = stringPreferencesKey("reader_font_path")
        val FONT_NAME = stringPreferencesKey("reader_font_name")
        val FONT_SERIF = booleanPreferencesKey("reader_font_serif")
        val FONT_SIZE = floatPreferencesKey("reader_font_size")
        val LINE_HEIGHT = floatPreferencesKey("reader_line_height")
        val LETTER_SPACING = floatPreferencesKey("reader_letter_spacing")
        val PARAGRAPH_SPACING = floatPreferencesKey("reader_paragraph_spacing")
        val MARGIN = intPreferencesKey("reader_margin")
        val ALIGNMENT = stringPreferencesKey("reader_alignment")
        val INDENT = stringPreferencesKey("reader_indent")
        val THEME = stringPreferencesKey("reader_theme")
        val READING_MODE = stringPreferencesKey("reader_mode")
        val PAGE_FIT = stringPreferencesKey("reader_page_fit")
        val DUAL_PAGE = booleanPreferencesKey("reader_dual_page")
        val LAYOUT = stringPreferencesKey("library_layout")
        val SORT = stringPreferencesKey("library_sort")
        val SHOW_AUTHOR = booleanPreferencesKey("library_show_author")
        val SHOW_PROGRESS = booleanPreferencesKey("library_show_progress")
        val SHELF = stringPreferencesKey("library_shelf")
        val GRID_COLUMNS = intPreferencesKey("library_grid_columns")
        val COMIC_GRID_COLUMNS = intPreferencesKey("library_comic_grid_columns")
        val REMOVED_BUILTINS = stringSetPreferencesKey("removed_builtin_fonts")
        val THEME_MODE = stringPreferencesKey("app_theme_mode")
        val PALETTE = stringPreferencesKey("app_palette")
        val BACKGROUND = stringPreferencesKey("app_background")
        val STYLE = stringPreferencesKey("app_style")
    }
}
