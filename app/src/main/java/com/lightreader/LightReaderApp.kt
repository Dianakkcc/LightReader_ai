package com.lightreader

import android.app.Application

class LightReaderApp : Application() {

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        com.lightreader.core.util.AppStrings.initialize(this)
        instance = this
    }

    companion object {
        private var instance: LightReaderApp? = null

        fun containerOrNull(): AppContainer? = instance?.container
    }
}
