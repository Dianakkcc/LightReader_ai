package com.lightreader.ui.settings

import com.lightreader.R
import com.lightreader.core.util.AppStrings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 离线查看随 APK 分发的许可；只在打开文件时后台读取，正文按段落布局。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<Pair<String, String>?>(null) }
    val index by produceState<List<Pair<String, String>>>(emptyList(), context) {
        value = withContext(Dispatchers.IO) {
            context.assets.open("licenses/INDEX.txt").bufferedReader().use { reader ->
                reader.readLines().mapNotNull { line ->
                    val parts = line.split('\t', limit = 2)
                    if (parts.size == 2) parts[0] to parts[1] else null
                }
            }
        }
    }
    val fileName = selected?.first
    val paragraphs by produceState<List<String>?>(null, fileName) {
        value = null
        if (fileName != null) {
            value = withContext(Dispatchers.IO) {
                context.assets.open("licenses/$fileName").bufferedReader().use { reader ->
                    reader.readText().replace("\r\n", "\n").split(Regex("\n[ \\t]*\n"))
                }
            }
        }
    }
    val goBack = { if (selected != null) selected = null else onBack() }
    BackHandler(onBack = goBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selected?.second ?: AppStrings.get(R.string.ui_licenses_and_notices_162)) },
                navigationIcon = {
                    IconButton(onClick = goBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = AppStrings.get(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (fileName == null) {
                item {
                    Text(
                        AppStrings.get(R.string.ui_free_software_distribution_libraries_fonts_and_c_164),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                itemsIndexed(index, key = { _, entry -> entry.first }) { _, entry ->
                    ListItem(
                        headlineContent = { Text(entry.second) },
                        modifier = Modifier.clickable { selected = entry },
                    )
                }
            } else {
                val content = paragraphs
                if (content == null) {
                    item { Text(AppStrings.get(R.string.ui_loading_165), modifier = Modifier.padding(16.dp)) }
                } else {
                    itemsIndexed(content) { _, paragraph ->
                        Text(
                            paragraph,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
