package com.mlengine.app

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val isLoading: Boolean = false,
    val result: InferenceResult? = null,
    val metrics: SessionStats? = null,
    val error: String? = null,
    val serverOnline: Boolean = false,
    val selectedMode: String = "adaptive",
    val latencyBudgetMs: Long = 150L,
    val selectedImageUri: Uri? = null,
    val networkQuality: String = "GOOD",
    val networkLabel: String = "Good — no delay"
)

class MainViewModel : ViewModel() {

    private val apiClient = ApiClient()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        checkServer()
        refreshNetworkStatus()
    }

    fun checkServer() {
        viewModelScope.launch(Dispatchers.IO) {
            val online = apiClient.checkHealth()
            _uiState.value = _uiState.value.copy(serverOnline = online)
        }
    }

    fun setMode(mode: String) {
        _uiState.value = _uiState.value.copy(selectedMode = mode)
    }

    fun setLatencyBudget(ms: Long) {
        _uiState.value = _uiState.value.copy(latencyBudgetMs = ms)
    }

    fun setSelectedImage(uri: Uri?) {
        _uiState.value = _uiState.value.copy(selectedImageUri = uri, result = null, error = null)
    }

    fun setNetworkQuality(quality: String) {
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = apiClient.setNetworkQuality(quality)) {
                is ApiClient.Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        networkQuality = result.data.currentQuality,
                        networkLabel = result.data.label
                    )
                }
                is ApiClient.Result.Error -> {
                    _uiState.value = _uiState.value.copy(error = result.message)
                }
            }
        }
    }

    fun refreshNetworkStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = apiClient.getNetworkStatus()) {
                is ApiClient.Result.Success -> {
                    _uiState.value = _uiState.value.copy(
                        networkQuality = result.data.currentQuality,
                        networkLabel = result.data.label
                    )
                }
                is ApiClient.Result.Error -> {}
            }
        }
    }

    fun runInference(context: Context) {
        val uri = _uiState.value.selectedImageUri ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            try {
                val imageBytes = context.contentResolver
                    .openInputStream(uri)?.readBytes()
                    ?: run {
                        _uiState.value = _uiState.value.copy(isLoading = false, error = "Could not read image")
                        return@launch
                    }
                when (val result = apiClient.infer(
                    imageBytes = imageBytes,
                    mode = _uiState.value.selectedMode,
                    latencyBudgetMs = _uiState.value.latencyBudgetMs
                )) {
                    is ApiClient.Result.Success -> {
                        _uiState.value = _uiState.value.copy(isLoading = false, result = result.data, error = null)
                        refreshMetrics()
                    }
                    is ApiClient.Result.Error -> {
                        _uiState.value = _uiState.value.copy(isLoading = false, error = result.message)
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "Unknown error")
            }
        }
    }

    fun refreshMetrics() {
        viewModelScope.launch(Dispatchers.IO) {
            when (val result = apiClient.getMetrics()) {
                is ApiClient.Result.Success -> _uiState.value = _uiState.value.copy(metrics = result.data)
                is ApiClient.Result.Error -> {}
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
