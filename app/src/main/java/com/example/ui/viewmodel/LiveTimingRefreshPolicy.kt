package com.example.ui.viewmodel

/**
 * Centralizes the live polling cadence and refresh eligibility rules so they can be unit-tested
 * independently from Android lifecycle and repository/network details.
 */
internal class LiveTimingRefreshPolicy(
    val activePollIntervalMillis: Long = 5_000L,
    val idlePollIntervalMillis: Long = 60_000L,
    val weatherRefreshIntervalMillis: Long = 60_000L,
    val dataTtlMillis: Long = 120_000L
) {
    fun pollIntervalMillis(sessionIsLive: Boolean): Long =
        if (sessionIsLive) activePollIntervalMillis else idlePollIntervalMillis

    fun shouldFetchWeather(now: Long, lastFetch: Long, force: Boolean): Boolean =
        force || now - lastFetch >= weatherRefreshIntervalMillis

    fun shouldFetchTimings(
        now: Long,
        lastFetch: Long,
        force: Boolean,
        sessionIsLive: Boolean,
        showLatestSession: Boolean,
        hasSessionIdentity: Boolean,
        justCompleted: Boolean,
        sessionFinished: Boolean,
        sessionKey: String,
        finalFetchedSessionKey: String?
    ): Boolean {
        if (force || sessionIsLive) return true
        if (showLatestSession && hasSessionIdentity &&
            (finalFetchedSessionKey != sessionKey || now - lastFetch >= dataTtlMillis)) return true
        if (justCompleted && finalFetchedSessionKey != sessionKey) return true
        if (sessionFinished && hasSessionIdentity &&
            (finalFetchedSessionKey != sessionKey || now - lastFetch >= dataTtlMillis)) return true
        return hasSessionIdentity && now - lastFetch >= dataTtlMillis &&
            finalFetchedSessionKey != sessionKey
    }
}
