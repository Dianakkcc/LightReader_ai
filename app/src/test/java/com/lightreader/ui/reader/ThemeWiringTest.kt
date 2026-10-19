package com.lightreader.ui.reader

import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.graphics.toArgb
import com.lightreader.core.format.ReaderAssets
import com.lightreader.core.model.ChapterContent
import com.lightreader.core.model.ReaderSettings
import com.lightreader.core.model.ThemePreference
import com.lightreader.ui.theme.AppPalette
import com.lightreader.ui.theme.BackgroundTone
import com.lightreader.ui.theme.ReaderTheme
import com.lightreader.ui.theme.ThemeStyle
import com.lightreader.ui.theme.appColorScheme
import com.lightreader.ui.theme.readerPalette
import com.lightreader.ui.theme.withBackgroundTone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The theme system is broad (13 paper colours, 7 chrome palettes, 4 canvas tones), so the wiring is
 * pinned down here: the Diana / 嘉然 artwork has to be inside the APK, the reading page has to ask
 * for it only in that theme, and a canvas tone has to really change the background.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemeWiringTest {

    private val chapter = ChapterContent(
        index = 0,
        title = "第一章",
        html = "<p>正文</p>",
        text = "正文",
        baseUrl = ReaderAssets.BASE_URL,
    )

    @Test
    fun `every paper theme has its own palette`() {
        // SYSTEM deliberately shares the paper palette; everything else is its own colour.
        val explicit = ThemePreference.entries.filter { it != ThemePreference.SYSTEM }
        val palettes = explicit.map { readerPalette(ReaderTheme.valueOf(it.name)) }
        assertEquals(explicit.size, palettes.size)
        val distinct = palettes.map { it.background.toArgb() }.distinct()
        assertEquals("每种阅读主题都要有自己的纸色", palettes.size, distinct.size)
        assertEquals(
            "跟随系统应和纸白一致",
            readerPalette(ReaderTheme.PAPER).background,
            readerPalette(ReaderTheme.SYSTEM).background,
        )
    }

    @Test
    fun `every chrome palette differs and canvas tones change the background`() {
        val lights = AppPalette.entries.map { appColorScheme(it, dark = false) }
        assertEquals(
            "每个配色都要给出不同的强调色",
            AppPalette.entries.size,
            lights.map { it.primary.value }.distinct().size,
        )
        val base = appColorScheme(AppPalette.BLUE, dark = false)
        val white = base.withBackgroundTone(BackgroundTone.WHITE)
        val cream = base.withBackgroundTone(BackgroundTone.CREAM)
        assertEquals(0xFFFFFFFF.toInt(), white.background.toArgb())
        assertNotEquals(base.background, white.background)
        assertNotEquals(white.background, cream.background)
        // The dark variants must stay dark, so the shelf does not flash white at night.
        val darkTone = appColorScheme(AppPalette.BLUE, dark = true)
            .withBackgroundTone(BackgroundTone.WHITE)
        assertTrue("深色模式下纯白应变成深色底", darkTone.background.luminance() < 0.2f)
    }

    @Test
    fun `the jiaran theme carries its artwork and other themes do not`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val asset = context.assets.open("brand/jiaran.png").use { it.readBytes() }
        assertTrue("嘉然立绘必须打进 APK", asset.size > 10_000)

        fun htmlFor(theme: ThemePreference) = ChapterHtml.build(
            content = chapter,
            settings = ReaderSettings(theme = theme),
            palette = readerPalette(
                if (theme == ThemePreference.JIARAN) ReaderTheme.JIARAN else ReaderTheme.PAPER,
            ),
            fontScale = 1f,
            horizontalPaddingPx = 20,
            verticalPaddingPx = 24,
            initialOffset = 0,
            nextChapterTitle = null,
        )

        val jiaran = htmlFor(ThemePreference.JIARAN)
        assertTrue("嘉然主题要挂上角标元素", jiaran.contains("id=\"lr-brand\""))
        assertTrue("角标要指向包内素材", jiaran.contains(ReaderAssets.brand("jiaran.png")))
        assertTrue("样式表要能被就地替换", jiaran.contains("id=\"lr-style\""))
        assertTrue(
            "其它主题不该挂角标元素",
            !htmlFor(ThemePreference.PAPER).contains("id=\"lr-brand\""),
        )
    }

    private fun androidx.compose.ui.graphics.Color.luminance(): Float =
        androidx.core.graphics.ColorUtils.calculateLuminance(
            this.toArgb(),
        ).toFloat()

    @Test
    fun `the atelier design language swaps the whole icon family`() {
        val classic = ThemeStyle.iconsFor(ThemeStyle.CLASSIC)
        val atelier = ThemeStyle.iconsFor(ThemeStyle.ATELIER)
        // Every role that the app draws must come from the other family in 静奢 mode.
        val pairs = listOf(
            classic.back to atelier.back,
            classic.search to atelier.search,
            classic.sort to atelier.sort,
            classic.light to atelier.light,
            classic.dark to atelier.dark,
            classic.settings to atelier.settings,
            classic.more to atelier.more,
            classic.grid to atelier.grid,
            classic.list to atelier.list,
            classic.bookmark to atelier.bookmark,
            classic.bookmarkOutline to atelier.bookmarkOutline,
            classic.fontSize to atelier.fontSize,
            classic.palette to atelier.palette,
            classic.upload to atelier.upload,
            classic.delete to atelier.delete,
            classic.add to atelier.add,
            classic.remove to atelier.remove,
            classic.close to atelier.close,
            classic.forward to atelier.forward,
        )
        pairs.forEachIndexed { index, (before, after) ->
            assertNotEquals("第 $index 个图标在静奢风格里必须换掉", before, after)
        }
        assertTrue(
            "静奢图标应是自绘的（名字带 atelier- 前缀）",
            atelier.search.name.startsWith("atelier-"),
        )
        assertTrue("经典风格仍是 Material 图标", !classic.search.name.startsWith("atelier-"))
        assertEquals(ThemeStyle.ATELIER, ThemeStyle.fromId("atelier"))
        assertEquals(ThemeStyle.CLASSIC, ThemeStyle.fromId(null))
    }

    /**
     * The chapter boundary banner is a deliberate design decision the user asked for twice: plain
     * text that fades into the page through a gradient - no coloured plate, no rounded panel, no
     * shadow. This pins the stylesheet so a later refactor cannot quietly bring the plate back.
     */
    @Test
    fun `the chapter boundary label is plain text on a gradient, not a coloured plate`() {
        val html = ChapterHtml.build(
            content = chapter,
            settings = ReaderSettings(),
            palette = readerPalette(ReaderTheme.PAPER),
            fontScale = 1f,
            horizontalPaddingPx = 20,
            verticalPaddingPx = 24,
            initialOffset = 0,
            nextChapterTitle = "下一章 · 第二章",
            previousChapterTitle = "上一章 · 序",
        )
        assertTrue("横幅元素要在页面里", html.contains("id=\"lr-edge-next\""))
        assertTrue("上一条横幅也要在", html.contains("id=\"lr-edge-prev\""))

        val css = html.substringAfter("<style", "").substringBefore("</style>")
        assertTrue("横幅朝页面方向必须是渐变收边", css.contains("linear-gradient"))
        assertTrue(
            "横幅底色必须透明（不能是色块）",
            css.contains("background-color: transparent !important"),
        )
        val plate = css.substringAfter(".lr-edge {").substringBefore("}")
        assertFalse("横幅不能再有圆角面板", plate.contains("border-radius"))
        assertFalse("横幅不能带投影", plate.contains("box-shadow"))
        assertFalse("横幅不能再铺底色", plate.contains("backdrop-filter"))
        // CSS reads 8-digit hex as #RRGGBBAA; writing the alpha first turned "transparent paper"
        // into an opaque cyan (#00FCFCFA), which is what made the banner look coloured.
        assertTrue(
            "渐变收边必须是纸色、完全透明（#RRGGBB00）",
            css.contains("#FCFCFA00"),
        )
        assertFalse("不能再写成 #AARRGGBB", css.contains("#00FCFCFA"))
    }

    /**
     * Reported from a real phone: the settings screen showed one colour for a couple of rows and a
     * different one for the rest. List rows and the app bar paint `surface`, cards and sheets paint
     * the `surfaceContainer*` family, and only `background` had been recoloured - so a "picked"
     * canvas ended up next to Material's own baseline tint.
     */
    @Test
    fun `the whole surface family follows the canvas`() {
        listOf(AppPalette.BLUE, AppPalette.JIARAN).forEach { palette ->
            listOf(false, true).forEach { dark ->
                val scheme = appColorScheme(palette, dark)
                assertEquals(
                    "列表行/顶栏必须和页面同色（$palette dark=$dark）",
                    scheme.background,
                    scheme.surface,
                )
                surfaceFamily(scheme).forEach { (name, color) ->
                    assertTrue(
                        "$name 也要由画布色推导（$palette dark=$dark）",
                        colorDistance(scheme.background, color) < 0.16f,
                    )
                }
            }
        }

        // 选了「纯白 / 淡米 / 浅灰」之后，整套表面角色都要跟着走。
        listOf(BackgroundTone.WHITE, BackgroundTone.CREAM, BackgroundTone.GREY).forEach { tone ->
            val scheme = appColorScheme(AppPalette.JIARAN, dark = false).withBackgroundTone(tone)
            assertEquals("选了底色后列表行仍要和页面同色", scheme.background, scheme.surface)
            surfaceFamily(scheme).forEach { (name, color) ->
                assertTrue(
                    "$name 没跟随所选底色（$tone）",
                    colorDistance(scheme.background, color) < 0.16f,
                )
            }
        }

        // 嘉然是暖纸色：任何表面角色都不该跑成 Material 默认的紫调（那是 B > G > R）。
        val warm = appColorScheme(AppPalette.JIARAN, dark = false)
        surfaceFamily(warm).forEach { (name, color) ->
            assertTrue(
                "$name 混进了 Material 默认的冷紫调：$color",
                color.red >= color.green && color.green >= color.blue,
            )
        }
    }

    private fun surfaceFamily(scheme: androidx.compose.material3.ColorScheme) = listOf(
        "surfaceContainerLowest" to scheme.surfaceContainerLowest,
        "surfaceContainerLow" to scheme.surfaceContainerLow,
        "surfaceContainer" to scheme.surfaceContainer,
        "surfaceContainerHigh" to scheme.surfaceContainerHigh,
        "surfaceContainerHighest" to scheme.surfaceContainerHighest,
        "surfaceDim" to scheme.surfaceDim,
        "surfaceBright" to scheme.surfaceBright,
    )

    private fun colorDistance(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Float =
        maxOf(kotlin.math.abs(a.red - b.red), kotlin.math.abs(a.green - b.green), kotlin.math.abs(a.blue - b.blue))
}
