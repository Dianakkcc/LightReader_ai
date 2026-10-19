package com.lightreader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import java.io.Closeable
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking

/** Clear and join collectors before Robolectric disposes their database and file handles. */
class TestViewModels : Closeable {
    private val store = ViewModelStore()
    private val models = mutableListOf<ViewModel>()
    fun <T : ViewModel> own(model: T): T {
        store.put(models.size.toString(), model)
        models += model
        return model
    }
    override fun close() {
        val jobs = models.mapNotNull { it.viewModelScope.coroutineContext[Job] }
        store.clear()
        runBlocking { jobs.forEach { it.join() } }
    }
}
