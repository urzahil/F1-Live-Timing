package rocks.urzahil.f1livetiming.data.api

import rocks.urzahil.f1livetiming.data.model.FiaDocumentsResponse
import rocks.urzahil.f1livetiming.data.model.LiveSnapshotResponse
import rocks.urzahil.f1livetiming.data.model.LiveStatusResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Retrofit interface for the self-hosted f1-livetiming-api backend (docker/)
 */
interface F1LiveApiService {

    @GET("api/status")
    suspend fun getStatus(): LiveStatusResponse

    /** The whole live timing screen in one display-ready reply; 404 on backends older than patch 09. */
    @GET("api/live")
    suspend fun getLive(): LiveSnapshotResponse

    @GET("api/documents")
    suspend fun getFiaDocuments(@Query("year") year: Int, @Query("round") round: Int): FiaDocumentsResponse
}
