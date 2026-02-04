package com.example.lightningsearch.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.lightningsearch.R
import com.example.lightningsearch.ui.components.FileItem
import com.example.lightningsearch.ui.components.SearchBar
import com.example.lightningsearch.ui.components.StatusBar
import java.io.File

@Composable
fun SearchScreen(
    onRequestPermission: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Scaffold(
        bottomBar = {
            StatusBar(
                resultCount = state.resultCount,
                searchTimeMs = state.searchTimeMs,
                totalIndexed = state.totalIndexed
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            SearchBar(query = state.query, onQueryChange = viewModel::onQueryChange)

            if (state.hasPermission && !state.isIndexing && state.results.isNotEmpty()) {
                SortBar(currentSort = state.sortMode, onSortChange = viewModel::setSortMode)
            }

            when {
                !state.hasPermission -> PermissionRequest(onRequestPermission)
                state.isIndexing -> IndexingProgress(state.indexProgress, state.indexCurrentPath)
                state.query.isNotEmpty() && state.results.isEmpty() && !state.isSearching -> EmptyResults()
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(items = state.results, key = { it.path }) { file ->
                            FileItem(file = file, onClick = {
                                try {
                                    val fileObj = File(file.path)
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fileObj)
                                    val mime = if (file.is_directory) "resource/folder" else getMimeType(file.extension)
                                    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, mime)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    })
                                } catch (e: ActivityNotFoundException) {
                                    Toast.makeText(context, "没有找到打开此文件的应用", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "无法打开文件", Toast.LENGTH_SHORT).show()
                                }
                            })
                            Divider(modifier = Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SortBar(currentSort: SortMode, onSortChange: (SortMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("排序", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            Row(modifier = Modifier.clickable { expanded = true }, verticalAlignment = Alignment.CenterVertically) {
                Text(getSortLabel(currentSort), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                SortMode.values().forEach { mode ->
                    DropdownMenuItem(text = { Text(getSortLabel(mode)) }, onClick = { onSortChange(mode); expanded = false })
                }
            }
        }
    }
}

private fun getSortLabel(mode: SortMode) = when (mode) {
    SortMode.NAME_ASC -> "名称 ↑"; SortMode.NAME_DESC -> "名称 ↓"
    SortMode.SIZE_ASC -> "大小 ↑"; SortMode.SIZE_DESC -> "大小 ↓"
    SortMode.DATE_ASC -> "日期 ↑"; SortMode.DATE_DESC -> "日期 ↓"
}

@Composable
private fun PermissionRequest(onRequestPermission: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.permission_required), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onRequestPermission, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.grant_permission)) }
        }
    }
}

@Composable
private fun SimpleLoadingIndicator(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val transition = rememberInfiniteTransition(label = "loading")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "rotation")
    Canvas(modifier = modifier.size(48.dp)) {
        drawArc(color.copy(alpha = 0.3f), 0f, 360f, false, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
        drawArc(color, angle, 90f, false, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun IndexingProgress(progress: Int, currentPath: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SimpleLoadingIndicator()
            Text(stringResource(R.string.indexing), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
            Text("已索引 $progress 个文件", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            Text(currentPath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, start = 32.dp, end = 32.dp).fillMaxWidth(), maxLines = 1)
        }
    }
}

@Composable
private fun EmptyResults() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(R.string.no_results), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun getMimeType(ext: String?) = when (ext?.lowercase()) {
    "jpg", "jpeg", "png", "gif", "webp", "bmp" -> "image/*"
    "mp4", "mkv", "avi", "mov", "wmv", "flv" -> "video/*"
    "mp3", "wav", "flac", "aac", "ogg", "m4a" -> "audio/*"
    "pdf" -> "application/pdf"
    "doc", "docx" -> "application/msword"
    "xls", "xlsx" -> "application/vnd.ms-excel"
    "ppt", "pptx" -> "application/vnd.ms-powerpoint"
    "txt", "log" -> "text/plain"
    "zip", "rar", "7z" -> "application/zip"
    "apk" -> "application/vnd.android.package-archive"
    else -> "*/*"
}
