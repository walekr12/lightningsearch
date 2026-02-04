package com.example.lightningsearch.ui

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

enum class SortMode {
    NAME_ASC, NAME_DESC,
    SIZE_ASC, SIZE_DESC,
    DATE_ASC, DATE_DESC
}

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
    val hasPermission: Boolean = false,
    val sortMode: SortMode = SortMode.NAME_ASC
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
        // Load existing count from database
        viewModelScope.launch {
            val count = fileRepository.getFileCount()
            _state.value = _state.value.copy(totalIndexed = count)
        }
    }

    fun setPermissionGranted(granted: Boolean) {
        _state.value = _state.value.copy(hasPermission = granted)
    }

    fun setSortMode(mode: SortMode) {
        _state.value = _state.value.copy(sortMode = mode)
        if (_state.value.results.isNotEmpty()) {
            _state.value = _state.value.copy(results = sortResults(_state.value.results, mode))
        }
    }

    private fun sortResults(results: List<FileEntity>, mode: SortMode): List<FileEntity> {
        return when (mode) {
            SortMode.NAME_ASC -> results.sortedWith(compareBy({ !it.is_directory }, { it.name_lower }))
            SortMode.NAME_DESC -> results.sortedWith(compareBy<FileEntity> { !it.is_directory }.thenByDescending { it.name_lower })
            SortMode.SIZE_ASC -> results.sortedWith(compareBy({ !it.is_directory }, { it.size }))
            SortMode.SIZE_DESC -> results.sortedWith(compareBy<FileEntity> { !it.is_directory }.thenByDescending { it.size })
            SortMode.DATE_ASC -> results.sortedWith(compareBy({ !it.is_directory }, { it.modified_time }))
            SortMode.DATE_DESC -> results.sortedWith(compareBy<FileEntity> { !it.is_directory }.thenByDescending { it.modified_time })
        }
    }

    fun onQueryChange(query: String) {
        _state.value = _state.value.copy(query = query)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(150)
            performSearch(query)
        }
    }

    private suspend fun performSearch(query: String) {
        if (query.isBlank()) {
            _state.value = _state.value.copy(results = emptyList(), resultCount = 0, searchTimeMs = 0)
            return
        }

        _state.value = _state.value.copy(isSearching = true)
        val startTime = System.currentTimeMillis()
        val results = fileRepository.search(query)
        val sorted = sortResults(results, _state.value.sortMode)
        val elapsed = System.currentTimeMillis() - startTime

        _state.value = _state.value.copy(
            results = sorted,
            resultCount = sorted.size,
            searchTimeMs = elapsed,
            isSearching = false
        )
    }

    fun checkAndStartIndexing(getStoragePaths: () -> List<String>) {
        if (indexingStarted) return

        viewModelScope.launch {
            val existingCount = fileRepository.getFileCount()
            if (existingCount > 0) {
                _state.value = _state.value.copy(totalIndexed = existingCount)
                return@launch
            }
            startIndexing(getStoragePaths())
        }
    }

    fun startIndexing(rootPaths: List<String>) {
        if (indexingStarted) return
        indexingStarted = true

        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(isIndexing = true, indexProgress = 0, indexCurrentPath = "")
            }

            try {
                val total = fileRepository.indexFiles(rootPaths) { indexed, current ->
                    _state.value = _state.value.copy(indexProgress = indexed, indexCurrentPath = current)
                }

                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(isIndexing = false, totalIndexed = total)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(isIndexing = false)
                }
            } finally {
                indexingStarted = false
            }
        }
    }
}
