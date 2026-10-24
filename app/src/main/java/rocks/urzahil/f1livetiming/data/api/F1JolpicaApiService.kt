package rocks.urzahil.f1livetiming.data.api

import rocks.urzahil.f1livetiming.data.model.ConstructorStandingsRoot
import rocks.urzahil.f1livetiming.data.model.DriverStandingsRoot
import rocks.urzahil.f1livetiming.data.model.SeasonCalendarRoot
import rocks.urzahil.f1livetiming.data.model.SessionResultsRoot
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Public REST API for official current season Championship and Calendar data
 */
interface F1JolpicaApiService {

    @GET("current/driverStandings.json")
    suspend fun getCurrentDriverStandings(): DriverStandingsRoot

    @GET("current/constructorStandings.json")
    suspend fun getCurrentConstructorStandings(): ConstructorStandingsRoot

    @GET("current.json")
    suspend fun getCurrentCalendar(): SeasonCalendarRoot

    @GET("current/{round}/results.json")
    suspend fun getRaceResults(@Path("round") round: String): SessionResultsRoot

    @GET("current/{round}/qualifying.json")
    suspend fun getQualifyingResults(@Path("round") round: String): SessionResultsRoot

    @GET("current/{round}/sprint.json")
    suspend fun getSprintResults(@Path("round") round: String): SessionResultsRoot
}
