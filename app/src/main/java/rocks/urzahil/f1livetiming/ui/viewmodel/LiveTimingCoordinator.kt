package rocks.urzahil.f1livetiming.ui.viewmodel

import rocks.urzahil.f1livetiming.data.model.LiveSnapshotResponse
import rocks.urzahil.f1livetiming.data.model.ParsedDriverTiming
import rocks.urzahil.f1livetiming.data.model.ParsedRaceControlMessage
import rocks.urzahil.f1livetiming.data.model.ParsedSession
import rocks.urzahil.f1livetiming.data.model.ParsedWeather
import rocks.urzahil.f1livetiming.data.model.TrackStatus
import rocks.urzahil.f1livetiming.data.model.hasIdentity
import rocks.urzahil.f1livetiming.data.repository.BackendOutdatedException
import rocks.urzahil.f1livetiming.data.repository.BackendTooNewException
import rocks.urzahil.f1livetiming.data.repository.F1LiveRepository

internal data class LiveTimingSnapshot(
    val session: ParsedSession = ParsedSession(),
    val weather: ParsedWeather = ParsedWeather(),
    val timings: List<ParsedDriverTiming> = emptyList(),
    val raceControl: List<ParsedRaceControlMessage> = emptyList(),
    val trackStatus: TrackStatus = TrackStatus.CLEAR,
    val error: String? = null
)

/**
 * Fetches the backend's live snapshot, which already holds everything the screen shows. On failures it
 * keeps the last good snapshot and reports why, so the screen never blanks on a single missed refresh.
 */
internal class LiveTimingCoordinator(
    private val fetchSnapshot: suspend (String) -> Result<LiveSnapshotResponse> = F1LiveRepository()::fetchLiveSnapshot,
    private val policy: LiveTimingRefreshPolicy = LiveTimingRefreshPolicy(),
    private val clock: () -> Long = System::currentTimeMillis
) {
    private var snapshot = LiveTimingSnapshot()
    private var lastSuccessAt = 0L

    fun resetForBackendChange() {
        lastSuccessAt = 0L
        snapshot = snapshot.copy(error = null)
    }

    suspend fun refresh(baseUrl: String): LiveTimingSnapshot {
        val currentUrl = baseUrl.trim()
        if (currentUrl.isBlank()) return snapshot

        val now = clock()
        fetchSnapshot(currentUrl)
            .onSuccess { live ->
                // A backend that has just restarted answers with no session until the feed reconnects. A
                // session that was running a moment ago stays on screen, marked stale, rather than blanking.
                val previous = snapshot.session
                val emptyAfterRestart = !live.session.hasIdentity && previous.hasIdentity &&
                    now - lastSuccessAt < policy.dataTtlMillis &&
                    !previous.status.contains("FINISHED", ignoreCase = true)
                snapshot = if (emptyAfterRestart) {
                    snapshot.copy(error = STALE_MESSAGE)
                } else {
                    lastSuccessAt = now
                    LiveTimingSnapshot(
                        session = live.session,
                        weather = live.weather,
                        timings = live.drivers,
                        raceControl = live.raceControl,
                        trackStatus = live.trackStatus
                    )
                }
            }
            .onFailure { error ->
                val hasAnyData = snapshot.session.hasIdentity || snapshot.timings.isNotEmpty() ||
                    snapshot.weather != ParsedWeather()
                snapshot = snapshot.copy(
                    error = when {
                        error is BackendOutdatedException -> OUTDATED_BACKEND_MESSAGE
                        error is BackendTooNewException -> NEWER_BACKEND_MESSAGE
                        hasAnyData -> STALE_MESSAGE
                        else -> UNREACHABLE_MESSAGE
                    }
                )
            }
        return snapshot
    }

    companion object {
        const val STALE_MESSAGE = "Showing last successful data - the latest refresh was incomplete."
        const val UNREACHABLE_MESSAGE = "Unable to refresh live data. Check the backend connection."
        const val OUTDATED_BACKEND_MESSAGE =
            "The backend is out of date. Rebuild it (docker compose up -d --build) to show live timing."
        const val NEWER_BACKEND_MESSAGE = "The backend is newer than this app. Update the app to show live timing."
    }
}
