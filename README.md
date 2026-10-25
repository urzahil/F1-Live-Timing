# F1 Live Timing

An Android app for following Formula 1 race weekends: live timing, tyre stints, track weather, championship standings and session results, all in a dark "pit wall" interface.

> Unofficial fan project. See the [disclaimer](#disclaimer).

## Features

### Live timing
- Live driver leaderboard
- Real time session progress above the table
- Race control messages
- Session status and track status
- Track weather
- Full tyre stint history
- Automatic polling every 5 s while a session is live

### Standings
- Drivers' and constructors' championships for the current season

### Calendar and weekends
- Season race calendar
- Results of each session

## How it works

The app combines two data sources:

| Data | Source |
| --- | --- |
| Live timing, tyres, weather, race control | A self-hosted [`julesr0y/f1-livetiming-api`](https://github.com/julesr0y/f1-livetiming-api) backend (URL configurable in the app) |
| Standings, calendar, session results | The public [Jolpica F1 API](https://api.jolpi.ca/ergast/f1/) (Ergast-compatible) |

The live backend URL can be changed at any time from settings.

### Live backend

[`docker/`](docker/) builds the backend from a pinned upstream commit with local patches from [`docker/patches/`](docker/patches/).

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
