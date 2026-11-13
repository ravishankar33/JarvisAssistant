package com.example.jarvis

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class JarvisUiState(
    val isRunning: Boolean = false,
    val statusText: String = "Idle",
    val lastHeard: String = "—"
)

object JarvisStateBus {
    private val _state = MutableStateFlow(JarvisUiState())
    val state: StateFlow<JarvisUiState> = _state.asStateFlow()

    fun update(
        isRunning: Boolean? = null,
        statusText: String? = null,
        lastHeard: String? = null
    ) {
        val current = _state.value
        _state.value = current.copy(
            isRunning = isRunning ?: current.isRunning,
            statusText = statusText ?: current.statusText,
            lastHeard = lastHeard ?: current.lastHeard
        )
    }
}
