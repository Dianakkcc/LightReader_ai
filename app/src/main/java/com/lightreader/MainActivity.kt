package com.lightreader

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lightreader.ui.AppNavHost
import com.lightreader.ui.theme.LightReaderTheme
import com.lightreader.core.util.Logs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Single activity host.
 *
 * It also accepts books from the rest of the system: "open with" (ACTION_VIEW) and sharing
 * (ACTION_SEND / ACTION_SEND_MULTIPLE) both land here, get imported and opened directly.
 */
class MainActivity : ComponentActivity() {

    private val incoming = MutableStateFlow<List<Uri>>(emptyList())
    // Reading/layout preferences do not change the app theme. Observing them here made every
    // slider write invalidate the navigation host and the whole reading screen as well.
    private val chromeSettings by lazy {
        container.settingsStore.settings.map {
            it.copy(
                reader = com.lightreader.core.model.ReaderSettings(),
                library = com.lightreader.core.model.LibrarySettings(),
            )
        }.distinctUntilChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        requestFastestRefreshRate()
        handleIntent(intent)

        setContent {
            val density = LocalDensity.current
            val uris by incoming.collectAsState()
            val settings by chromeSettings.collectAsState(
                initial = com.lightreader.core.model.AppSettings(),
            )
            val darkTheme = when (settings.themeMode) {
                com.lightreader.core.model.AppThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                com.lightreader.core.model.AppThemeMode.LIGHT -> false
                com.lightreader.core.model.AppThemeMode.DARK -> true
            }
            LightReaderTheme(
                palette = settings.palette,
                background = settings.background,
                style = settings.style,
                darkTheme = darkTheme,
                systemFontScale = density.fontScale,
            ) {
                AppNavHost(
                    container = container,
                    incomingUris = uris,
                    onIncomingConsumed = { incoming.value = emptyList() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private val container: AppContainer
        get() = (application as LightReaderApp).container

    /**
     * Asks the window for the screen's fastest refresh mode.
     *
     * Several phones (MIUI / HyperOS in particular) keep an app at 60 Hz unless it asks for more
     * while the screen runs at 90/120 Hz. That is invisible inside the WebView - the page scrolls in
     * Blink's own compositor, which follows the display - but every Compose animation and scroll runs
     * on the app's frame clock, so a 60 Hz cap looks exactly like "every other frame is dropped,
     * very steadily" when scrolling a reader panel.
     */
    @Suppress("DEPRECATION")
    private fun requestFastestRefreshRate() {
        val display: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            windowManager.defaultDisplay
        }
        val modes = runCatching { display?.supportedModes }.getOrNull().orEmpty()
        val fastest = modes.maxByOrNull { it.refreshRate } ?: return
        Logs.d(
            "显示刷新率：支持 " + modes.joinToString { "%.0fHz".format(it.refreshRate) } +
                "，请求 ${"%.0f".format(fastest.refreshRate)}Hz",
        )
        window.attributes = window.attributes.apply {
            // Only a refresh-rate hint: preferredDisplayModeId would also switch the resolution.
            preferredRefreshRate = fastest.refreshRate
        }
    }

    @Suppress("DEPRECATION")
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(
                intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri,
            )

            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Uri>(
                Intent.EXTRA_STREAM,
            ) ?: emptyList()

            else -> emptyList()
        }
        if (uris.isNotEmpty()) {
            incoming.value = uris
        }
    }
}
