package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.api.NetworkClient
import com.example.data.repository.F1LiveRepository
import com.example.data.repository.ServerSettingsRepository
import com.example.ui.theme.TeamColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.compose.ui.graphics.Color
import com.example.data.model.getAllSessions

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun liveServicesAreReusedForEquivalentBackendUrls() {
    val first = NetworkClient.createLiveService("http://EXAMPLE.com:3000/")
    val second = NetworkClient.createLiveService("http://example.com:3000")
    org.junit.Assert.assertSame(first, second)
    assertEquals("http://example.com:3000/", NetworkClient.normalizeBaseUrl(" HTTP://EXAMPLE.COM:3000/// "))
  }

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("F1 Live Timing", appName)
  }

  @Test
  fun `server settings sanitizes url`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = ServerSettingsRepository(context)
    repo.updateBackendUrl("192.168.1.50:3000")
    assertEquals("http://192.168.1.50:3000", repo.backendUrl.value)

    repo.updateBackendUrl("https://f1.mydomain.com")
    assertEquals("https://f1.mydomain.com", repo.backendUrl.value)
  }

  @Test
  fun `empty session json reports no active session`() {
    val repo = F1LiveRepository()
    val session = repo.parseSessionJson("{\"Session\":{}}")
    assertFalse(session.isLive)
    assertEquals("NO ACTIVE SESSION", session.status)
  }

  @Test
  fun `empty standings does not invent empty leader`() {
    val repo = F1LiveRepository()
    val standings = repo.parseStandingsJson("{\"Leaderboard\":[],\"Lines\":{}}", emptyMap())
    assertTrue("Standings must be empty when no session is streaming", standings.isEmpty())

    // Even if an empty line object exists without driver info, it must be ignored
    val dummyStandings = repo.parseStandingsJson("{\"Leaderboard\":[\"1\"],\"Lines\":{\"1\":{}}}", emptyMap())
    assertTrue("Empty driver dictionary must not create ghost leader", dummyStandings.isEmpty())
  }

  @Test
  fun `team colors helper extracts and infers constructor colors from drivers at runtime`() {
    // 1. Register driver color extracted from API at runtime
    val ferrariRed = TeamColors.parseHex("ED1131")!!
    TeamColors.registerColor("Ferrari", ferrariRed)
    TeamColors.registerColor("16", ferrariRed) // Leclerc
    TeamColors.registerColor("LEC", ferrariRed)

    // Inferred constructor color matches driver's team color from API
    assertEquals(ferrariRed, TeamColors.getTeamColor("ferrari"))
    assertEquals(ferrariRed, TeamColors.getTeamColor("Scuderia Ferrari"))
    assertEquals(ferrariRed, TeamColors.getTeamColor("LEC"))

    // 2. Register McLaren Papaya from API at runtime
    val mclarenPapaya = TeamColors.parseHex("F47600")!!
    TeamColors.registerColor("McLaren", mclarenPapaya)
    TeamColors.registerColor("NOR", mclarenPapaya)
    assertEquals(mclarenPapaya, TeamColors.getTeamColor("mclaren"))
    assertEquals(mclarenPapaya, TeamColors.getTeamColor("NOR"))

    // 3. Any unregistered constructor dynamically derives a non-null distinct color without hardcoding
    val unknown = TeamColors.getTeamColor("new_entry_motorsport_2027")
    assertTrue(unknown.alpha > 0f)
  }

  @Test
  fun `country code helper maps nationalities accurately`() {
    assertEquals("NED", com.example.ui.theme.CountryCodeHelper.getCountryCode("Dutch"))
    assertEquals("GBR", com.example.ui.theme.CountryCodeHelper.getCountryCode("British"))
    assertEquals("MON", com.example.ui.theme.CountryCodeHelper.getCountryCode("Monegasque"))
    assertEquals("ESP", com.example.ui.theme.CountryCodeHelper.getCountryCode("Spanish"))
    assertEquals("ITA", com.example.ui.theme.CountryCodeHelper.getCountryCode("Italian"))
    assertEquals("AUS", com.example.ui.theme.CountryCodeHelper.getCountryCode("Australian"))
    assertEquals("GER", com.example.ui.theme.CountryCodeHelper.getCountryCode("German"))
    assertEquals("BRA", com.example.ui.theme.CountryCodeHelper.getCountryCode("Brazilian"))
    assertEquals("ARG", com.example.ui.theme.CountryCodeHelper.getCountryCode("Argentine"))
    assertEquals("JPN", com.example.ui.theme.CountryCodeHelper.getCountryCode("Japanese"))
    assertEquals("USA", com.example.ui.theme.CountryCodeHelper.getCountryCode("American"))
    assertEquals("MEX", com.example.ui.theme.CountryCodeHelper.getCountryCode("Mexican"))
    assertEquals("CAN", com.example.ui.theme.CountryCodeHelper.getCountryCode("Canadian"))
    org.junit.Assert.assertNull(com.example.ui.theme.CountryCodeHelper.getCountryCode(null))
    org.junit.Assert.assertNull(com.example.ui.theme.CountryCodeHelper.getCountryCode("null"))
    org.junit.Assert.assertNull(com.example.ui.theme.CountryCodeHelper.getCountryCode(""))
    org.junit.Assert.assertNull(com.example.ui.theme.CountryCodeHelper.getCountryCode("---"))
  }

  @Test
  fun `weekend sessions extraction handles standard and sprint weekends`() {
    val standardRace = com.example.data.model.SeasonRaceItem(
      round = "1",
      raceName = "Australian Grand Prix",
      date = "2026-03-08",
      time = "04:00:00Z",
      firstPractice = com.example.data.model.SessionSchedule("2026-03-06", "01:30:00Z"),
      secondPractice = com.example.data.model.SessionSchedule("2026-03-06", "05:00:00Z"),
      thirdPractice = com.example.data.model.SessionSchedule("2026-03-07", "01:30:00Z"),
      qualifying = com.example.data.model.SessionSchedule("2026-03-07", "05:00:00Z")
    )

    val standardSessions = standardRace.getAllSessions()
    assertEquals(5, standardSessions.size)
    assertEquals(com.example.data.model.WeekendSessionType.FP1, standardSessions[0].type)
    assertEquals(com.example.data.model.WeekendSessionType.FP2, standardSessions[1].type)
    assertEquals(com.example.data.model.WeekendSessionType.FP3, standardSessions[2].type)
    assertEquals(com.example.data.model.WeekendSessionType.QUALIFYING, standardSessions[3].type)
    assertEquals(com.example.data.model.WeekendSessionType.RACE, standardSessions[4].type)

    val sprintRace = com.example.data.model.SeasonRaceItem(
      round = "2",
      raceName = "Chinese Grand Prix",
      date = "2026-03-15",
      time = "07:00:00Z",
      firstPractice = com.example.data.model.SessionSchedule("2026-03-13", "03:30:00Z"),
      sprintQualifying = com.example.data.model.SessionSchedule("2026-03-13", "07:30:00Z"),
      sprint = com.example.data.model.SessionSchedule("2026-03-14", "03:00:00Z"),
      qualifying = com.example.data.model.SessionSchedule("2026-03-14", "07:00:00Z")
    )

    val sprintSessions = sprintRace.getAllSessions()
    assertEquals(5, sprintSessions.size)
    assertEquals(com.example.data.model.WeekendSessionType.FP1, sprintSessions[0].type)
    assertEquals(com.example.data.model.WeekendSessionType.SPRINT_QUALIFYING, sprintSessions[1].type)
    assertEquals(com.example.data.model.WeekendSessionType.SPRINT, sprintSessions[2].type)
    assertEquals(com.example.data.model.WeekendSessionType.QUALIFYING, sprintSessions[3].type)
    assertEquals(com.example.data.model.WeekendSessionType.RACE, sprintSessions[4].type)
  }

  @Test
  fun `lap time parser accepts valid formats and rejects placeholders`() {
    val repo = F1LiveRepository()
    assertEquals(83.456, repo.parseLapTimeToSeconds("1:23.456")!!, 0.0001)
    assertEquals(59.9, repo.parseLapTimeToSeconds("59.900")!!, 0.0001)
    assertEquals(3723.5, repo.parseLapTimeToSeconds("1:02:03.5")!!, 0.0001)
    org.junit.Assert.assertNull(repo.parseLapTimeToSeconds("--"))
    org.junit.Assert.assertNull(repo.parseLapTimeToSeconds(""))
    org.junit.Assert.assertNull(repo.parseLapTimeToSeconds("NO TIME"))
    org.junit.Assert.assertNull(repo.parseLapTimeToSeconds("NaN"))
    org.junit.Assert.assertNull(repo.parseLapTimeToSeconds("1e3"))
  }

  @Test
  fun `standings keep retired cars last, blank sectors as placeholders and flag fastest lap once`() {
    val repo = F1LiveRepository()
    val raw = """
      [
        {"RacingNumber":"11","Tla":"PER","Position":"1","BestLapTime":{"Value":"1:21.000"}},
        {"RacingNumber":"1","Tla":"VER","Position":"11","BestLapTime":{"Value":"1:20.000"},
         "Sectors":[{"Value":""},{"Value":"30.100"}]},
        {"RacingNumber":"44","Tla":"HAM","Position":"11","Retired":true}
      ]
    """.trimIndent()

    val timings = repo.parseStandingsJson(raw)

    // Car 1 in P11 and car 11 in P1 must have distinct identities (used as list keys).
    assertEquals(timings.size, timings.map { it.racingNumber }.toSet().size)
    assertEquals(listOf("PER", "VER", "HAM"), timings.take(3).map { it.driverCode })
    val ver = timings.first { it.racingNumber == "1" }
    assertEquals("--", ver.sector1)
    assertEquals("30.100", ver.sector2)
    assertEquals("--", ver.sector3)
    assertEquals(listOf("VER"), timings.filter { it.isOverallBestLap }.map { it.driverCode })
  }

  @Test
  fun `practice and qualifying gaps fall back to time diff fields`() {
    val repo = F1LiveRepository()
    val raw = """
      [
        {"RacingNumber":"4","Tla":"NOR","Position":"1","TimeDiffToFastest":"","TimeDiffToPositionAhead":""},
        {"RacingNumber":"81","Tla":"PIA","Position":"2","TimeDiffToFastest":"+0.215","TimeDiffToPositionAhead":"+0.215"},
        {"RacingNumber":"16","Tla":"LEC","Position":"3",
         "Stats":[{"TimeDiffToFastest":"+0.400","TimeDiffToPositionAhead":"+0.100"},
                  {"TimeDiffToFastest":"+0.312","TimeDiffToPositionAhead":"+0.097"},
                  {"TimeDiffToFastest":"","TimeDiffToPositionAhead":""}]},
        {"RacingNumber":"44","Tla":"HAM","Position":"4","GapToLeader":"+5.1",
         "IntervalToPositionAhead":{"Value":"+1.2"},"TimeDiffToFastest":"+9.9"}
      ]
    """.trimIndent()

    val byCode = repo.parseStandingsJson(raw).associateBy { it.driverCode }

    assertEquals("LEADER", byCode.getValue("NOR").gapToLeader)
    assertEquals("+0.215", byCode.getValue("PIA").gapToLeader)
    assertEquals("+0.215", byCode.getValue("PIA").intervalToAhead)
    // Q3 not yet set: use the latest part that has a value (Q2).
    assertEquals("+0.312", byCode.getValue("LEC").gapToLeader)
    assertEquals("+0.097", byCode.getValue("LEC").intervalToAhead)
    // Race fields still take precedence.
    assertEquals("+5.1", byCode.getValue("HAM").gapToLeader)
    assertEquals("+1.2", byCode.getValue("HAM").intervalToAhead)
  }

  @Test
  fun `stints and sectors delivered as index-keyed objects are parsed`() {
    val repo = F1LiveRepository()
    // Shape served by the backend during a live session: lists merged from deltas as {"0": {...}}.
    val raw = """
      [
        {"RacingNumber":"81","Tla":"PIA","Position":"2",
         "Stints":{"0":{"Compound":"MEDIUM","New":"true","TotalLaps":4}},
         "Sectors":{"0":{"Value":"25.101"},"2":{"Value":"40.250"}}},
        {"RacingNumber":"23","Tla":"ALB","Position":"19",
         "Stints":{"0":{"TotalLaps":1},"1":{"Compound":"HARD","New":"false","TotalLaps":1}}}
      ]
    """.trimIndent()

    val byCode = repo.parseStandingsJson(raw).associateBy { it.driverCode }

    val pia = byCode.getValue("PIA")
    assertEquals("MEDIUM", pia.currentCompound)
    assertEquals(4, pia.currentCompoundAge)
    assertEquals(true, pia.isCurrentCompoundNew)
    assertEquals("25.101", pia.sector1)
    assertEquals("--", pia.sector2)
    assertEquals("40.250", pia.sector3)

    val alb = byCode.getValue("ALB")
    assertEquals(listOf("UNKNOWN", "HARD"), alb.stintsHistory.map { it.compound })
    assertEquals("HARD", alb.currentCompound)
  }

  @Test
  fun `race control keeps the latest five messages newest first`() {
    val repo = F1LiveRepository()
    val messages = (1..7).joinToString(",") { i ->
      """{"Utc":"2026-10-23T17:4$i:00","Category":"Flag","Flag":"YELLOW","Message":"MSG $i"}"""
    }
    val raw = """{"RaceControl":[$messages,{"Utc":"2026-10-23T17:50:00","Category":"Other","Message":""}]}"""

    val parsed = repo.parseRaceControlJson(raw)

    assertEquals(listOf("MSG 7", "MSG 6", "MSG 5", "MSG 4", "MSG 3"), parsed.map { it.message })
    assertEquals("YELLOW", parsed.first().flag)
    // Times are converted from UTC to the device zone; only the shape is stable across machines.
    assert(Regex("""\d{2}:\d{2}:\d{2}""").matches(parsed.first().time))
    assertEquals(emptyList<Any>(), repo.parseRaceControlJson("""{"RaceControl":[]}"""))
  }

  @Test
  fun `session clock is parsed from the session response and counts down only while running`() {
    val repo = F1LiveRepository()
    val raw = """
      {"Session":{"Meeting":{"Name":"United States Grand Prix"},"Type":"Practice","Name":"Practice 1",
        "SessionStatus":"Started"},
       "ExtrapolatedClock":{"Utc":"2026-10-23T17:30:00.250Z","Remaining":"01:00:00","Extrapolating":true}}
    """.trimIndent()

    val clock = repo.parseSessionJson(raw).clock!!
    assertEquals(3_600_000L, clock.remainingMillis)
    assertEquals(true, clock.extrapolating)
    // 10.25 minutes after 17:30:00.250 UTC, 49:45 remain.
    assertEquals(2_985_000L, clock.remainingAt(clock.utcMillis + 615_000L))
    // Never negative once time is up.
    assertEquals(0L, clock.remainingAt(clock.utcMillis + 4_000_000L))

    val stopped = clock.copy(extrapolating = false)
    assertEquals(3_600_000L, stopped.remainingAt(clock.utcMillis + 615_000L))

    // Older backends without the clock, and malformed values, give no clock rather than a wrong one.
    assertEquals(null, repo.parseSessionJson("""{"Session":{"Name":"Practice 1","SessionStatus":"Started"}}""").clock)
    assertEquals(null, repo.parseSessionClock(org.json.JSONObject("""{"Utc":"2026-10-23T17:30:00Z","Remaining":"--"}""")))
  }
}
