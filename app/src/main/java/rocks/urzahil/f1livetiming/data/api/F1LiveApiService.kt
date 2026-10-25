package rocks.urzahil.f1livetiming.data.api

import rocks.urzahil.f1livetiming.data.model.FiaDocumentsResponse
import rocks.urzahil.f1livetiming.data.model.LiveStatusResponse
import rocks.urzahil.f1livetiming.data.model.LiveWeatherResponse
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for self-hosted julesr0y/f1-livetiming-api backend
 */
interface F1LiveApiService {

    @GET("api/status")
    suspend fun getStatus(): LiveStatusResponse

    @GET("api/session")
    suspend fun getSessionRaw(): ResponseBody

    @GET("api/leaderboard")
    suspend fun getLeaderboardRaw(): ResponseBody

    @GET("api/standings")
    suspend fun getStandingsRaw(): ResponseBody

    @GET("api/standings/{driverNumber}")
    suspend fun getDriverTelemetryRaw(@Path("driverNumber") driverNumber: String): ResponseBody

    @GET("api/tyres")
    suspend fun getTyresRaw(): ResponseBody

    @GET("api/weather")
    suspend fun getWeatherRaw(): ResponseBody

    @GET("api/race-control")
    suspend fun getRaceControlRaw(): ResponseBody

    @GET("api/lap-count")
    suspend fun getLapCountRaw(): ResponseBody

    @GET("api/documents")
    suspend fun getFiaDocuments(@Query("year") year: Int, @Query("round") round: Int): FiaDocumentsResponse
}
