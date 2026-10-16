# F1 Live Timing & Telemetry Dashboard

An Android application delivering real-time Formula 1 live timings, driver interval telemetry, stint tyre histories, track meteorological conditions, championship standings, and the current season calendar powered by a self-hosted `f1-livetiming-api` backend and supplementary F1 championship feeds.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following architectural and user experience decisions were confirmed through Phase 1 planning:

*   **Backend Connection**: Configurable in-app backend URL manager with an integrated latency/health tester, preloaded with emulator (`http://10.0.2.2:3000`), local network LAN, and custom server presets.
*   **Championship & Calendar Data**: Hybrid API architecture combining the self-hosted `f1-livetiming-api` for real-time SignalR live data (standings, gaps, tyres, weather, race control) with the free, official open F1 Ergast/Jolpica REST API for full current-season driver standings, constructor standings, and grand prix calendars.
*   **Live Update Rhythm**: 5-second polling interval during active sessions with a live pause/resume toggle, offline detector, and instantaneous manual refresh button.
*   **Visual Direction**: Formula 1 Pit-Wall Dark HUD with authentic Pirelli compound badges (Soft #E10600, Medium #FFD700, Hard #FFFFFF, Inter #39B54A, Wet #0070FF), dynamic event layouts for Race vs. Practice/Qualifying, sector delta tags, and team livery accent indicators.
*   **Docker Backend Artifact**: Self-contained `docker/Dockerfile`, `docker/docker-compose.yml`, and setup documentation to host `julesr0y/f1-livetiming-api` with zero configuration.

---

## 1. Overview & Core Concept

### What It Does
The F1 Live Timing app delivers a pit-wall engineering experience on Android devices. It connects to a self-hosted instance of `julesr0y/f1-livetiming-api` to monitor live Grand Prix sessions in real time. Depending on the active session type (Race, Practice, or Qualifying), the timing screen adapts dynamically:
*   **Race Mode**: Displays live track positions, gap to leader, interval to car ahead (gap to previous driver), pit stop count, current tyre compound & stint age, and retirement/in-pit flags.
*   **Practice / Qualifying Mode**: Displays best lap time, sector 1/2/3 timing deltas with purple (overall best) and green (personal best) indicators, and total laps completed.
*   **Driver Details & Tyre History**: Tapping any driver reveals a deep telemetry panel displaying their complete chronological tyre history (stint count, compound, laps on set, new vs. used), sector breakdown, and gap progression.
*   **Weather & Track Radar**: Real-time meteorological telemetry including track surface temperature, ambient air temperature, relative humidity, atmospheric pressure, wind speed/direction, and rainfall detection.
*   **Championship Standings**: Complete current-year Drivers' Championship and Constructors' Championship tables with points, wins, and team colors.
*   **Season Calendar**: Full Grand Prix schedule for the current year showing circuit names, dates, weekend formats (Sprint vs Standard), and countdown to the next race.

### Target Audience & Persona
Formula 1 fans, sim-racers, and motorsport enthusiasts seeking clean, second-screen timing telemetry without television broadcast delays or paywalls.

### Key Value
High-fidelity telemetry with zero invented or hardcoded numbers, complete tyre stint tracking, event-aware timing views, and frictionless Docker hosting.

---

## 2. User Experience & Visual Design

### Key User Flows
1.  **Launch & Connection Health**: The app initializes, checks connection to the configured backend URL, and shows a live status badge (`ONLINE`, `SESSION LIVE`, or `OFFLINE / NO ACTIVE SESSION`). If unconfigured or unreachable, a guided bottom sheet lets the user test and set their Docker server host.
2.  **Live Timing Feed (Dynamic Event View)**:
    *   *Header Bar*: Active session title (e.g. "FORMULA 1 MONACO GRAND PRIX - RACE"), lap progress counter (`Lap 42 / 78`), track status flag indicator (GREEN, YELLOW, SC, VSC, RED), and weather strip.
    *   *Leaderboard Table*: Driver rows featuring position, car number, team color bar, driver 3-letter acronym and full name, gap to leader, gap to previous driver (interval), current tyre compound badge with stint lap age, and pit stops.
    *   *Driver Expansion*: Tapping a driver opens a dialog/bottom sheet showing their complete tyre history (`Stint 1: MEDIUM (24 laps, New) -> Stint 2: HARD (18 laps, New)`), personal best lap time, and status.
3.  **Track Weather Modal / Panel**: Quick-access chip revealing live ambient temp, track temp, humidity percentage, wind speed/direction, and rain status sensor.
4.  **Standings Tab**: Switchable view for Drivers' Championship and Constructors' Championship, loaded dynamically for the current season.
5.  **Calendar Tab**: Chronological list of current season races with round number, Grand Prix name, circuit location, race date, and past results or upcoming countdown.
6.  **Settings / Docker Guide Modal**: Endpoint URL configuration, connection ping test button with HTTP status code and response latency, and embedded Docker run instructions.

### Visual Identity & Theme
*   **Aesthetic Direction**: F1 Pit-Wall Dark Telemetry — sleek, technical, high-contrast, professional.
*   **Color Palette**:
    *   *Background*: Deep Carbon (`#0B0D10`), Surface (`#14181F`), Card Container (`#1C222C`)
    *   *Primary / Accents*: F1 Speed Red (`#E10600`), Pure White (`#FFFFFF`), Muted Slate (`#8E9BAE`)
    *   *Tyre Compounds*: Soft (`#FF1801`), Medium (`#FFD800`), Hard (`#F0F0F0`), Intermediate (`#39B54A`), Wet (`#0070FF`)
    *   *Sector Times*: Overall Best (`#9B51E0` Purple), Personal Best (`#27AE60` Green), No Improvement (`#F2994A` Yellow)
    *   *Track Status*: Green Flag (`#2ECC71`), Yellow Flag (`#F1C40F`), Safety Car (`#E67E22`), Red Flag (`#E74C3C`)
*   **Typography**: Clean sans-serif with tabular figures (`FontWeight.Bold` and monospace numeric alignments for times and gaps so numbers do not jump during live updates).
*   **Interactive Motion**: Subtle pulse animation on the live polling indicator, smooth row transitions, and clear tactile ripple feedback.

---

## 3. Key Product Decisions & Trade-Offs

*   **Decision 1: Dynamic Telemetry Schema Parsing for Race vs. Practice/Qualifying**
    *   *Chosen Approach*: The API returns differing payloads depending on session type (`IntervalToPositionAhead` and `GapToLeader` during Race; `BestLapTime`, sector times `Sector1Time`, `Sector2Time`, `Sector3Time` during Practice and Qualifying). The data parser dynamically binds the appropriate columns based on the session payload.
    *   *Why*: Formula 1 timing rules change fundamentally between sessions. Displaying sector times during qualifying and delta gaps during races matches authentic broadcast graphics.
*   **Decision 2: Dual Data Provider Architecture (LiveTiming API + Season Jolpica Feed)**
    *   *Chosen Approach*: Real-time session data, driver lists, tyre stints, gaps, and weather use `julesr0y/f1-livetiming-api`. Season standings and the full race calendar use Jolpica/Ergast open public API.
    *   *Why*: The live timing SignalR server only streams data while an official session is currently taking place on track. The user specifically requested a calendar and standings tab; using the open championship API ensures full current-year data is always available even between race weekends, with zero hardcoding.
*   **Decision 3: Zero Mock Data Principle & Clear Connection Feedback**
    *   *Chosen Approach*: All numbers, names, and timings originate strictly from API endpoints. If the backend server is offline or no race is underway, the UI displays rich, helpful status states explaining the session lifecycle rather than generating mock numbers.
*   **Decision 4: Docker Artifact Packaging**
    *   *Chosen Approach*: Provide a dedicated Docker configuration (`Dockerfile` and `docker-compose.yml`) targeting `julesr0y/f1-livetiming-api` with automatic Node/TypeScript building and port exposure on `3000`.

---

## 4. Technical Architecture & Data Strategy

### System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                             Android Jetpack Compose UI                      │
├─────────────────────────────────────────────────────────────────────────────┤
│  TopAppBar: Session Name, Flag Status, Weather Pill, Connection & Settings  │
├─────────────────────────────────────────────────────────────────────────────┤
│  Tab Navigation: [ LIVE TIMING ]  |  [ STANDINGS ]  |  [ CALENDAR ]         │
├─────────────────────────────────────────────────────────────────────────────┤
│  [ LIVE TIMING TAB ]                                                        │
│  ├── Session Header: Lap Progress, Active Flags, Track & Air Temp           │
│  ├── Dynamic Leaderboard (Race: Gaps/Tyres | Practice/Quali: Best Lap/Sec)  │
│  └── Driver Stint Dialog (Full Tyre History: compound, laps, new/used)      │
│                                                                             │
│  [ STANDINGS TAB ]                                                          │
│  ├── Toggle: Drivers Championship / Constructors Championship               │
│  └── List: Position, Driver/Team, Points, Wins, Livery color                │
│                                                                             │
│  [ CALENDAR TAB ]                                                           │
│  └── Current Year Grand Prix list: Round, Circuit, Country, Date, Status    │
│                                                                             │
│  [ SETTINGS / DOCKER MODAL ]                                                │
│  └── Backend URL input, Ping Test, Port Guide, Docker run commands          │
└───────────────────────┬───────────────────────────────┬─────────────────────┘
                        │                               │
                        ▼                               ▼
       ┌─────────────────────────────────┐   ┌───────────────────────────────┐
       │     F1LiveTimingRepository      │   │    F1ChampionshipRepository   │
       └────────────────┬────────────────┘   └───────────────┬───────────────┘
                        │                                    │
                        ▼                                    ▼
       ┌─────────────────────────────────┐   ┌───────────────────────────────┐
       │      F1LiveApiService           │   │    F1JolpicaApiService        │
       │  (Retrofit2 + Moshi)            │   │  (Retrofit2 + Moshi)          │
       ├─────────────────────────────────┤   ├───────────────────────────────┤
       │  GET /api/status                │   │  GET /api/f1/current/driver   │
       │  GET /api/session               │   │      Standings.json           │
       │  GET /api/standings             │   │  GET /api/f1/current/construc │
       │  GET /api/tyres                 │   │      torStandings.json        │
       │  GET /api/weather               │   │  GET /api/f1/current.json     │
       └────────────────┬────────────────┘   └───────────────┬───────────────┘
                        │                                    │
                        ▼                                    ▼
       ┌─────────────────────────────────┐   ┌───────────────────────────────┐
       │   Docker Self-Hosted Server     │   │      Ergast / Jolpica         │
       │  (julesr0y/f1-livetiming-api)   │   │      Public REST API          │
       └─────────────────────────────────┘   └───────────────────────────────┘
```

### Key Data Entities
*   `LiveSessionStatus`: `meetingKey`, `sessionName`, `sessionType` (Race, Practice, Qualifying, Sprint), `status` (Started, Finished, Inactive), `currentLap`, `totalLaps`.
*   `DriverTiming`: `racingNumber`, `driverCode`, `fullName`, `teamName`, `teamColor`, `position`, `gapToLeader`, `intervalToAhead` (gap to previous driver), `bestLapTime`, `sector1`, `sector2`, `sector3`, `inPit`, `pitStops`, `currentTyreCompound`, `currentTyreAgeLaps`, `isNewTyre`.
*   `TyreStintHistory`: `stintNumber`, `compound` (SOFT, MEDIUM, HARD, INTERMEDIATE, WET), `totalLaps`, `isNew`, `compoundColor`.
*   `TrackWeather`: `airTemp`, `trackTemp`, `humidity`, `pressure`, `windSpeed`, `windDirection`, `rainfall` (rain detected boolean).
*   `DriverStandingsItem`: `position`, `points`, `wins`, `driverName`, `nationality`, `constructorName`, `permanentNumber`.
*   `ConstructorStandingsItem`: `position`, `points`, `wins`, `constructorName`, `nationality`.
*   `SeasonRace`: `round`, `raceName`, `circuitName`, `locality`, `country`, `date`, `time`.

### Docker Configuration Assets
To provide the requested Docker configuration for hosting `julesr0y/f1-livetiming-api`, the project will bundle:
1.  `docker/Dockerfile`: Multi-stage Node.js alpine build pulling and building `julesr0y/f1-livetiming-api` from GitHub with native WebSocket/SignalR dependencies, exposing port 3000.
2.  `docker/docker-compose.yml`: One-command container launcher with port mapping `3000:3000` and restart policy.
3.  `docker/README.md`: Step-by-step instructions for launching locally on Linux/macOS/Windows, exposing to LAN, and connecting the Android app.
