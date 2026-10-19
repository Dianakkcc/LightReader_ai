package com.lightreader.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.view.WindowCompat

internal val Color_White = Color(0xFFFFFFFF)

private val LightColors = lightColorScheme(
    primary = AppPrimary,
    onPrimary = AppOnPrimary,
    primaryContainer = AppPrimaryContainer,
    onPrimaryContainer = AppOnPrimaryContainer,
    secondary = AppSecondary,
    onSecondary = Color_White,
    secondaryContainer = AppSecondaryContainer,
    onSecondaryContainer = AppOnSecondaryContainer,
    tertiary = AppTertiary,
    onTertiary = Color_White,
    tertiaryContainer = AppTertiaryContainer,
    onTertiaryContainer = AppOnTertiaryContainer,
    background = AppBackgroundLight,
    onBackground = AppOnSurfaceLight,
    surface = AppSurfaceLight,
    onSurface = AppOnSurfaceLight,
    surfaceVariant = AppSurfaceVariantLight,
    onSurfaceVariant = AppOnSurfaceVariantLight,
    outline = AppOutlineLight,
    outlineVariant = AppSurfaceVariantLight,
    error = AppError,
    onError = AppOnError,
    errorContainer = AppErrorContainer,
    onErrorContainer = AppOnErrorContainer,
)

private val DarkColors = darkColorScheme(
    primary = AppPrimaryDark,
    onPrimary = AppOnPrimaryDark,
    primaryContainer = AppPrimaryContainerDark,
    onPrimaryContainer = AppOnPrimaryContainerDark,
    secondary = AppSecondaryDark,
    onSecondary = Color(0xFF1F2A1E),
    secondaryContainer = AppSecondaryContainerDark,
    onSecondaryContainer = Color(0xFFD9E4D6),
    tertiary = AppTertiaryDark,
    onTertiary = Color(0xFF2A1D15),
    tertiaryContainer = AppTertiaryContainerDark,
    onTertiaryContainer = Color(0xFFF3E3D8),
    background = AppBackgroundDark,
    onBackground = AppOnSurfaceDark,
    surface = AppSurfaceDark,
    onSurface = AppOnSurfaceDark,
    surfaceVariant = AppSurfaceVariantDark,
    onSurfaceVariant = AppOnSurfaceVariantDark,
    outline = AppOutlineDark,
    outlineVariant = AppSurfaceVariantDark,
    error = AppErrorDark,
    onError = Color(0xFF3F1D19),
    errorContainer = AppErrorContainerDark,
    onErrorContainer = Color(0xFFF0DAD7),
)

private fun Typography.scale(factor: Float): Typography = copy(
    displayLarge = displayLarge.scaled(factor),
    displayMedium = displayMedium.scaled(factor),
    displaySmall = displaySmall.scaled(factor),
    headlineLarge = headlineLarge.scaled(factor),
    headlineMedium = headlineMedium.scaled(factor),
    headlineSmall = headlineSmall.scaled(factor),
    titleLarge = titleLarge.scaled(factor),
    titleMedium = titleMedium.scaled(factor),
    titleSmall = titleSmall.scaled(factor),
    bodyLarge = bodyLarge.scaled(factor),
    bodyMedium = bodyMedium.scaled(factor),
    bodySmall = bodySmall.scaled(factor),
    labelLarge = labelLarge.scaled(factor),
    labelMedium = labelMedium.scaled(factor),
    labelSmall = labelSmall.scaled(factor),
)

private fun androidx.compose.ui.text.TextStyle.scaled(factor: Float) =
    copy(fontSize = fontSize * factor, lineHeight = lineHeight * factor)

/**
 * @param systemFontScale mirrors the Android "font size" accessibility setting so that the
 * whole UI scales with the user's preference instead of only the reading surface.
 */
@Composable
fun LightReaderTheme(
    palette: AppPalette = AppPalette.BLUE,
    background: BackgroundTone = BackgroundTone.DEFAULT,
    style: ThemeStyle = ThemeStyle.CLASSIC,
    darkTheme: Boolean = isSystemInDarkTheme(),
    systemFontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val colorScheme = appColorScheme(palette, darkTheme).withBackgroundTone(background)
    val base = if (systemFontScale == 1f) Typography() else Typography().scale(systemFontScale)
    // 静奢: serif headings, tighter leading, generously rounded surfaces - the same content, a
    // completely different voice.
    val typography = if (style == ThemeStyle.ATELIER) {
        base.copy(
            headlineSmall = base.headlineSmall.copy(
                fontFamily = FontFamily.Serif,
                letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified,
            ),
            titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Serif),
            titleMedium = base.titleMedium.copy(fontFamily = FontFamily.Serif),
            titleSmall = base.titleSmall.copy(
                fontFamily = FontFamily.Serif,
                letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified,
            ),
        )
    } else {
        base
    }
    val shapes = if (style == ThemeStyle.ATELIER) AtelierShapes else Shapes()

    val view = LocalView.current
    val context = LocalContext.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = context.findActivity() ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalAppIcons provides ThemeStyle.iconsFor(style)) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = shapes,
            content = content,
        )
    }
}

private val AtelierShapes = Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

private tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
