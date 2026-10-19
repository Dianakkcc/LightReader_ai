package com.lightreader.core.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import java.util.UUID

/** A real DataStore with a lifetime matching one Robolectric sandbox, including on Windows. */
class TestPreferenceStore(context: Context) : AutoCloseable {
    private val job = SupervisorJob()
    private val store = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(job + Dispatchers.IO),
    ) { context.preferencesDataStoreFile("test-${UUID.randomUUID()}") }
    val settings = SettingsStore(context, store)
    override fun close() = runBlocking { job.cancelAndJoin() }
}
