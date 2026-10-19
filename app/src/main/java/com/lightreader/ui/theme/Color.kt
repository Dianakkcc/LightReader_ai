package com.lightreader.ui.theme

import com.lightreader.R
import com.lightreader.core.util.AppStrings
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/**
 * Colour schemes for the app chrome (shelf, settings, dialogs). A scheme is generated from one
 * accent plus one paper tint, so adding a theme is one line - and every theme gets a light and a
 * dark variant that stay in the same family.
 */
enum class AppPalette(val id: String, private val labelRes: Int) {
    BLUE("blue", R.string.ui_ocean_245),
    ROSE("rose", R.string.ui_rose_182),
    MINT("mint", R.string.ui_mint_183),
    LILAC("lilac", R.string.ui_lilac_185),
    SUNSET("sunset", R.string.ui_sunset_246),
    FOREST("forest", R.string.ui_forest_247),
    JIARAN("jiaran", R.string.ui_diana_special_248),
    ;

    val label: String get() = AppStrings.get(labelRes)

    companion object {
        fun fromId(id: String?): AppPalette = entries.firstOrNull { it.id == id } ?: BLUE
    }
}

private data class PaletteSeed(val accent: Color, val paper: Color)

/**
 * Colour of the shelf / settings canvas. The palette above tints the whole chrome, which leaves a
 * slightly coloured page even when the user wants plain paper - this is the knob for that.
 */
enum class BackgroundTone(val id: String, private val labelRes: Int) {
    /** Let the palette decide (soft tint of the accent). */
    DEFAULT("default", R.string.ui_follow_palette_249),
    WHITE("white", R.string.ui_white_250),
    CREAM("cream", R.string.ui_cream_251),
    GREY("grey", R.string.ui_gray_252),
    ;

    val label: String get() = AppStrings.get(labelRes)

