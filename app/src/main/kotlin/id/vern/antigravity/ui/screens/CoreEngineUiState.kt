package id.vern.antigravity.ui.screens

/** Lightweight UI state for the core engine CLI update card. */
data class CoreEngineUiState(
    val isChecking: Boolean = false,
    val isUpdateAvailable: Boolean = false,
    val latestVersion: String? = null
)
