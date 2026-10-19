package com.lightreader

import android.content.Context
import com.lightreader.core.data.BookImporter
import com.lightreader.core.data.BuiltinFonts
import com.lightreader.core.data.BookWorkspace
import com.lightreader.core.data.CoverStore
import com.lightreader.core.data.DataTransferRepository
import com.lightreader.core.data.LibraryRepository
import com.lightreader.core.data.ReadingRepository
import com.lightreader.core.data.SettingsStore
import com.lightreader.core.data.db.AppDatabase
import com.lightreader.core.format.ParserFactory
import com.lightreader.core.format.ParserRegistry
import com.lightreader.core.model.Book
import com.lightreader.core.model.OpenedDocument
import com.lightreader.core.util.ImageLoader
import com.lightreader.core.util.BitmapCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Hand written dependency container.
 *
 * A DI framework would add build complexity and reflection for no benefit at this size; every
 * dependency here is a plain singleton created once at application start.
 */
class AppContainer(
    context: Context,
    /** Tests can skip copying the bundled fonts into private storage. */
    installBuiltinFonts: Boolean = true,
) {

    val applicationContext: Context = context.applicationContext

    val workspace: BookWorkspace = BookWorkspace(applicationContext)

    val database: AppDatabase = AppDatabase.create(applicationContext)

    val settingsStore: SettingsStore = SettingsStore(applicationContext)

    val parserRegistry: ParserRegistry = ParserFactory.createRegistry()

    val imageLoader: ImageLoader = ImageLoader(applicationContext)

    /** Rendered PDF / comic pages; bounded by bytes, evicted by the framework when needed. */
    val pageCache: BitmapCache = BitmapCache(64L * 1024 * 1024)

    /** Outlives a single screen, used for progress writes that must not be cancelled. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // Fonts shipped inside the APK are copied into private storage once, in the background, so
        // the first launch is not blocked by ~40 MB of font data.
        if (installBuiltinFonts) {
            appScope.launch {
                BuiltinFonts.installIfNeeded(
                    context = applicationContext,
                    workspace = workspace,
                    fontDao = database.fontDao(),
                    settingsStore = settingsStore,
                )
            }
        }
    }

    val coverStore: CoverStore = CoverStore(workspace)

    private val bookImporter: BookImporter = BookImporter(
        context = applicationContext,
        registry = parserRegistry,
        bookDao = database.bookDao(),
        coverStore = coverStore,
    )

    val libraryRepository: LibraryRepository = LibraryRepository(
        context = applicationContext,
        bookDao = database.bookDao(),
        progressDao = database.progressDao(),
        bookmarkDao = database.bookmarkDao(),
        workspace = workspace,
        coverStore = coverStore,
        importer = bookImporter,
    )

    val readingRepository: ReadingRepository = ReadingRepository(
        context = applicationContext,
        database = database,
        workspace = workspace,
        settingsStore = settingsStore,
    )

    val dataTransferRepository: DataTransferRepository = DataTransferRepository(
        context = applicationContext,
        database = database,
        settingsStore = settingsStore,
    )

    /** Opens a book for reading; the caller owns the returned document and must close it. */
    suspend fun openDocument(book: Book): OpenedDocument {
        val source = libraryRepository.sourceOf(book)
        return parserRegistry.open(applicationContext, source, book.format).first
    }

    fun clearCaches() {
        imageLoader.clear()
        pageCache.clear()
        workspace.clearCaches()
    }
}
