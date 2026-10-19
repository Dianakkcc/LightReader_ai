package com.lightreader.ui.theme

import com.lightreader.R
import com.lightreader.core.util.AppStrings
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
        ThemePreference.SYSTEM -> AppStrings.get(R.string.settings_theme_system)
        ThemePreference.PAPER -> AppStrings.get(R.string.ui_paper_179)
        ThemePreference.OAT -> AppStrings.get(R.string.ui_oat_180)
        ThemePreference.SAGE -> AppStrings.get(R.string.ui_sage_181)
        ThemePreference.ROSE -> AppStrings.get(R.string.ui_rose_182)
        ThemePreference.MINT -> AppStrings.get(R.string.ui_mint_183)
        ThemePreference.AZURE -> AppStrings.get(R.string.ui_lake_184)
        ThemePreference.LILAC -> AppStrings.get(R.string.ui_lilac_185)
        ThemePreference.NIGHT -> AppStrings.get(R.string.ui_night_gray_186)
        ThemePreference.DUSK -> AppStrings.get(R.string.ui_violet_night_187)
        ThemePreference.FOREST -> AppStrings.get(R.string.ui_deep_green_188)
        ThemePreference.BLACK -> AppStrings.get(R.string.settings_theme_black)
        ThemePreference.JIARAN -> AppStrings.get(R.string.ui_diana_190)
    }
}
