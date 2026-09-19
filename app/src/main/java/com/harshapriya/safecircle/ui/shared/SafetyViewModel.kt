package com.harshapriya.safecircle.ui.shared

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.harshapriya.safecircle.data.SafetyRepository
import com.harshapriya.safecircle.domain.SafetyEngine
import com.harshapriya.safecircle.model.SafetySession
import com.harshapriya.safecircle.model.SafetySnapshot
import com.harshapriya.safecircle.model.SessionMode
import com.harshapriya.safecircle.sync.ResolvedSessionSync
import kotlinx.coroutines.launch

enum class ResolutionSyncState { IDLE, SYNCING, SYNCED, RETRYING }

class SafetyViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = SafetyRepository(application)
    private val _session = MutableLiveData<SafetySession?>(repo.currentSession())
    val session: LiveData<SafetySession?> = _session

    private val _snapshot = MutableLiveData(SafetyEngine.evaluate(_session.value))
    val snapshot: LiveData<SafetySnapshot> = _snapshot

    private val _resolutionSync = MutableLiveData(ResolutionSyncState.IDLE)
    val resolutionSync: LiveData<ResolutionSyncState> = _resolutionSync

    fun start(mode: SessionMode) = set(repo.startSession(mode))
    fun start(mode: SessionMode, durationMinutes: Int, destinationLabel: String?) =
        set(repo.startSession(mode, durationMinutes, destinationLabel))
    fun checkIn() = set(repo.checkIn())
    fun markSafe() {
        val resolved = repo.markSafe()
        if (resolved?.resolved == true) _resolutionSync.value = ResolutionSyncState.SYNCING
        set(resolved)
        if (resolved?.resolved == true) {
            viewModelScope.launch {
                // WorkManager remains the durable retry path. This immediate authenticated
                // push lets Guardian Live observe resolution during the live judge flow.
                _resolutionSync.value = runCatching {
                    ResolvedSessionSync(getApplication<Application>()).push(resolved)
                }.fold(
                    onSuccess = { ResolutionSyncState.SYNCED },
                    onFailure = { ResolutionSyncState.RETRYING }
                )
            }
        }
    }
    fun simulateConcern() = set(repo.simulateConcern())
    fun extendEta(minutes: Int) = set(repo.updateEta(minutes))
    fun refresh() = set(repo.currentSession())
    fun guardians() = repo.guardians()
    fun lastLocationLabel() = repo.lastLocationLabel()

    private fun set(value: SafetySession?) {
        _session.value = value
        _snapshot.value = SafetyEngine.evaluate(value)
    }
}
