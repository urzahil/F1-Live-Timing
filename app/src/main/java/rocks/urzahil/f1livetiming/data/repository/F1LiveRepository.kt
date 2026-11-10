package rocks.urzahil.f1livetiming.data.repository

import android.util.Log
import rocks.urzahil.f1livetiming.data.api.NetworkClient
import rocks.urzahil.f1livetiming.data.model.LiveSnapshotResponse
import rocks.urzahil.f1livetiming.ui.theme.TeamColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/** The backend predates GET /api/live and has to be rebuilt to serve this app. */
class BackendOutdatedException : Exception("The backend does not serve /api/live")

/** The backend's /api/live uses a newer schema than this app can display. */
class BackendTooNewException(val schemaVersion: Int) : Exception("Unsupported live schema $schemaVersion")

/**
 * Reads the live timing snapshot from the backend. All interpretation of the F1 feed happens there
 * (docker/live/live-snapshot.js); this only fetches the reply and checks that the app can display it.
 */
class F1LiveRepository {

    private companion object {
        const val TAG = "F1LiveRepository"
    }

    suspend fun fetchLiveSnapshot(baseUrl: String): Result<LiveSnapshotResponse> = withContext(Dispatchers.IO) {
        try {
            val snapshot = NetworkClient.createLiveService(baseUrl).getLive()
            if (snapshot.schemaVersion > LiveSnapshotResponse.SUPPORTED_SCHEMA_VERSION) {
                throw BackendTooNewException(snapshot.schemaVersion)
            }
            registerTeamColours(snapshot)
            Result.success(snapshot)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val mapped = if (error is HttpException && error.code() == 404) BackendOutdatedException() else error
            // Log exception types only; never log URLs, payloads, or secrets.
            Log.w(TAG, "Live snapshot request failed (${mapped.javaClass.simpleName})")
            Result.failure(mapped)
        }
    }

    /** Lets every screen colour a driver or team the way the live feed does. */
    private fun registerTeamColours(snapshot: LiveSnapshotResponse) {
        for (driver in snapshot.drivers) {
            val colour = driver.teamColorHex
            if (colour.isBlank()) continue
            if (driver.teamName.isNotBlank()) TeamColors.registerHexColor(driver.teamName, colour)
            if (driver.racingNumber.isNotBlank()) TeamColors.registerHexColor(driver.racingNumber, colour)
            // "DRV" is the backend's placeholder for a driver without a code.
            if (driver.driverCode.isNotBlank() && driver.driverCode != "DRV") TeamColors.registerHexColor(driver.driverCode, colour)
        }
    }
}
