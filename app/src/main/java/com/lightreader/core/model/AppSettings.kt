package com.lightreader.core.model

import com.lightreader.ui.theme.AppPalette
import com.lightreader.ui.theme.BackgroundTone
import com.lightreader.ui.theme.ThemeStyle

/** App-wide light/dark choice, independent from the reading surface theme. */
enum class AppThemeMode(val id: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        fun fromId(id: String?): AppThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

data class AppSettings(
    val reader: ReaderSettings = ReaderSettings(),
    val library: LibrarySettings = LibrarySettings(),
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    /** Accent scheme of the app chrome: shelf, settings, panels, dialogs. */
    val palette: AppPalette = AppPalette.BLUE,
    /** Canvas colour of the shelf / settings: the palette tint, pure white, cream or grey. */
    val background: BackgroundTone = BackgroundTone.DEFAULT,
    /** Design language: classic Material icons, or the 静奢 line-icon family. */
    val style: ThemeStyle = ThemeStyle.CLASSIC,
)
