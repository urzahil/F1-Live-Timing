package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ServerSettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("f1_live_timing_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_BACKEND_URL = "backend_url"
        const val DEFAULT_BACKEND_URL = "https://f1api.urzahil.rocks"
        const val KEY_SHOW_LATEST_SESSION = "show_latest_session"
    }

    private val _backendUrl = MutableStateFlow(
        prefs.getString(KEY_BACKEND_URL, DEFAULT_BACKEND_URL) ?: DEFAULT_BACKEND_URL
    )
    val backendUrl: StateFlow<String> = _backendUrl.asStateFlow()

    private val _showLatestSession = MutableStateFlow(
        prefs.getBoolean(KEY_SHOW_LATEST_SESSION, false)
    )
    val showLatestSession: StateFlow<Boolean> = _showLatestSession.asStateFlow()

    fun updateBackendUrl(newUrl: String) {
        val trimmed = newUrl.trim()
        val sanitized = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
        prefs.edit().putString(KEY_BACKEND_URL, sanitized).apply()
        _backendUrl.value = sanitized
    }

    fun updateShowLatestSession(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_LATEST_SESSION, show).apply()
        _showLatestSession.value = show
    }

    fun resetToDefault() {
        updateBackendUrl(DEFAULT_BACKEND_URL)
        updateShowLatestSession(false)
    }
}
