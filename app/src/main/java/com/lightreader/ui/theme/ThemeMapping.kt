package com.lightreader.ui.theme

import com.lightreader.core.model.ThemePreference

/**
 * One place that answers "which palette / which label does this preference have".
 *
 * The reading surface and the theme picker both need it; keeping the mapping in a single function
 * is what stops the two lists from drifting apart when a theme is added.
 */
object ThemeMapping {

    /** Palette of a preference. `dark` is only consulted by [ThemePreference.SYSTEM]. */
    fun readerThemeOf(preference: ThemePreference, dark: Boolean = false): ReaderTheme = when (preference) {
        ThemePreference.SYSTEM -> if (dark) ReaderTheme.NIGHT else ReaderTheme.PAPER
        ThemePreference.PAPER -> ReaderTheme.PAPER
        ThemePreference.OAT -> ReaderTheme.OAT
        ThemePreference.SAGE -> ReaderTheme.SAGE
        ThemePreference.ROSE -> ReaderTheme.ROSE
        ThemePreference.MINT -> ReaderTheme.MINT
        ThemePreference.AZURE -> ReaderTheme.AZURE
        ThemePreference.LILAC -> ReaderTheme.LILAC
        ThemePreference.NIGHT -> ReaderTheme.NIGHT
        ThemePreference.DUSK -> ReaderTheme.DUSK
        ThemePreference.FOREST -> ReaderTheme.FOREST
        ThemePreference.BLACK -> ReaderTheme.BLACK
        ThemePreference.JIARAN -> ReaderTheme.JIARAN
    }

    /** Name shown to the user. */
    fun labelOf(preference: ThemePreference): String = when (preference) {
        ThemePreference.SYSTEM -> "跟随系统"
        ThemePreference.PAPER -> "纸白"
        ThemePreference.OAT -> "燕麦"
        ThemePreference.SAGE -> "灰绿"
        ThemePreference.ROSE -> "樱粉"
        ThemePreference.MINT -> "薄荷"
        ThemePreference.AZURE -> "湖蓝"
        ThemePreference.LILAC -> "香芋"
        ThemePreference.NIGHT -> "夜灰"
        ThemePreference.DUSK -> "紫夜"
        ThemePreference.FOREST -> "墨绿"
        ThemePreference.BLACK -> "黑色"
        ThemePreference.JIARAN -> "嘉然"
    }
}
