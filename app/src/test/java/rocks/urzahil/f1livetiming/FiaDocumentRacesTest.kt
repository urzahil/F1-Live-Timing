package rocks.urzahil.f1livetiming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import rocks.urzahil.f1livetiming.data.model.SeasonRaceItem
import rocks.urzahil.f1livetiming.data.model.SessionSchedule
import rocks.urzahil.f1livetiming.ui.viewmodel.FiaDocumentRaces
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class FiaDocumentRacesTest {

    private val utc = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun millis(value: String): Long = utc.parse(value)!!.time

    private fun race(round: Int, practiceDate: String?, raceDate: String) = SeasonRaceItem(
        round = round.toString(),
        date = raceDate,
        time = "19:00:00Z",
        firstPractice = practiceDate?.let { SessionSchedule(date = it, time = "17:30:00Z") }
    )

    private val season = listOf(
        race(1, "2026-03-06", "2026-03-08"),
        race(2, "2026-03-13", "2026-03-15"),
        race(3, "2026-03-27", "2026-03-29"),
        race(4, "2026-04-10", "2026-04-12")
    )

    @Test
    fun listsStartedWeekendsMostRecentFirst() {
        val started = FiaDocumentRaces.started(season, millis("2026-03-16T12:00:00"))
        assertEquals(listOf("2", "1"), started.map { it.round })
    }

    @Test
    fun aWeekendCountsAsStartedFromFirstPractice() {
        assertEquals(listOf("1"), FiaDocumentRaces.started(season, millis("2026-03-06T17:30:00")).map { it.round })
        assertEquals(emptyList<String>(), FiaDocumentRaces.started(season, millis("2026-03-06T17:29:00")).map { it.round })
    }

    @Test
    fun nextIsTheEarliestWeekendNotStartedYet() {
        assertEquals("3", FiaDocumentRaces.next(season, millis("2026-03-16T12:00:00"))?.round)
        assertEquals("1", FiaDocumentRaces.next(season, millis("2026-01-01T00:00:00"))?.round)
        assertNull(FiaDocumentRaces.next(season, millis("2026-04-11T00:00:00")))
    }

    @Test
    fun usesTheRaceDateWhenPracticeIsMissing() {
        val races = listOf(race(1, null, "2026-03-08"))
        assertEquals(1, FiaDocumentRaces.started(races, millis("2026-03-08T19:00:00")).size)
        assertEquals(0, FiaDocumentRaces.started(races, millis("2026-03-08T18:59:00")).size)
    }

    @Test
    fun skipsRacesWithoutAParseableDate() {
        val races = listOf(SeasonRaceItem(round = "1", date = "TBC"))
        assertEquals(0, FiaDocumentRaces.started(races, millis("2026-12-31T00:00:00")).size)
        assertNull(FiaDocumentRaces.next(races, millis("2026-01-01T00:00:00")))
    }
}
