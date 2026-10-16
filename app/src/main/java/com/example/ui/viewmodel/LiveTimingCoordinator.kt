package com.example.ui.viewmodel

import com.example.data.repository.F1LiveRepository
import com.example.data.repository.ParsedDriverTiming
import com.example.data.repository.ParsedSession
import com.example.data.repository.ParsedWeather

internal data class LiveTimingSnapshot(
    val session: ParsedSession = ParsedSession(),
    val weather: ParsedWeather = ParsedWeather(),
    val timings: List<ParsedDriverTiming> = emptyList(),
    val status: String = "Waiting for the first refresh",
    val error: String? = null
)

/**
 * Coordinates live session, weather, and timing refreshes independently of UI state. It retains
 * the last good response on failures, avoids caching empty timing replies, and applies expiry and
 * session-transition rules through LiveTimingRefreshPolicy.
 */
internal class LiveTimingCoordinator(
    private val repository: F1LiveRepository = F1LiveRepository(),
    private val policy: LiveTimingRefreshPolicy = LiveTimingRefreshPolicy(),
    private val clock: () -> Long = System::currentTimeMillis
) {
    private var snapshot = LiveTimingSnapshot()
    private var lastWeatherFetchAt = 0L
    private var lastTimingsFetchAt = 0L
    private var lastSessionSuccessAt = 0L
    private var finalTimingFetchSessionKey: String? = null

    fun resetForBackendChange() {
        lastWeatherFetchAt = 0L
        lastTimingsFetchAt = 0L
        lastSessionSuccessAt = 0L
        finalTimingFetchSessionKey = null
        snapshot = snapshot.copy(status = "Backend changed — refreshing data", error = null)
    }

    suspend fun refresh(baseUrl: String, force: Boolean, showLatestSession: Boolean): LiveTimingSnapshot {
        val currentUrl = baseUrl.trim()
        if (currentUrl.isBlank()) {
            return snapshot.copy(status = "Backend URL is not configured")
        }

        val now = clock()
        var stale = false
        val previousSession = snapshot.session
        val sessionResult = repository.fetchSession(currentUrl)
        sessionResult.onSuccess { parsed ->
            val hasIdentity = parsed.meetingName.isNotBlank() || parsed.sessionName.isNotBlank()
            val hadIdentity = previousSession.meetingName.isNotBlank() || previousSession.sessionName.isNotBlank()
            val incompleteEmptyReply = !hasIdentity && hadIdentity &&
                now - lastSessionSuccessAt < policy.dataTtlMillis &&
                !previousSession.status.contains("FINISHED", ignoreCase = true)
            if (incompleteEmptyReply) {
                stale = true
            } else {
                if (sessionKey(parsed) != sessionKey(previousSession) && snapshot.timings.isNotEmpty()) {
                    // A session disappearing or changing must not leave the prior leaderboard
                    // attached to the next session, even when an empty session response arrives.
                    snapshot = snapshot.copy(timings = emptyList())
                    lastTimingsFetchAt = 0L
                    finalTimingFetchSessionKey = null
                }
                snapshot = snapshot.copy(session = parsed)
                lastSessionSuccessAt = now
            }
        }.onFailure { stale = true }

        val session = snapshot.session
        val hasIdentity = session.meetingName.isNotBlank() ||
            session.sessionName.isNotBlank() || session.sessionType.isNotBlank()
        val key = sessionKey(session)
        val justCompleted = previousSession.isLive && !session.isLive
        val finished = session.status.contains("FINISHED", ignoreCase = true)
        val fetchWeather = policy.shouldFetchWeather(now, lastWeatherFetchAt, force)
        val fetchTimings = policy.shouldFetchTimings(
            now = now,
            lastFetch = lastTimingsFetchAt,
            force = force,
            sessionIsLive = session.isLive,
            showLatestSession = showLatestSession,
            hasSessionIdentity = hasIdentity,
            justCompleted = justCompleted,
            sessionFinished = finished,
            sessionKey = key,
            finalFetchedSessionKey = finalTimingFetchSessionKey
        )

        if (fetchWeather) {
            repository.fetchWeather(currentUrl).onSuccess { weather ->
                snapshot = snapshot.copy(weather = weather)
                lastWeatherFetchAt = now
            }.onFailure { stale = true }
        }

        if (fetchTimings) {
            repository.fetchLiveTimings(currentUrl).onSuccess { timings ->
                if (timings.isNotEmpty()) {
                    snapshot = snapshot.copy(timings = timings)
                    lastTimingsFetchAt = now
                    if (!session.isLive) finalTimingFetchSessionKey = key
                } else if (snapshot.timings.isNotEmpty()) {
                    stale = true
                } else {
                    lastTimingsFetchAt = 0L
                }
            }.onFailure { stale = true }
        }

        val hasAnyData = hasIdentity || snapshot.timings.isNotEmpty() || snapshot.weather != ParsedWeather()
        val status = when {
            stale && hasAnyData -> "Showing last successful data — some information may be stale"
            stale -> "Unable to refresh live data"
            hasAnyData -> "Data updated"
            else -> "No active session"
        }
        val error = when {
            stale && hasAnyData -> "Showing last successful data - the latest refresh was incomplete."
            stale -> "Unable to refresh live data. Check the backend connection."
            else -> null
        }
        snapshot = snapshot.copy(status = status, error = error)
        return snapshot
    }

    private fun sessionKey(session: ParsedSession): String =
        listOf(session.meetingName.trim(), session.sessionName.trim(), session.sessionType.trim())
            .joinToString("|")
}
