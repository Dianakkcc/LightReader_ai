package com.lightreader.core.util

import android.util.Log

/**
 * Single logging facade. Only diagnostics go here; user facing errors are modelled as
 * [com.lightreader.core.format.ParseException] so the UI can explain what happened.
 */
object Logs {
    private const val TAG = "LightReader"
    private const val ENABLED = true

    fun d(message: String) {
        if (ENABLED) Log.d(TAG, message)
    }

    fun w(message: String, throwable: Throwable? = null) {
        Log.w(TAG, message, throwable)
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
    }
}
