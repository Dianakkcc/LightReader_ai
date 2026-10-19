package com.lightreader.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Every icon the app draws, behind one indirection.
 *
 * The classic look uses the Material set. The "静奢" (Atelier) design language replaces the whole
 * set with a hand drawn line family - 24x24 grid, 1.4px strokes, round caps and joins - so that
 * theme looks like a different product while nothing else changes.
 */
data class AppIconSet(
    val back: ImageVector,
    val forward: ImageVector,
    val close: ImageVector,
    val search: ImageVector,
    val sort: ImageVector,
    val light: ImageVector,
    val dark: ImageVector,
    val settings: ImageVector,
    val more: ImageVector,
    val add: ImageVector,
    val remove: ImageVector,
    val grid: ImageVector,
    val list: ImageVector,
    val bookmark: ImageVector,
    val bookmarkOutline: ImageVector,
    val fontSize: ImageVector,
    val palette: ImageVector,
    val upload: ImageVector,
    val delete: ImageVector,
)

val ClassicIcons = AppIconSet(
    back = Icons.AutoMirrored.Outlined.ArrowBack,
    forward = Icons.AutoMirrored.Outlined.ArrowForward,
    close = Icons.Outlined.Close,
    search = Icons.Outlined.Search,
    sort = Icons.Outlined.Sort,
    light = Icons.Outlined.LightMode,
    dark = Icons.Outlined.DarkMode,
    settings = Icons.Outlined.Settings,
    more = Icons.Outlined.MoreVert,
    add = Icons.Outlined.Add,
    remove = Icons.Outlined.Remove,
    grid = Icons.Outlined.GridView,
    list = Icons.AutoMirrored.Outlined.List,
    bookmark = Icons.Outlined.Bookmark,
    bookmarkOutline = Icons.Outlined.BookmarkBorder,
    fontSize = Icons.Outlined.FormatSize,
    palette = Icons.Outlined.Palette,
    upload = Icons.Outlined.FileUpload,
    delete = Icons.Outlined.Delete,
)

// ---------------------------------------------------------------- drawing helpers

private fun PathBuilder.line(x1: Float, y1: Float, x2: Float, y2: Float) {
    moveTo(x1, y1)
    lineTo(x2, y2)
}

private fun PathBuilder.box(left: Float, top: Float, right: Float, bottom: Float) {
    moveTo(left, top)
    lineTo(right, top)
    lineTo(right, bottom)
    lineTo(left, bottom)
    close()
}

private fun PathBuilder.dot(cx: Float, cy: Float, radius: Float) {
    moveTo(cx - radius, cy)
    arcToRelative(radius, radius, 0f, true, true, radius * 2, 0f)
    arcToRelative(radius, radius, 0f, true, true, -radius * 2, 0f)
    close()
}

private fun PathBuilder.bookmark() {
    moveTo(7f, 4.5f)
    lineTo(17f, 4.5f)
    lineTo(17f, 19.5f)
    lineTo(12f, 15.2f)
    lineTo(7f, 19.5f)
    close()
}

/** A calm, evenly drawn line icon: 1.4px stroke, round caps, no fill. */
private fun lineIcon(name: String, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 1.4f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block,
    ).build()

