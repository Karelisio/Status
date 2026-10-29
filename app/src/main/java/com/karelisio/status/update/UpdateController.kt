package com.karelisio.status.update

import com.karelisio.status.AppGraph
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: GhRelease) : UpdateState
    data class Downloading(val release: GhRelease, val progress: Float) : UpdateState
    data class Installing(val release: GhRelease) : UpdateState
    data class Failed(val message: String, val release: GhRelease? = null) : UpdateState
}

/** Drives the update bottom sheet: check → download (with progress) → PackageInstaller. */
object UpdateController {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var job: Job? = null
    private val repo by lazy { UpdateRepository(AppGraph.app, AppGraph.settings) }

    /** At launch: at most every 6 h, silently, and never re-proposing a skipped version. */
    fun checkIfDue() {
        if (job?.isActive == true) return
        job = AppGraph.scope.launch {
            if (!repo.isCheckDue()) return@launch
            val release = repo.check().getOrNull() ?: return@launch
            if (repo.skipped() == release.version) return@launch
            _state.value = UpdateState.Available(release)
        }
    }

    /** Manual check from the About screen. */
    fun checkNow() {
        if (job?.isActive == true) return
        _state.value = UpdateState.Checking
        job = AppGraph.scope.launch {
            repo.check()
                .onSuccess { _state.value = it?.let { r -> UpdateState.Available(r) } ?: UpdateState.UpToDate }
                .onFailure { _state.value = UpdateState.Failed(it.message ?: "Vérification impossible") }
        }
    }

    fun download(release: GhRelease) {
        val asset = release.apk ?: return
        job?.cancel()
        _state.value = UpdateState.Downloading(release, 0f)
        job = AppGraph.scope.launch {
            runCatching {
                val file = repo.download(asset) { p -> _state.value = UpdateState.Downloading(release, p) }
                _state.value = UpdateState.Installing(release)
                UpdateInstaller.install(AppGraph.app, file)
            }.onFailure { _state.value = UpdateState.Failed(it.message ?: "Téléchargement impossible", release) }
        }
    }

    fun skip(release: GhRelease) {
        AppGraph.scope.launch { repo.skip(release.version) }
        dismiss()
    }

    fun dismiss() {
        job?.cancel()
        _state.value = UpdateState.Idle
    }

    fun onInstalled() { _state.value = UpdateState.Idle }

    fun onInstallFailed(message: String) {
        val release = (_state.value as? UpdateState.Installing)?.release
        _state.value = UpdateState.Failed(message, release)
    }
}
