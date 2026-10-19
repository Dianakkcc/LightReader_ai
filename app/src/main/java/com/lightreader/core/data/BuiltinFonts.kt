package com.lightreader.core.data

import android.content.Context
import com.lightreader.core.data.db.FontDao
import com.lightreader.core.data.db.FontEntity
import com.lightreader.core.util.Logs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Fonts shipped inside the APK.
 *
 * One serif, chosen on purpose: the bundled font set used to carry four faces (~51 MB of the APK),
 * and for Chinese book reading the serif is the one that matters - the system font already covers
 * the sans case, and importing another face is one tap away for anyone who wants one.
 *
 * The font is copied into app private storage on first launch so every code path - the WebView
 * renderer and the Compose preview - treats it exactly like an imported font.
 */
object BuiltinFonts {

    data class Spec(
        val assetName: String,
        val displayName: String,
        val description: String,
    )

    val ALL: List<Spec> = listOf(
        Spec("NotoSerifSC-Regular.otf", "思源宋体", "内置宋体衬线，接近纸书排印（SIL OFL 1.1）"),
    )

    /**
     * Installs every font that ships inside the APK: the file is copied into app private storage
     * and registered, so the whole font list is ready to use from the first launch - in the reader,
     * in the font manager and in the preview - without depending on the asset being unpacked later.
     *
     * Fonts the user deleted on purpose stay deleted (tombstone in [SettingsStore]).
     */
    suspend fun installIfNeeded(
        context: Context,
        workspace: BookWorkspace,
        fontDao: FontDao,
        settingsStore: SettingsStore,
    ) = withContext(Dispatchers.IO) {
        val removed = settingsStore.removedBuiltinFonts()
        // Faces that shipped with an older version and are no longer part of the app are dropped,
        // so the font list matches what is really bundled - and if one of them was the reading font,
        // the reader falls back to the system font instead of pointing at a missing file.
        val shipped = ALL.map { it.assetName }.toSet()
        val stale = fontDao.findAll().filter { it.isBuiltin && it.fileName !in shipped }
        if (stale.isNotEmpty()) {
            val staleIds = stale.map { it.id }.toSet()
            stale.forEach { font ->
                fontDao.delete(font.id)
                runCatching { File(font.path).delete() }
            }
            Logs.d("已移除不再内置的字体：${stale.joinToString { it.displayName }}")
            if (settingsStore.current().reader.fontId in staleIds) {
                settingsStore.updateReader {
                    it.copy(
                        fontId = null,
                        fontFamily = null,
                        fontPath = null,
                        fontDisplayName = null,
                    )
                }
            }
        }
        val installed = fontDao.findAll().map { it.fileName }.toSet()
        ALL.forEach { spec ->
            if (spec.assetName in installed || spec.assetName in removed) return@forEach
            runCatching {
                val target = File(workspace.fontsDir, "builtin-${spec.assetName}")
                if (!target.isFile || target.length() == 0L) {
                    context.assets.open("fonts/${spec.assetName}").use { input ->
                        target.outputStream().use { output ->
                            input.copyTo(output, DEFAULT_BUFFER_SIZE * 4)
                        }
                    }
                }
                fontDao.insert(
                    FontEntity(
                        displayName = spec.displayName,
                        fileName = spec.assetName,
                        path = target.absolutePath,
                        addedAt = System.currentTimeMillis(),
                        isDefault = false,
                        isBuiltin = true,
                    ),
                )
                Logs.d("已安装内置字体：${spec.displayName}")
            }.onFailure { Logs.w("内置字体安装失败：${spec.displayName}", it) }
        }
    }

    /** Copies one shipped font out of the APK the first time it is used. */
    suspend fun materialize(context: Context, assetName: String, target: File) {
        withContext(Dispatchers.IO) {
            if (target.isFile && target.length() > 0L) return@withContext
            runCatching {
                target.parentFile?.mkdirs()
                context.assets.open("fonts/$assetName").use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output, DEFAULT_BUFFER_SIZE * 4)
                    }
                }
                Logs.d("已启用内置字体：$assetName")
            }.onFailure { Logs.w("内置字体解包失败：$assetName", it) }
        }
    }
}
