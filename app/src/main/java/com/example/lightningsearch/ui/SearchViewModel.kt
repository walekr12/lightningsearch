package com.example.lightningsearch.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lightningsearch.data.db.FileEntity
import com.example.lightningsearch.data.repository.FileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    init {
        viewModelScope.launch {
            fileRepository.getFileCountFlow().collect { count ->
                _state.value = _state.value.copy(totalIndexed = count)
            }
        }
    }

    fun setPermissionGranted(granted: Boolean) {
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

        val startTime = System.currentTimeMillis()
        val results = fileRepository.search(query)
        val elapsed = System.currentTimeMillis() - startTime

        _state.value = _state.value.copy(
            results = results,
            resultCount = results.size,
            searchTimeMs = elapsed,
            isSearching = false
        )
    }

    fun startIndexing(rootPaths: List<String>) {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isIndexing = true,
                indexProgress = 0,
                indexCurrentPath = ""
            )

            val total = fileRepository.indexFiles(rootPaths) { indexed, current ->
                _state.value = _state.value.copy(
                    indexProgress = indexed,
                    indexCurrentPath = current
                )
            }

            _state.value = _state.value.copy(
                isIndexing = false,
                totalIndexed = total
            )
        }
    }
}
