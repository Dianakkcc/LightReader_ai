package com.lightreader.core.util

import androidx.test.core.app.ApplicationProvider
import com.lightreader.R
import com.lightreader.core.model.ShelfKind
import com.lightreader.ui.theme.AppPalette
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppStringsTest {
    @Test @Config(qualifiers = "en")
    fun `English resources include dynamic shelf and theme labels`() {
        AppStrings.initialize(ApplicationProvider.getApplicationContext())
        assertEquals("Books", ShelfKind.BOOK.displayName)
        assertEquals("Comics", ShelfKind.COMIC.displayName)
        assertEquals("Ocean", AppPalette.BLUE.label)
        assertEquals("Settings", AppStrings.get(R.string.settings_title))
        assertEquals("5 min ago", AppStrings.get(R.string.time_minutes_ago, 5))
    }

    @Test @Config(qualifiers = "zh")
    fun `Chinese resources and formatted parameters remain available`() {
        AppStrings.initialize(ApplicationProvider.getApplicationContext())
        assertEquals("书籍", ShelfKind.BOOK.displayName)
        assertEquals("设置", AppStrings.get(R.string.settings_title))
        assertEquals("5 分钟前", AppStrings.get(R.string.time_minutes_ago, 5))
    }
}
