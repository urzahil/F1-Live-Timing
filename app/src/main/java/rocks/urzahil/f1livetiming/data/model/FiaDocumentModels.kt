package rocks.urzahil.f1livetiming.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Reply of the backend's GET /api/documents?year=&round= (FIA decision documents of one grand prix). */
@JsonClass(generateAdapter = true)
data class FiaDocumentsResponse(
    @Json(name = "year") val year: Int? = null,
    @Json(name = "round") val round: Int? = null,
    @Json(name = "lastRefreshAt") val lastRefreshAt: String? = null,
    @Json(name = "documents") val documents: List<FiaDocument>? = null
)

@JsonClass(generateAdapter = true)
data class FiaDocument(
    @Json(name = "id") val id: Long? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "category") val category: List<String>? = null,
    @Json(name = "file_url") val fileUrl: String? = null,
    @Json(name = "published_at") val publishedAt: String? = null
)
