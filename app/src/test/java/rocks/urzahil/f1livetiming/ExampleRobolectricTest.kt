package rocks.urzahil.f1livetiming

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import rocks.urzahil.f1livetiming.data.api.NetworkClient
import rocks.urzahil.f1livetiming.data.repository.F1ChampionshipRepository
import rocks.urzahil.f1livetiming.data.repository.ServerSettingsRepository
import rocks.urzahil.f1livetiming.ui.theme.TeamColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.compose.ui.graphics.Color
import rocks.urzahil.f1livetiming.data.model.getAllSessions

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
  fun `stored OpenF1 driver list supplies headshots when OpenF1 is unreachable`() {
    val file = java.io.File.createTempFile("openf1_drivers", ".json").apply { deleteOnExit() }
    file.writeText(
      """[{"name_acronym":"ver","driver_number":1,"last_name":"Verstappen",""" +
        """"headshot_url":"https://media.formula1.com/ver.png","team_colour":"3671C6","team_name":"Red Bull Racing"}]"""
    )
    val metadata = F1ChampionshipRepository(file).loadStoredDriverMetadata()
    assertEquals("https://media.formula1.com/ver.png", metadata.headshots["VER"])
    assertEquals("https://media.formula1.com/ver.png", metadata.headshots["1"])
    assertEquals("https://media.formula1.com/ver.png", metadata.headshots["verstappen"])

    file.writeText("not json")
    assertTrue(F1ChampionshipRepository(file).loadStoredDriverMetadata().headshots.isEmpty())
    assertTrue(F1ChampionshipRepository(null).loadStoredDriverMetadata().headshots.isEmpty())
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
    assertEquals("NED", rocks.urzahil.f1livetiming.ui.theme.CountryCodeHelper.getCountryCode("Dutch"))
    assertEquals("GBR", rocks.urzahil.f1livetiming.ui.theme.CountryCodeHelper.getCountryCode("British"))
    assertEquals("MON", rocks.urzahil.f1livetiming.ui.theme.CountryCodeHelper.getCountryCode("Monegasque"))
    assertEquals("ESP", rocks.urzahil.f1livetiming.ui.theme.CountryCodeHelper.getCountryCode("Spanish"))
    assertEquals("ITA", rocks.urzahil.f1livetiming.ui.theme.CountryCodeHelper.getCountryCode("Italian"))
    assertEquals("AUS", rocks.urzahil.f1livetiming.ui.theme.CountryCodeHelper.getCountryCode("Australian"))
    assertEquals("GER", rocks.urzahil.f1livetiming.ui.theme.CountryCodeHelper.getCountryCode("German"))
    assertEquals("BRA", rocks.urzahil.f1livetiming.ui.theme.CountryCodeHelper.getCountryCode("Brazilian"))
    assertEquals("ARG", rocks.urzahil.f1livetiming.ui.theme.CountryCodeHelper.getCountryCode("Argentine"))
    assertEquals("JPN", rocks.urzahil.f1livetiming.ui.theme.CountryCodeHelper.getCountryCode("Japanese"))
    assertEquals("USA", rocks.urzahil.f1livetiming.ui.theme.CountryCodeHelper.getCountryCode("American"))
    assertEquals("MEX", rocks.urzahil.f1livetiming.ui.theme.CountryCodeHelper.getCountryCode("Mexican"))
    assertEquals("CAN", rocks.urzahil.f1livetiming.ui.theme.CountryCodeHelper.getCountryCode("Canadian"))
    org.junit.Assert.assertNull(rocks.urzahil.f1livetiming.ui.theme.CountryCodeHelper.getCountryCode(null))
    org.junit.Assert.assertNull(rocks.urzahil.f1livetiming.ui.theme.CountryCodeHelper.getCountryCode("null"))
    org.junit.Assert.assertNull(rocks.urzahil.f1livetiming.ui.theme.CountryCodeHelper.getCountryCode(""))
    org.junit.Assert.assertNull(rocks.urzahil.f1livetiming.ui.theme.CountryCodeHelper.getCountryCode("---"))
  }

  @Test
  fun `weekend sessions extraction handles standard and sprint weekends`() {
    val standardRace = rocks.urzahil.f1livetiming.data.model.SeasonRaceItem(
      round = "1",
      raceName = "Australian Grand Prix",
      date = "2026-03-08",
      time = "04:00:00Z",
      firstPractice = rocks.urzahil.f1livetiming.data.model.SessionSchedule("2026-03-06", "01:30:00Z"),
      secondPractice = rocks.urzahil.f1livetiming.data.model.SessionSchedule("2026-03-06", "05:00:00Z"),
      thirdPractice = rocks.urzahil.f1livetiming.data.model.SessionSchedule("2026-03-07", "01:30:00Z"),
      qualifying = rocks.urzahil.f1livetiming.data.model.SessionSchedule("2026-03-07", "05:00:00Z")
    )

    val standardSessions = standardRace.getAllSessions()
    assertEquals(5, standardSessions.size)
    assertEquals(rocks.urzahil.f1livetiming.data.model.WeekendSessionType.FP1, standardSessions[0].type)
    assertEquals(rocks.urzahil.f1livetiming.data.model.WeekendSessionType.FP2, standardSessions[1].type)
    assertEquals(rocks.urzahil.f1livetiming.data.model.WeekendSessionType.FP3, standardSessions[2].type)
    assertEquals(rocks.urzahil.f1livetiming.data.model.WeekendSessionType.QUALIFYING, standardSessions[3].type)
    assertEquals(rocks.urzahil.f1livetiming.data.model.WeekendSessionType.RACE, standardSessions[4].type)

    val sprintRace = rocks.urzahil.f1livetiming.data.model.SeasonRaceItem(
      round = "2",
      raceName = "Chinese Grand Prix",
      date = "2026-03-15",
      time = "07:00:00Z",
      firstPractice = rocks.urzahil.f1livetiming.data.model.SessionSchedule("2026-03-13", "03:30:00Z"),
      sprintQualifying = rocks.urzahil.f1livetiming.data.model.SessionSchedule("2026-03-13", "07:30:00Z"),
      sprint = rocks.urzahil.f1livetiming.data.model.SessionSchedule("2026-03-14", "03:00:00Z"),
      qualifying = rocks.urzahil.f1livetiming.data.model.SessionSchedule("2026-03-14", "07:00:00Z")
    )

    val sprintSessions = sprintRace.getAllSessions()
    assertEquals(5, sprintSessions.size)
    assertEquals(rocks.urzahil.f1livetiming.data.model.WeekendSessionType.FP1, sprintSessions[0].type)
    assertEquals(rocks.urzahil.f1livetiming.data.model.WeekendSessionType.SPRINT_QUALIFYING, sprintSessions[1].type)
    assertEquals(rocks.urzahil.f1livetiming.data.model.WeekendSessionType.SPRINT, sprintSessions[2].type)
    assertEquals(rocks.urzahil.f1livetiming.data.model.WeekendSessionType.QUALIFYING, sprintSessions[3].type)
    assertEquals(rocks.urzahil.f1livetiming.data.model.WeekendSessionType.RACE, sprintSessions[4].type)
  }
}
