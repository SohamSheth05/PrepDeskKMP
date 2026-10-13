package com.prepdesk.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prepdesk.domain.AppStatus
import com.prepdesk.domain.Library
import com.prepdesk.domain.ProgressSource
import com.prepdesk.domain.AppState
import kotlinx.coroutines.flow.*

/** The progress, question bank, and save status most screens render from. */
data class Snapshot(val state: AppState, val library: Library, val saving: Boolean)

fun snapshotOf(progress: ProgressSource, status: AppStatus): Flow<Snapshot> =
    combine(progress.state, progress.library, status.pending) { state, library, pending -> Snapshot(state, library, pending > 0) }

fun currentSnapshot(progress: ProgressSource, status: AppStatus) =
    Snapshot(progress.state.value, progress.library.value, status.pending.value > 0)

/** Exposes a flow as screen state that stays alive briefly across configuration changes. */
fun <T> ViewModel.stateOf(flow: Flow<T>, initial: T): StateFlow<T> =
    flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)