private fun filledIcon(name: String, block: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(
        fill = SolidColor(Color.Black),
        pathBuilder = block,
    ).build()

// ---------------------------------------------------------------- the atelier family

val AtelierIcons = AppIconSet(
    back = lineIcon("atelier-back") {
        line(19f, 12f, 5.5f, 12f)
        line(11f, 6f, 5f, 12f)
        line(11f, 18f, 5f, 12f)
    },
    forward = lineIcon("atelier-forward") {
        line(5f, 12f, 18.5f, 12f)
        line(13f, 6f, 19f, 12f)
        line(13f, 18f, 19f, 12f)
    },
    close = lineIcon("atelier-close") {
        line(7f, 7f, 17f, 17f)
        line(17f, 7f, 7f, 17f)
    },
    search = lineIcon("atelier-search") {
        dot(10.5f, 10.5f, 5.4f)
        line(14.5f, 14.5f, 19.5f, 19.5f)
    },
    sort = lineIcon("atelier-sort") {
        dot(5.4f, 7f, 0.9f)
        dot(5.4f, 12f, 0.9f)
        dot(5.4f, 17f, 0.9f)
        line(9.5f, 7f, 19f, 7f)
        line(9.5f, 12f, 16f, 12f)
        line(9.5f, 17f, 13f, 17f)
    },
    light = lineIcon("atelier-sun") {
        dot(12f, 12f, 4.2f)
        line(12f, 3.2f, 12f, 5.2f)
        line(12f, 18.8f, 12f, 20.8f)
        line(3.2f, 12f, 5.2f, 12f)
        line(18.8f, 12f, 20.8f, 12f)
        line(5.8f, 5.8f, 7.2f, 7.2f)
        line(16.8f, 16.8f, 18.2f, 18.2f)
        line(5.8f, 18.2f, 7.2f, 16.8f)
        line(16.8f, 7.2f, 18.2f, 5.8f)
    },
    // The crescent is a filled disc with a second disc punched out of it (EvenOdd). Drawing it as
    // two arcs used to reach the icon edge, which looked clipped inside the round header button.
    dark = ImageVector.Builder(
        name = "atelier-moon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(
        fill = SolidColor(Color.Black),
        pathFillType = PathFillType.EvenOdd,
    ) {
        dot(11.4f, 12.2f, 7.8f)
        dot(16.6f, 8.6f, 6.4f)
    }.build(),
    settings = lineIcon("atelier-settings") {
        dot(12f, 12f, 3.1f)
        line(12f, 3.4f, 12f, 5.6f)
        line(12f, 18.4f, 12f, 20.6f)
        line(3.4f, 12f, 5.6f, 12f)
        line(18.4f, 12f, 20.6f, 12f)
        line(6f, 6f, 7.5f, 7.5f)
        line(16.5f, 16.5f, 18f, 18f)
        line(6f, 18f, 7.5f, 16.5f)
        line(16.5f, 7.5f, 18f, 6f)
    },
    more = lineIcon("atelier-more") {
        dot(12f, 5.4f, 1.25f)
        dot(12f, 12f, 1.25f)
        dot(12f, 18.6f, 1.25f)
    },
    add = lineIcon("atelier-add") {
        line(12f, 5f, 12f, 19f)
        line(5f, 12f, 19f, 12f)
    },
    remove = lineIcon("atelier-remove") {
        line(5f, 12f, 19f, 12f)
    },
    grid = lineIcon("atelier-grid") {
        box(4f, 4f, 11f, 11f)
        box(13f, 4f, 20f, 11f)
        box(4f, 13f, 11f, 20f)
        box(13f, 13f, 20f, 20f)
    },
    list = lineIcon("atelier-list") {
        line(4f, 7f, 20f, 7f)
        line(4f, 12f, 20f, 12f)
        line(4f, 17f, 20f, 17f)
    },
    bookmark = filledIcon("atelier-bookmark-filled") { bookmark() },
    bookmarkOutline = lineIcon("atelier-bookmark") { bookmark() },
    fontSize = lineIcon("atelier-font-size") {
        line(4f, 17f, 9.5f, 5f)
        line(9.5f, 5f, 15f, 17f)
        line(6.4f, 12.6f, 12.6f, 12.6f)
        line(17f, 11.5f, 20f, 11.5f)
        line(18.5f, 10f, 18.5f, 13f)
    },
    palette = lineIcon("atelier-palette") {
        dot(12f, 12f, 8.2f)
        dot(8.6f, 9.4f, 1.05f)
        dot(14.6f, 9.2f, 1.05f)
        dot(9.2f, 15.2f, 1.05f)
        dot(15.2f, 14.6f, 1.05f)
    },
    upload = lineIcon("atelier-upload") {
        line(4.5f, 15f, 4.5f, 19.5f)
        line(4.5f, 19.5f, 19.5f, 19.5f)
        line(19.5f, 19.5f, 19.5f, 15f)
        line(12f, 4.5f, 12f, 15f)
        line(8f, 8.4f, 12f, 4.5f)
        line(16f, 8.4f, 12f, 4.5f)
    },
    delete = lineIcon("atelier-delete") {
        line(5f, 7f, 19f, 7f)
        line(9.5f, 7f, 9.5f, 4.4f)
        line(9.5f, 4.4f, 14.5f, 4.4f)
        line(14.5f, 4.4f, 14.5f, 7f)
        line(7f, 7f, 8.2f, 19.6f)
        line(8.2f, 19.6f, 15.8f, 19.6f)
        line(15.8f, 19.6f, 17f, 7f)
        line(10.4f, 10.5f, 10.4f, 16.2f)
        line(13.6f, 10.5f, 13.6f, 16.2f)
    },
)

/**
 * The design language of the app chrome. `CLASSIC` keeps the Material icons; `ATELIER` swaps the
 * whole icon family, the heading font and the corner radii.
 */
enum class ThemeStyle(val id: String, val label: String) {
    CLASSIC("classic", "经典"),
    ATELIER("atelier", "静奢"),
    ;

    companion object {
        fun fromId(id: String?): ThemeStyle = entries.firstOrNull { it.id == id } ?: CLASSIC

        fun iconsFor(style: ThemeStyle): AppIconSet =
            if (style == ATELIER) AtelierIcons else ClassicIcons
    }
}

/**
 * Icons of the active design language; read this instead of `Icons.Outlined.*`.
 *
 * Deliberately *not* a `staticCompositionLocalOf`: the value changes when the user switches design
 * language, and a static local does not invalidate composables that already read it - the reading
 * screen kept the old icons until it was recreated.
 */
val LocalAppIcons = compositionLocalOf { ClassicIcons }