    companion object {
        fun fromId(id: String?): BackgroundTone = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

internal fun ColorScheme.withBackgroundTone(tone: BackgroundTone): ColorScheme {
    val dark = background.luminance() < 0.3f
    val (canvas, variant) = when (tone) {
        BackgroundTone.DEFAULT -> return this
        BackgroundTone.WHITE ->
            if (dark) Color(0xFF0D0E10) to Color(0xFF1C1F23) else Color(0xFFFFFFFF) to Color(0xFFF2F3F5)

        BackgroundTone.CREAM ->
            if (dark) Color(0xFF17130E) to Color(0xFF26201A) else Color(0xFFFAF6EE) to Color(0xFFF0E9DC)

        BackgroundTone.GREY ->
            if (dark) Color(0xFF101214) to Color(0xFF1F2226) else Color(0xFFF4F5F7) to Color(0xFFE8EAEE)
    }
    return surfacesFrom(canvas, variant, dark)
}

/**
 * Rebuilds **every** surface role from one canvas colour.
 *
 * M3 components do not all paint `background`: list rows and the app bar use `surface`, cards,
 * sheets and menus use the `surfaceContainer*` family, and those roles were never assigned here -
 * they kept Material's own baseline tint. The visible result was the settings screen looking like
 * a patchwork: pink rows next to white ones, in the exact colour the user had just chosen.
 *
 * One canvas therefore owns the whole family; the container steps stay a few percent away from it
 * so cards and sheets are still distinguishable.
 */
private fun ColorScheme.surfacesFrom(
    canvas: Color,
    variant: Color,
    dark: Boolean,
): ColorScheme {
    fun step(fraction: Float): Color = lerp(canvas, if (dark) Color.White else Color.Black, fraction)
    return copy(
        background = canvas,
        surface = canvas,
        surfaceVariant = variant,
        surfaceContainerLowest = canvas,
        surfaceContainerLow = step(if (dark) 0.05f else 0.02f),
        surfaceContainer = step(if (dark) 0.08f else 0.035f),
        surfaceContainerHigh = step(if (dark) 0.11f else 0.05f),
        surfaceContainerHighest = step(if (dark) 0.14f else 0.07f),
        surfaceDim = if (dark) canvas else step(0.09f),
        surfaceBright = if (dark) step(0.12f) else canvas,
    )
}

private fun Color.luminance(): Float = ColorUtils.calculateLuminance(toArgb()).toFloat()

private fun seedOf(palette: AppPalette): PaletteSeed = when (palette) {
    AppPalette.BLUE -> PaletteSeed(Color(0xFF3A6EA5), Color(0xFFF6F8FB))
    AppPalette.ROSE -> PaletteSeed(Color(0xFFC2426B), Color(0xFFFDF5F7))
    AppPalette.MINT -> PaletteSeed(Color(0xFF2E8B72), Color(0xFFF3FAF7))
    AppPalette.LILAC -> PaletteSeed(Color(0xFF6E5AA8), Color(0xFFF7F5FC))
    AppPalette.SUNSET -> PaletteSeed(Color(0xFFC4682B), Color(0xFFFDF7F2))
    AppPalette.FOREST -> PaletteSeed(Color(0xFF4A7A46), Color(0xFFF5F9F3))
    // Diana / 嘉然: the rose accent and warm paper of the Diana theme pack.
    AppPalette.JIARAN -> PaletteSeed(Color(0xFFB84970), Color(0xFFFBF8F6))
}

private fun hsl(color: Color, hue: Float = Float.NaN, saturation: Float = Float.NaN, lightness: Float = Float.NaN): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    val h = if (hue.isNaN()) hsl[0] else (hue + 360f) % 360f
    val s = (if (saturation.isNaN()) hsl[1] else saturation).coerceIn(0f, 1f)
    val l = (if (lightness.isNaN()) hsl[2] else lightness).coerceIn(0f, 1f)
    return Color.hsl(h, s, l)
}

/** Light or dark Material scheme derived from one accent + one paper tint. */
internal fun appColorScheme(palette: AppPalette, dark: Boolean): ColorScheme {
    val seed = seedOf(palette)
    val accent = seed.accent
    val paper = seed.paper
    if (!dark) {
        return lightColorScheme(
            primary = accent,
            onPrimary = Color.White,
            primaryContainer = hsl(accent, saturation = 0.55f, lightness = 0.90f),
            onPrimaryContainer = hsl(accent, saturation = 0.65f, lightness = 0.22f),
            secondary = hsl(accent, hue = hue(accent) + 16f, saturation = 0.34f, lightness = 0.44f),
            onSecondary = Color.White,
            secondaryContainer = hsl(accent, saturation = 0.30f, lightness = 0.93f),
            onSecondaryContainer = hsl(accent, saturation = 0.50f, lightness = 0.20f),
            tertiary = hsl(accent, hue = hue(accent) - 26f, saturation = 0.32f, lightness = 0.48f),
            onTertiary = Color.White,
            tertiaryContainer = hsl(accent, hue = hue(accent) - 26f, saturation = 0.28f, lightness = 0.94f),
            onTertiaryContainer = hsl(accent, hue = hue(accent) - 26f, saturation = 0.45f, lightness = 0.20f),
            background = paper,
            onBackground = hsl(paper, saturation = 0.10f, lightness = 0.13f),
            surface = paper,
            onSurface = hsl(paper, saturation = 0.10f, lightness = 0.13f),
            surfaceVariant = hsl(paper, saturation = 0.22f, lightness = 0.945f),
            onSurfaceVariant = hsl(paper, saturation = 0.10f, lightness = 0.38f),
            outline = hsl(paper, saturation = 0.14f, lightness = 0.78f),
            outlineVariant = hsl(paper, saturation = 0.16f, lightness = 0.90f),
            error = AppError,
            onError = AppOnError,
            errorContainer = AppErrorContainer,
            onErrorContainer = AppOnErrorContainer,
        ).surfacesFrom(
            canvas = paper,
            // Chips, text fields and unselected tags sit one step below the canvas.
            variant = hsl(paper, saturation = 0.20f, lightness = 0.945f),
            dark = false,
        )
    }
    return darkColorScheme(
        primary = hsl(accent, saturation = 0.55f, lightness = 0.74f),
        onPrimary = hsl(accent, saturation = 0.60f, lightness = 0.14f),
        primaryContainer = hsl(accent, saturation = 0.40f, lightness = 0.26f),
        onPrimaryContainer = hsl(accent, saturation = 0.45f, lightness = 0.90f),
        secondary = hsl(accent, hue = hue(accent) + 16f, saturation = 0.25f, lightness = 0.70f),
        onSecondary = hsl(accent, saturation = 0.35f, lightness = 0.14f),
        secondaryContainer = hsl(accent, saturation = 0.24f, lightness = 0.28f),
        onSecondaryContainer = hsl(accent, saturation = 0.30f, lightness = 0.88f),
        tertiary = hsl(accent, hue = hue(accent) - 26f, saturation = 0.26f, lightness = 0.74f),
        onTertiary = hsl(accent, saturation = 0.30f, lightness = 0.14f),
        tertiaryContainer = hsl(accent, hue = hue(accent) - 26f, saturation = 0.24f, lightness = 0.30f),
        onTertiaryContainer = hsl(accent, hue = hue(accent) - 26f, saturation = 0.28f, lightness = 0.90f),
        background = hsl(paper, saturation = 0.30f, lightness = 0.07f),
        onBackground = hsl(paper, saturation = 0.06f, lightness = 0.90f),
        surface = hsl(paper, saturation = 0.26f, lightness = 0.10f),
        onSurface = hsl(paper, saturation = 0.06f, lightness = 0.90f),
        surfaceVariant = hsl(paper, saturation = 0.18f, lightness = 0.17f),
        onSurfaceVariant = hsl(paper, saturation = 0.08f, lightness = 0.66f),
        outline = hsl(paper, saturation = 0.10f, lightness = 0.50f),
        outlineVariant = hsl(paper, saturation = 0.14f, lightness = 0.24f),
        error = AppErrorDark,
        onError = Color(0xFF3F1D19),
        errorContainer = AppErrorContainerDark,
        onErrorContainer = Color(0xFFF0DAD7),
    ).surfacesFrom(
        canvas = hsl(paper, saturation = 0.30f, lightness = 0.07f),
        variant = hsl(paper, saturation = 0.18f, lightness = 0.17f),
        dark = true,
    )
}

private fun hue(color: Color): Float {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color.toArgb(), hsl)
    return hsl[0]
}

