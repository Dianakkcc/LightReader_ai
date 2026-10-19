package com.lightreader.core.util

import android.content.Context
import androidx.annotation.StringRes

/** Resource lookup shared by UI callbacks and view models. Retains only application context. */
object AppStrings {
    @Volatile private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    fun get(@StringRes id: Int, vararg args: Any?): String {
        val context = checkNotNull(applicationContext) { "Application resources not initialized" }
        return if (args.isEmpty()) context.getString(id) else context.getString(id, *args)
    }
}
