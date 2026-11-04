package rocks.urzahil.f1livetiming

import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import rocks.urzahil.f1livetiming.data.model.LiveSnapshotResponse
import rocks.urzahil.f1livetiming.data.model.ParsedDriverTiming
import rocks.urzahil.f1livetiming.data.model.ParsedSession
import rocks.urzahil.f1livetiming.data.model.TrackStatus
import rocks.urzahil.f1livetiming.data.model.TrackStatusAdapter
import rocks.urzahil.f1livetiming.data.repository.BackendOutdatedException
import rocks.urzahil.f1livetiming.data.repository.BackendTooNewException
import rocks.urzahil.f1livetiming.ui.viewmodel.LiveTimingCoordinator
import java.io.IOException

class LiveSnapshotTest {

    private val adapter = Moshi.Builder().add(TrackStatusAdapter).build().adapter(LiveSnapshotResponse::class.java)

    @Test
    fun `backend snapshot decodes into the display models`() {
        // Trimmed from a GET /api/live reply of the backend (docker/live/live-snapshot.js).
        val json = """
            {"schemaVersion":1,"generatedAt":1793785579306,
             "session":{"meetingName":"São Paulo Grand Prix","sessionName":"Race","sessionType":"Race","currentLap":20,
               "totalLaps":71,"status":"STARTED","isLive":true,"isTimed":false,"qualifyingPart":null,
               "clock":{"remainingMillis":5400000,"utcMillis":1794158400123,"extrapolating":true}},
             "trackStatus":"SAFETY_CAR",
             "weather":{"airTemp":"24.1 °C","trackTemp":"44.0 °C","humidity":"60.0 %","pressure":"925.1 hPa",
               "windSpeed":"1.4 m/s","windDirection":"120°","isRaining":false},
             "raceControl":[{"utcMillis":1794158280000,"category":"SafetyCar","flag":"","message":"SAFETY CAR DEPLOYED","scope":""},
               {"utcMillis":null,"category":"Other","flag":"","message":"NO TIME","scope":""}],
             "drivers":[{"position":"1","racingNumber":"4","driverCode":"NOR","fullName":"Lando NORRIS","teamName":"McLaren",
               "teamColorHex":"F47600","gapToLeader":"LAP 20","intervalToAhead":"LAP 20","bestLapTime":"1:12.300",
               "lastLapTime":"--","sector1":"--","sector2":"--","sector3":"--","inPit":false,"pitStops":1,"totalLaps":0,
               "isRetired":false,"isStopped":false,"currentCompound":"HARD","currentCompoundAge":3,"isCurrentCompoundNew":false,
               "stintsHistory":[{"stintNumber":1,"compound":"SOFT","totalLaps":10,"isNew":true},
                 {"stintNumber":2,"compound":"HARD","totalLaps":3,"isNew":false}],
               "isOverallBestLap":false,"isCatchingAhead":null,"isKnockedOut":false,"aFieldFromANewerBackend":42}]}
        """.trimIndent()

        val snapshot = adapter.fromJson(json)!!

        assertEquals(1, snapshot.schemaVersion)
        assertEquals("São Paulo Grand Prix", snapshot.session.meetingName)
        assertEquals(true, snapshot.session.isLive)
        assertNull(snapshot.session.qualifyingPart)
        assertEquals(5_400_000L, snapshot.session.clock!!.remainingAt(1794158400123L))
        assertEquals(TrackStatus.SAFETY_CAR, snapshot.trackStatus)
        assertEquals("24.1 °C", snapshot.weather.airTemp)
        assertEquals(listOf(1794158280000L, null), snapshot.raceControl.map { it.utcMillis })
        val nor = snapshot.drivers.single()
        assertEquals("NOR", nor.driverCode)
        assertNull(nor.isCatchingAhead)
        assertEquals(listOf("SOFT", "HARD"), nor.stintsHistory.map { it.compound })
    }

    @Test
    fun `missing fields take their defaults and an unknown track status reads as clear`() {
        val snapshot = adapter.fromJson("""{"schemaVersion":1,"trackStatus":"CHEQUERED","drivers":[{"racingNumber":"1"}]}""")!!
        assertEquals(TrackStatus.CLEAR, snapshot.trackStatus)
        assertEquals("NO ACTIVE SESSION", snapshot.session.status)
        assertEquals("--", snapshot.drivers.single().gapToLeader)
        assertEquals(emptyList<Any>(), snapshot.raceControl)
    }

    private fun live(session: ParsedSession, vararg codes: String) = LiveSnapshotResponse(
        schemaVersion = 1,
        session = session,
        drivers = codes.map { ParsedDriverTiming(racingNumber = it, driverCode = it) }
    )

    private val race = ParsedSession(meetingName = "Qatar Grand Prix", sessionName = "Race", status = "STARTED", isLive = true)

    @Test
    fun `failed refreshes keep the last snapshot and say why`() = runTest {
        var reply: Result<LiveSnapshotResponse> = Result.failure(IOException())
        val coordinator = LiveTimingCoordinator(fetchSnapshot = { reply })

        assertEquals(LiveTimingCoordinator.UNREACHABLE_MESSAGE, coordinator.refresh("http://backend").error)

        reply = Result.success(live(race, "VER", "NOR"))
        val good = coordinator.refresh("http://backend")
        assertNull(good.error)
        assertEquals(listOf("VER", "NOR"), good.timings.map { it.driverCode })

        reply = Result.failure(IOException())
        val stale = coordinator.refresh("http://backend")
        assertEquals(LiveTimingCoordinator.STALE_MESSAGE, stale.error)
        assertEquals(listOf("VER", "NOR"), stale.timings.map { it.driverCode })

        reply = Result.failure(BackendOutdatedException())
        assertEquals(LiveTimingCoordinator.OUTDATED_BACKEND_MESSAGE, coordinator.refresh("http://backend").error)
        reply = Result.failure(BackendTooNewException(2))
        assertEquals(LiveTimingCoordinator.NEWER_BACKEND_MESSAGE, coordinator.refresh("http://backend").error)
    }

    @Test
    fun `a restarted backend does not blank a running session until the data expires`() = runTest {
        var now = 1_000L
        var reply = Result.success(live(race, "VER"))
        val coordinator = LiveTimingCoordinator(fetchSnapshot = { reply }, clock = { now })
        coordinator.refresh("http://backend")

        reply = Result.success(live(ParsedSession()))
        now += 5_000L
        val kept = coordinator.refresh("http://backend")
        assertEquals("Race", kept.session.sessionName)
        assertEquals(LiveTimingCoordinator.STALE_MESSAGE, kept.error)

        now += 120_000L
        val cleared = coordinator.refresh("http://backend")
        assertEquals("", cleared.session.sessionName)
        assertEquals(emptyList<Any>(), cleared.timings)
        assertNull(cleared.error)
    }

    @Test
    fun `a new session replaces the previous one at once`() = runTest {
        var reply = Result.success(live(race, "VER"))
        val coordinator = LiveTimingCoordinator(fetchSnapshot = { reply })
        coordinator.refresh("http://backend")

        reply = Result.success(live(race.copy(sessionName = "Sprint"), "NOR"))
        val next = coordinator.refresh("http://backend")
        assertEquals("Sprint", next.session.sessionName)
        assertEquals(listOf("NOR"), next.timings.map { it.driverCode })
    }
}