/**
 * Clean and airy: white pages, a clearly blue accent, grey only where structure needs it.
 * (An earlier palette mixed grey into every hue, which looked muddy on a real display.)
 */
internal val AppPrimary = Color(0xFF3A6EA5)
internal val AppOnPrimary = Color(0xFFFFFFFF)
internal val AppPrimaryContainer = Color(0xFFE1EBF7)
internal val AppOnPrimaryContainer = Color(0xFF17324C)

internal val AppSecondary = Color(0xFF5B7C99)
internal val AppSecondaryContainer = Color(0xFFE4EDF5)
internal val AppOnSecondaryContainer = Color(0xFF1F2E3A)

internal val AppTertiary = Color(0xFF7A8BB5)
internal val AppTertiaryContainer = Color(0xFFE8EBF5)
internal val AppOnTertiaryContainer = Color(0xFF232A3D)

internal val AppBackgroundLight = Color(0xFFF6F8FB)
internal val AppSurfaceLight = Color(0xFFFFFFFF)
internal val AppSurfaceVariantLight = Color(0xFFEDF1F6)
internal val AppOutlineLight = Color(0xFFD3DAE3)
internal val AppOnSurfaceLight = Color(0xFF1A1F26)
internal val AppOnSurfaceVariantLight = Color(0xFF58616C)

internal val AppPrimaryDark = Color(0xFF9FC3EC)
internal val AppOnPrimaryDark = Color(0xFF0E2438)
internal val AppPrimaryContainerDark = Color(0xFF274058)
internal val AppOnPrimaryContainerDark = Color(0xFFDCE9F8)

internal val AppSecondaryDark = Color(0xFFA8C0D4)
internal val AppSecondaryContainerDark = Color(0xFF34444F)

internal val AppTertiaryDark = Color(0xFFB6C0DE)
internal val AppTertiaryContainerDark = Color(0xFF3A4260)

internal val AppBackgroundDark = Color(0xFF12161A)
internal val AppSurfaceDark = Color(0xFF191E23)
internal val AppSurfaceVariantDark = Color(0xFF2A3138)
internal val AppOutlineDark = Color(0xFF7C858F)
internal val AppOnSurfaceDark = Color(0xFFE2E6EA)
internal val AppOnSurfaceVariantDark = Color(0xFFA9B1BA)

internal val AppError = Color(0xFFB3261E)
internal val AppOnError = Color(0xFFFFFFFF)
internal val AppErrorContainer = Color(0xFFF7DDDA)
internal val AppOnErrorContainer = Color(0xFF410E0B)
internal val AppErrorDark = Color(0xFFE3A8A2)
internal val AppErrorContainerDark = Color(0xFF5C2B25)

/**
 * Palettes used inside the reading surface. The same values are forwarded to the WebView renderer,
 * so both renderers always agree.
 */
enum class ReaderTheme(val id: String) {
    /** Follows the system light/dark setting. */
    SYSTEM("system"),

    /** Near-white paper. */
    PAPER("paper"),

    /** Warm oat, for long evening sessions. */
    OAT("oat"),

    /** Soft green grey. */
    SAGE("sage"),

    /** Sakura pink. */
    ROSE("rose"),

    /** Cool mint. */
    MINT("mint"),

    /** Bright lake blue. */
    AZURE("azure"),

    /** Taro lilac. */
    LILAC("lilac"),

    /** Night blue grey, never pure black. */
    NIGHT("night"),

    /** Deep violet night. */
    DUSK("dusk"),

    /** Deep forest night. */
    FOREST("forest"),

    /** True black, for OLED screens. */
    BLACK("black"),

