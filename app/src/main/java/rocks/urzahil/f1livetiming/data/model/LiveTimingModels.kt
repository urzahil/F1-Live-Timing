package rocks.urzahil.f1livetiming.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Reply of the self-hosted f1-livetiming-api backend's GET /api/status. The live timing endpoints are
 * parsed from raw JSON in F1LiveRepository, since their shape varies between backend versions.
 */
@JsonClass(generateAdapter = true)
data class LiveStatusResponse(
    @Json(name = "status") val status: String? = null,
    @Json(name = "service") val service: String? = null,
    @Json(name = "isConnected") val isConnected: Boolean? = null,
    @Json(name = "sessionActive") val sessionActive: Boolean? = null,
    @Json(name = "live") val live: Boolean? = null,
    @Json(name = "lastMessageAt") val lastMessageAt: String? = null,
    @Json(name = "uptimeSeconds") val uptimeSeconds: Long? = null,
    @Json(name = "message") val message: String? = null
)
