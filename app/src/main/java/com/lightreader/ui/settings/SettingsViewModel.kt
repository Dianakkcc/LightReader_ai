package com.lightreader.ui.settings

import com.lightreader.R
import com.lightreader.core.util.AppStrings
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lightreader.AppContainer
import com.lightreader.core.model.FontItem
import com.lightreader.core.model.AppThemeMode
import com.lightreader.core.model.LibrarySettings
import com.lightreader.core.model.ReaderSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    data class UiState(
        val reader: ReaderSettings = ReaderSettings(),
        val library: LibrarySettings = LibrarySettings(),
        val fonts: List<FontItem> = emptyList(),
        val defaultFontId: Long? = null,
        val cacheBytes: Long = 0L,
        val message: String? = null,
        val appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
        val appPalette: com.lightreader.ui.theme.AppPalette =
            com.lightreader.ui.theme.AppPalette.BLUE,
        val appBackground: com.lightreader.ui.theme.BackgroundTone =
            com.lightreader.ui.theme.BackgroundTone.DEFAULT,
        val appStyle: com.lightreader.ui.theme.ThemeStyle =
            com.lightreader.ui.theme.ThemeStyle.CLASSIC,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.settingsStore.settings.collect { settings ->
                _state.update {
                    it.copy(
                        reader = settings.reader,
                        library = settings.library,
                        appThemeMode = settings.themeMode,
                        appPalette = settings.palette,
                        appBackground = settings.background,
                        appStyle = settings.style,
                    )
                }
            }
        }
        viewModelScope.launch {
            container.readingRepository.observeFonts().collect { fonts ->
                _state.update { it.copy(fonts = fonts) }
            }
        }
        viewModelScope.launch {
            val default = container.readingRepository.findDefaultFont()
            _state.update { it.copy(defaultFontId = default?.id) }
        }
        refreshCacheSize()
    }

    fun updateReader(transform: (ReaderSettings) -> ReaderSettings) {
        viewModelScope.launch { container.settingsStore.updateReader(transform) }
    }

    fun updateLibrary(transform: (LibrarySettings) -> LibrarySettings) {
        viewModelScope.launch { container.settingsStore.updateLibrary(transform) }
    }

    fun importFont(uri: Uri) {
        viewModelScope.launch {
            val font = runCatching { container.readingRepository.importFont(uri) }.getOrNull()
            _state.update {
                it.copy(
                    message = if (font != null) {
                        AppStrings.get(R.string.settings_font_imported, font.displayName)
                    } else {
                        AppStrings.get(R.string.settings_font_invalid)
                    },
                )
            }
        }
    }

    fun deleteFont(font: FontItem) {
        viewModelScope.launch {
            container.readingRepository.deleteFont(font)
            if (_state.value.reader.fontId == font.id) {
                container.settingsStore.updateReader {
                    it.copy(fontId = null, fontFamily = null, fontPath = null, fontDisplayName = null)
                }
            }
            if (_state.value.defaultFontId == font.id) {
                container.readingRepository.setDefaultFont(null)
                _state.update { it.copy(defaultFontId = null) }
            }
            _state.update { it.copy(message = AppStrings.get(R.string.ui_deleted_font_235, font.displayName)) }
        }
    }

    fun setDefaultFont(font: FontItem) {
        viewModelScope.launch {
            val ready = container.readingRepository.prepareFont(font)
            container.readingRepository.setDefaultFont(ready.id)
            container.settingsStore.updateReader {
                it.copy(
                    fontId = ready.id,
                    fontFamily = ready.displayName,
                    fontPath = ready.path,
                    fontDisplayName = ready.displayName,
                )
            }
            _state.update {
                it.copy(
                    defaultFontId = ready.id,
                    message = AppStrings.get(R.string.ui_set_as_the_default_font_236, ready.displayName),
                )
            }
        }
    }

    fun useSystemFont() {
        viewModelScope.launch {
            container.readingRepository.setDefaultFont(null)
            container.settingsStore.updateReader {
                it.copy(fontId = null, fontFamily = null, fontPath = null, fontDisplayName = null)
            }
            _state.update { it.copy(defaultFontId = null) }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            container.clearCaches()
            _state.update { it.copy(cacheBytes = 0L, message = AppStrings.get(R.string.ui_cache_cleared_237)) }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            container.readingRepository.clearHistory()
            _state.update { it.copy(message = AppStrings.get(R.string.ui_reading_history_cleared_238)) }
        }
    }

    fun export(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching { container.dataTransferRepository.export(uri) }
            _state.update {
                it.copy(
                    message = result.fold(
                        onSuccess = { count -> AppStrings.get(R.string.ui_exported_reading_data_for_books_239, count) },
                        onFailure = { error -> AppStrings.get(R.string.ui_export_failed_240, error.message) },
                    ),
                )
            }
        }
    }

    fun restore(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching { container.dataTransferRepository.restore(uri) }
            _state.update {
                it.copy(
                    message = result.fold(
                        onSuccess = { stats -> AppStrings.get(R.string.ui_restored_data_for_books_241, stats.matchedBooks) },
                        onFailure = { error -> AppStrings.get(R.string.ui_import_failed_61, error.message) },
                    ),
                )
            }
        }
    }

    fun refreshCacheSize() {
        viewModelScope.launch {
            val size = container.workspace.cacheSizeBytes() + container.imageLoader.cacheSizeBytes() +
                container.pageCache.sizeBytes()
            _state.update { it.copy(cacheBytes = size) }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun setThemeMode(mode: AppThemeMode) {
        _state.update { it.copy(appThemeMode = mode) }
        viewModelScope.launch { container.settingsStore.updateThemeMode(mode) }
    }

    fun setPalette(palette: com.lightreader.ui.theme.AppPalette) {
        _state.update { it.copy(appPalette = palette) }
        viewModelScope.launch { container.settingsStore.updatePalette(palette) }
    }

    fun setBackground(tone: com.lightreader.ui.theme.BackgroundTone) {
        _state.update { it.copy(appBackground = tone) }
        viewModelScope.launch { container.settingsStore.updateBackground(tone) }
    }

    fun setStyle(style: com.lightreader.ui.theme.ThemeStyle) {
        _state.update { it.copy(appStyle = style) }
        viewModelScope.launch { container.settingsStore.updateStyle(style) }
    }
}