    /** Diana / 嘉然: rose ink on warm paper. */
    JIARAN("jiaran"),
}

data class ReaderPalette(
    val background: Color,
    val surface: Color,
    val text: Color,
    val secondaryText: Color,
    val accent: Color,
    val divider: Color,
    val isDark: Boolean,
)

fun readerPalette(theme: ReaderTheme): ReaderPalette = when (theme) {
    ReaderTheme.SYSTEM, ReaderTheme.PAPER -> ReaderPalette(
        background = Color(0xFFFCFCFA),
        surface = Color(0xFFF1F2F0),
        text = Color(0xFF22262B),
        secondaryText = Color(0xFF6B7280),
        accent = Color(0xFF3A6EA5),
        divider = Color(0x1F22262B),
        isDark = false,
    )

    ReaderTheme.OAT -> ReaderPalette(
        background = Color(0xFFF5EEE2),
        surface = Color(0xFFEAE1D2),
        text = Color(0xFF33302A),
        secondaryText = Color(0xFF7C7466),
        accent = Color(0xFFA98467),
        divider = Color(0x2433302A),
        isDark = false,
    )

    ReaderTheme.SAGE -> ReaderPalette(
        background = Color(0xFFEAF0EA),
        surface = Color(0xFFDEE6DC),
        text = Color(0xFF2E332E),
        secondaryText = Color(0xFF6F766E),
        accent = Color(0xFF6E8F77),
        divider = Color(0x242E332E),
        isDark = false,
    )

    ReaderTheme.ROSE -> ReaderPalette(
        background = Color(0xFFFBF1F4),
        surface = Color(0xFFF3E3E9),
        text = Color(0xFF33262B),
        secondaryText = Color(0xFF7A626A),
        accent = Color(0xFFC2426B),
        divider = Color(0x2433262B),
        isDark = false,
    )

    ReaderTheme.MINT -> ReaderPalette(
        background = Color(0xFFEDF7F3),
        surface = Color(0xFFDEEDE7),
        text = Color(0xFF25302C),
        secondaryText = Color(0xFF61736C),
        accent = Color(0xFF2E8B72),
        divider = Color(0x2425302C),
        isDark = false,
    )

    ReaderTheme.AZURE -> ReaderPalette(
        background = Color(0xFFEFF6FA),
        surface = Color(0xFFE0EDF5),
        text = Color(0xFF242C33),
        secondaryText = Color(0xFF5F6F7C),
        accent = Color(0xFF2F7CB5),
        divider = Color(0x24242C33),
        isDark = false,
    )

    ReaderTheme.LILAC -> ReaderPalette(
        background = Color(0xFFF5F1FA),
        surface = Color(0xFFEAE3F3),
        text = Color(0xFF2C2835),
        secondaryText = Color(0xFF6C6480),
        accent = Color(0xFF6E5AA8),
        divider = Color(0x242C2835),
        isDark = false,
    )

    ReaderTheme.NIGHT -> ReaderPalette(
        background = Color(0xFF1F2429),
        surface = Color(0xFF2A3036),
        text = Color(0xFFC6CDD4),
        secondaryText = Color(0xFF8A939C),
        accent = Color(0xFF93B4DC),
        divider = Color(0x33FFFFFF),
        isDark = true,
    )

    ReaderTheme.DUSK -> ReaderPalette(
        background = Color(0xFF221E2B),
        surface = Color(0xFF2D2738),
        text = Color(0xFFCFC7DC),
        secondaryText = Color(0xFF928AA3),
        accent = Color(0xFFB79CE8),
        divider = Color(0x33FFFFFF),
        isDark = true,
    )

    ReaderTheme.FOREST -> ReaderPalette(
        background = Color(0xFF1D2620),
        surface = Color(0xFF27332B),
        text = Color(0xFFC7D4C9),
        secondaryText = Color(0xFF8A9A8E),
        accent = Color(0xFF93C7A1),
        divider = Color(0x33FFFFFF),
        isDark = true,
    )

    ReaderTheme.BLACK -> ReaderPalette(
        background = Color(0xFF0E1012),
        surface = Color(0xFF191C1F),
        text = Color(0xFFB9C0C6),
        secondaryText = Color(0xFF7C848C),
        accent = Color(0xFF8CAAC8),
        divider = Color(0x33FFFFFF),
        isDark = true,
    )

    ReaderTheme.JIARAN -> ReaderPalette(
        background = Color(0xFFFBF8F6),
        surface = Color(0xFFF4E9EC),
        text = Color(0xFF2C2529),
        secondaryText = Color(0xFF7B6A70),
        accent = Color(0xFFB84970),
        divider = Color(0x242C2529),
        isDark = false,
    )
}
