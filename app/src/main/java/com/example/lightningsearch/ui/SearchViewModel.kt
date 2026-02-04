package com.example.lightningsearch.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lightningsearch.data.db.FileEntity
import com.example.lightningsearch.data.repository.FileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "SearchViewModel"

data class SearchState(
    val query: String = "",
    val results: List<FileEntity> = emptyList(),
    val resultCount: Int = 0,
    val searchTimeMs: Long = 0,
    val isSearching: Boolean = false,
    val isIndexing: Boolean = false,
    val indexProgress: Int = 0,
    val indexCurrentPath: String = "",
    val totalIndexed: Int = 0,
    val hasPermission: Boolean = false
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val fileRepository: FileRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var indexingStarted = false

    init {
        Log.d(TAG, "SearchViewModel init")
        viewModelScope.launch {
            try {
                fileRepository.getFileCountFlow().collect { count ->
                    Log.d(TAG, "File count updated: $count")
                    _state.value = _state.value.copy(totalIndexed = count)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error collecting file count", e)
            }
        }
    }

    fun setPermissionGranted(granted: Boolean) {
        Log.d(TAG, "setPermissionGranted: $granted")
        _state.value = _state.value.copy(hasPermission = granted)
    }

    fun onQueryChange(query: String) {
        _state.value = _state.value.copy(query = query)

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(150) // Debounce
            performSearch(query)
        }
    }

    private suspend fun performSearch(query: String) {
        if (query.isBlank()) {
            _state.value = _state.value.copy(
                results = emptyList(),
                resultCount = 0,
                searchTimeMs = 0
            )
            return
        }

        _state.value = _state.value.copy(isSearching = true)

        try {
            val startTime = System.currentTimeMillis()
            val results = fileRepository.search(query)
            val elapsed = System.currentTimeMillis() - startTime

            _state.value = _state.value.copy(
                results = results,
                resultCount = results.size,
                searchTimeMs = elapsed,
                isSearching = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error searching", e)
            _state.value = _state.value.copy(isSearching = false)
        }
    }

    fun startIndexing(rootPaths: List<String>) {
        Log.d(TAG, "startIndexing called, indexingStarted=$indexingStarted")
        if (indexingStarted) {
            Log.d(TAG, "Already indexing, returning")
            return
        }
        indexingStarted = true

        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Indexing coroutine started")

            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(
                    isIndexing = true,
                    indexProgress = 0,
                    indexCurrentPath = ""
                )
            }

            try {
                Log.d(TAG, "Calling fileRepository.indexFiles")
                val total = fileRepository.indexFiles(rootPaths) { indexed, current ->
                    // Update on main thread - but don't launch new coroutine for each update
                    if (indexed % 1000 == 0) {
                        Log.d(TAG, "Indexed: $indexed, current: $current")
                    }
                    _state.value = _state.value.copy(
                        indexProgress = indexed,
                        indexCurrentPath = current
                    )
                }

                Log.d(TAG, "Indexing completed, total: $total")
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        isIndexing = false,
                        totalIndexed = total
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during indexing", e)
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        isIndexing = false
                    )
                }
            } finally {
                indexingStarted = false
                Log.d(TAG, "Indexing finally block, indexingStarted reset")
            }
        }
    }
}
