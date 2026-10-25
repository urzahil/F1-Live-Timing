package rocks.urzahil.f1livetiming

import rocks.urzahil.f1livetiming.data.model.SeasonRaceItem
import rocks.urzahil.f1livetiming.data.model.SessionSchedule
import rocks.urzahil.f1livetiming.ui.viewmodel.ChampionshipPredictor
import rocks.urzahil.f1livetiming.ui.viewmodel.RemainingSessions
import rocks.urzahil.f1livetiming.ui.viewmodel.StandingEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ChampionshipPredictorTest {

    private val utc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun millis(value: String): Long = utc.parse(value)!!.time

    private fun race(round: Int, date: String, sprintDate: String? = null) = SeasonRaceItem(
        round = round.toString(),
        date = date,
        time = "13:00:00Z",
        sprint = sprintDate?.let { SessionSchedule(date = it, time = "15:00:00Z") }
    )

    private val calendar = listOf(
        race(1, "2026-03-08"),
        race(2, "2026-03-15", sprintDate = "2026-03-14"),
        race(3, "2026-03-22"),
        race(4, "2026-03-29", sprintDate = "2026-03-28")
    )

    @Test
    fun countsEverySessionAfterTheStandingsRound() {
        val remaining = ChampionshipPredictor.remainingSessions(calendar, standingsRound = 1, nowMillis = millis("2026-03-10T00:00:00"))
        assertEquals(RemainingSessions(races = 3, sprints = 2), remaining)
        assertEquals(3 * 25 + 2 * 8, remaining.driverPoints)
        assertEquals(3 * 43 + 2 * 15, remaining.constructorPoints)
    }

    @Test
    fun countsTheGrandPrixAfterAScoredSprintOnTheSameWeekend() {
        // Saturday evening: the sprint is in the standings, so they now read round 2, but Sunday's race is not.
        val remaining = ChampionshipPredictor.remainingSessions(calendar, standingsRound = 2, nowMillis = millis("2026-03-14T20:00:00"))
        assertEquals(RemainingSessions(races = 3, sprints = 1), remaining)
    }

    @Test
    fun dropsTheStandingsRoundOnceItsGrandPrixHasStarted() {
        val remaining = ChampionshipPredictor.remainingSessions(calendar, standingsRound = 2, nowMillis = millis("2026-03-16T00:00:00"))
        assertEquals(RemainingSessions(races = 2, sprints = 1), remaining)
    }

    @Test
    fun countsTheWholeSeasonBeforeAnyStandingsExist() {
        val remaining = ChampionshipPredictor.remainingSessions(calendar, standingsRound = null, nowMillis = millis("2026-01-01T00:00:00"))
        assertEquals(RemainingSessions(races = 4, sprints = 2), remaining)
    }

    @Test
    fun reportsTheSeasonOverAfterTheFinalRound() {
        val remaining = ChampionshipPredictor.remainingSessions(calendar, standingsRound = 4, nowMillis = millis("2026-03-30T00:00:00"))
        assertTrue(remaining.isSeasonOver)
    }

    @Test
    fun keepsContendersWhoCanTieTheLeaderAndDropsThoseWhoCannot() {
        val contenders = ChampionshipPredictor.contenders(
            listOf(
                StandingEntry("b", "BBB", null, 300f),
                StandingEntry("a", "AAA", null, 350f),
                StandingEntry("c", "CCC", null, 299f)
            ),
            pointsAvailable = 50
        )
        assertEquals(listOf("a", "b", "c"), contenders.map { it.id })
        assertTrue(contenders[0].inContention)
        assertTrue(contenders[1].inContention) // 300 + 50 ties the leader; countback decides
        assertFalse(contenders[2].inContention)
        assertEquals(349f, contenders[2].maxPoints)
        assertNull(ChampionshipPredictor.clinchedChampion(contenders))
    }

    @Test
    fun namesTheChampionOnceNobodyElseCanReachThem() {
        val contenders = ChampionshipPredictor.contenders(
            listOf(StandingEntry("a", "AAA", null, 400f), StandingEntry("b", "BBB", null, 330f)),
            pointsAvailable = 33
        )
        assertEquals("a", ChampionshipPredictor.clinchedChampion(contenders)?.id)
    }
}
