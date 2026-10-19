package com.lightreader.core.util

import android.util.Log
import com.lightreader.BuildConfig

/**
 * Single logging facade. Only diagnostics go here; user facing errors are modelled as
 * [com.lightreader.core.format.ParseException] so the UI can explain what happened.
 */
object Logs {
    private const val TAG = "LightReader"
    private val ENABLED = BuildConfig.DEBUG

    fun d(message: String) {
        if (ENABLED) Log.d(TAG, message)
    }

    fun w(message: String, throwable: Throwable? = null) {
        if (ENABLED) Log.w(TAG, message, throwable)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (ENABLED) Log.e(TAG, message, throwable)
    }
}
