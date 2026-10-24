# F1 Live Timing

An Android app for following Formula 1 race weekends: live timing, tyre stints, track weather, championship standings and session results, all in a dark "pit wall" interface.

> Unofficial fan project. See the [disclaimer](#disclaimer).

## Features

### Live timing
- Live driver leaderboard with best and last laps, sector times and gaps that follow the session format:
  - Races and sprints: time gap to the leader and interval to the car ahead
  - Practice: best-lap gap to the fastest driver and to the car ahead
  - Qualifying: best-lap gaps from the latest part (Q1/Q2/Q3) with a time
- Session progress above the table: lap counter (`LAP 17 / 44`) in races and sprints, session clock (`REMAINING 42:17`) in practice and qualifying. The clock ticks every second and holds while it is stopped (red flag, between Q parts); the dot is green while it runs and yellow while it is stopped
- Race control box below the classification with the 5 latest messages (track limits, flags, safety car…) in local time
- Session status and track-status flags (green, yellow, red, SC, VSC)
- Track weather: air and track temperature, humidity, pressure, wind and rain
- Per-driver tyre compounds and full stint history; no badge is shown until a driver has tyre data
- Data is cleared when a new session loads, so nothing from the previous session carries over
- Automatic polling every 5 s while a session is live

### Standings
- Drivers' and constructors' championships for the current season

### Calendar and weekends
- Season race calendar, with a pill for every session of the weekend (practice, sprint qualifying, sprint, qualifying, race)
- Session times are converted from UTC to the device's time zone, including daylight saving on the session's own date, so a weekend that spans a clock change shows each session correctly
- Times are hidden once a session has started; its pill keeps its size and shows just the session name
- Weekend detail screen with the results of each session: race, sprint, qualifying, sprint qualifying and practice timings. A session that has not run yet shows when it is scheduled

## How it works

The app combines two data sources:

| Data | Source |
| --- | --- |
| Live timing, tyres, weather, race control | A self-hosted [`julesr0y/f1-livetiming-api`](https://github.com/julesr0y/f1-livetiming-api) backend (URL configurable in the app) |
| Standings, calendar, session results | The public [Jolpica F1 API](https://api.jolpi.ca/ergast/f1/) (Ergast-compatible) |

The live backend URL can be changed at any time from settings.

### Live backend

[`docker/`](docker/) builds the backend from a pinned upstream commit with three local patches from [`docker/patches/`](docker/patches/), applied in filename order:

1. **`01-session-state.patch`** clears the cached state when a new session loads and on every reconnect snapshot, so the previous session's tyres and gaps don't carry over. It also applies F1's partial list updates (stints, sectors) entry by entry instead of dropping the rest of the list.
2. **`02-extrapolated-clock.patch`** subscribes to F1's session clock (`ExtrapolatedClock`) and returns it from `/api/session`.
3. **`03-record-replay.patch`** wires session recording and replay into the client and server (the code itself is in [`docker/replay/`](docker/replay/)):
   - The container records the raw feed of each live session and keeps the latest recording per session type (practice, qualifying, sprint qualifying, race, sprint) in a Docker volume
   - A token-protected control page at `http://<host>:3000/replay` plays a recording back through the normal handlers, so every `/api/*` endpoint answers as it does live. This lets you test the app between race weekends, with adjustable speed and a starting lap
   - Replay is disabled until you set `REPLAY_TOKEN` in `docker/.env`

The Dockerfile also serves the upstream JSDoc site at `/docs`, which upstream advertises but never mounts.

Quick start:

```bash
cd docker
docker compose up -d --build
curl http://localhost:3000/api/status
```

Then enter `http://<host>:3000` as the backend URL in the app. After pulling changes to `docker/`, rebuild with `docker compose up -d --build`. See [`docker/README.md`](docker/README.md) for the full setup, the endpoint list and the recording and replay API.

The app still works with an unpatched backend, but without the session clock and with the stale-data and missing-tyre issues the patches fix.

## Building the app

Requirements: Android Studio (or JDK 17 and the Android SDK), `minSdk` 24.

```bash
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # unit tests
```

The stack is Kotlin and Jetpack Compose, with Retrofit, OkHttp and Moshi for networking and Coil for images.

### Releases

The *Build F1 Live Timing Release APK* GitHub Actions workflow (run manually) runs the unit tests, builds a signed release APK and publishes it as a GitHub Release. It needs the `KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` repository secrets. For a local release build, set the same values as environment variables and `KEYSTORE_PATH` if the keystore is not at `my-upload-key.jks` in the project root.

## License

Released under the [GNU General Public License v3.0](LICENSE).

## Disclaimer

This is an independently developed, unofficial Formula 1 companion app and is not affiliated with, endorsed by or associated with Formula One, the FIA or any Formula 1 team. Formula 1 and related marks are the property of their respective owners. Data availability and accuracy depend on the configured backend and its upstream sources.
