package com.lightreader.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lightreader.AppContainer
import com.lightreader.ui.library.LibraryScreen
import com.lightreader.ui.reader.ReaderScreen
import com.lightreader.ui.settings.SettingsScreen

private object Routes {
    const val LIBRARY = "library"
    const val READER = "reader"
    const val SETTINGS = "settings"
}

@Composable
fun AppNavHost(
    container: AppContainer,
    incomingUris: List<Uri>,
    onIncomingConsumed: () -> Unit,
) {
    val navController = rememberNavController()

    // Books opened from a file manager ("open with") or shared into the app.
    //
    // The import deliberately runs on the application scope, not in the composition: consuming the
    // incoming list changes this effect's key, and a composition scoped coroutine would then be
    // cancelled in the middle of parsing - which is exactly what used to make "open with" fail with
    // LeftCompositionCancellationException.
    LaunchedEffect(incomingUris) {
        if (incomingUris.isEmpty()) return@LaunchedEffect
        val uris = incomingUris.toList()
        onIncomingConsumed()
        container.appScope.launch {
            val result = runCatching { container.libraryRepository.import(uris) }.getOrNull()
            // A book that is already on the shelf counts as "opened" too: the file manager should
            // never feel like nothing happened just because the import was a duplicate.
            val opened = uris.size == 1 || (result?.imported ?: 0) > 0
            if (opened) {
                val target = uris.firstNotNullOfOrNull { uri ->
                    container.libraryRepository.findByUri(uri.toString())
                }
                if (target != null) {
                    withContext(Dispatchers.Main) {
                        navController.navigate("${Routes.READER}/${target.id}")
                    }
                }
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                container = container,
                onOpenBook = { bookId -> navController.navigate("${Routes.READER}/$bookId") },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            route = "${Routes.READER}/{bookId}",
            arguments = listOf(navArgument("bookId") { type = NavType.LongType }),
        ) { entry ->
            val bookId = entry.arguments?.getLong("bookId") ?: return@composable
            ReaderScreen(
                bookId = bookId,
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
