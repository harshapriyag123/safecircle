package com.harshapriya.safecircle.ui.shared

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.harshapriya.safecircle.data.SafetyRepository
import com.harshapriya.safecircle.domain.SafetyEngine
import com.harshapriya.safecircle.model.SafetySession
import com.harshapriya.safecircle.model.SafetySnapshot
import com.harshapriya.safecircle.model.SessionMode

class SafetyViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = SafetyRepository(application)
    private val _session = MutableLiveData<SafetySession?>(repo.currentSession())
    val session: LiveData<SafetySession?> = _session

    private val _snapshot = MutableLiveData(SafetyEngine.evaluate(_session.value))
    val snapshot: LiveData<SafetySnapshot> = _snapshot

    fun start(mode: SessionMode) = set(repo.startSession(mode))
    fun checkIn() = set(repo.checkIn())
    fun markSafe() = set(repo.markSafe())
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
