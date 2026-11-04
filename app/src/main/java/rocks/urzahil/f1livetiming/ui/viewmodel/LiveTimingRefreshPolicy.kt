package rocks.urzahil.f1livetiming.ui.viewmodel

/**
 * Centralizes the live polling cadence so it can be unit-tested independently from Android lifecycle and
 * network details. Each poll fetches the backend's whole live snapshot in one request.
 */
internal class LiveTimingRefreshPolicy(
    val activePollIntervalMillis: Long = 5_000L,
    val idlePollIntervalMillis: Long = 60_000L,
    /** How long a session stays on screen while a restarted backend has not named it again yet. */
    val dataTtlMillis: Long = 120_000L
) {
    fun pollIntervalMillis(sessionIsLive: Boolean): Long =
        if (sessionIsLive) activePollIntervalMillis else idlePollIntervalMillis
}
