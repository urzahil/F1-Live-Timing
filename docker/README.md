# F1 Live Timing API - Docker Setup Guide

This guide explains how to host your own instance of the [`julesr0y/f1-livetiming-api`](https://github.com/julesr0y/f1-livetiming-api) backend to feed real-time Formula 1 live telemetry and timings into the Android app.

---

## Quick Start with Docker Compose

1. Open your terminal in the `docker/` directory:
   ```bash
   cd docker
   ```

2. Build and launch the container:
   ```bash
   docker compose up -d --build
   ```

3. Verify the container is running and healthy:
   ```bash
   docker compose ps
   curl http://localhost:3000/api/status
   ```

4. View live SignalR logs:
   ```bash
   docker compose logs -f f1-livetiming-api
   ```

The image pins a specific upstream commit and applies the patches in `patches/` in filename order:

1. **`01-session-state.patch`** clears the cached state when a new session loads and on every reconnect snapshot, so the previous session's tyres and gaps don't carry over. It also applies F1's partial list updates (stints, sectors) entry by entry instead of dropping the rest of the list.
2. **`02-extrapolated-clock.patch`** subscribes to F1's session clock (`ExtrapolatedClock`) and returns it from `/api/session`.
3. **`03-record-replay.patch`** wires session recording and replay into the client and server (the code itself is in [`docker/replay/`](docker/replay/)):
   - The container records the raw feed of each live session and keeps the latest recording per session type (practice, qualifying, sprint qualifying, race, sprint) in a Docker volume
   - If the container restarts or is rebuilt mid-session, the recording carries on in the same file when the feed comes back, so the replay covers the whole session apart from the time the container was down
   - A token-protected control page at `http://<host>:3000/replay` plays a recording back through the normal handlers, so every `/api/*` endpoint answers as it does live. This lets you test the app between race weekends, with adjustable speed and a starting lap
   - Replay is disabled until you set `REPLAY_TOKEN` in `docker/.env`
4. **`04-fia-documents.patch`** mounts `GET /api/documents`, which lists the FIA decision documents of a grand prix (the code is in [`docker/fia-docs/`](fia-docs/)). See [FIA Documents](#fia-documents) below.
5. **`05-track-status.patch`** subscribes to F1's official track status (`TrackStatus`: green, yellow, safety car, VSC, red flag) and returns it from `/api/session`, so the app's header pill follows the real flag state instead of guessing it from race control messages.
6. **`06-pit-lane-times.patch`** subscribes to F1's `PitLaneTimeCollection` channel and returns it from `GET /api/pitstops`:
   ```json
   {
     "PitTimes": { "16": { "RacingNumber": "16", "Duration": "29.1", "Lap": "7" } },
     "PitStops": { "16": [ { "Lap": 7, "Duration": 29.1 } ] }
   }
   ```
   - `PitTimes` is what the feed currently shows. An entry appears as a car enters the pit lane, gets its `Duration` (seconds, `""` until then) when it leaves, and is removed about 30 s later, so it is usually empty. A new connection always starts from that short-lived view.
   - `PitStops` is every completed stop of the session per driver, with the lap and the pit lane time in seconds. It survives the feed removing entries and a reconnect, and starts again when the session changes. Stops made while the backend was not connected are not in it.
7. **`07-timing-stats.patch`** subscribes to `TimingStats` and returns it from `GET /api/timing-stats` as `{"TimingStats": {"Withheld": false, "Lines": {"<racing number>": {...}}}}`: each driver's personal best lap, best sectors and best speeds (I1, I2, FL, speed trap) with their rank. Applies on top of 06.
8. **`08-session-status.patch`** subscribes to F1's `SessionStatus` channel and returns it from `/api/session` as `"SessionStatus": {"Status": "Started"}`. It reads `"Aborted"` while the session is suspended under a red flag, which the app shows as a red flag even when `TrackStatus` still reads safety car or all clear. Applies on top of 07.
9. **`09-live-snapshot.patch`** mounts `GET /api/live`, the whole live timing screen in one display-ready response (the code is in [`docker/live/`](live/)). The app reads only this endpoint for live timing, so **an app from this version on needs a backend with this patch**; an older one makes it ask you to rebuild. See [Live Snapshot](#live-snapshot) below.

After pulling changes to this folder, rebuild with `docker compose up -d --build`.

To stop the backend:
```bash
docker compose down
```

---

## Standalone Docker Commands (Without Compose)

```bash
# Build the image
docker build -t f1-livetiming-api .

# Run the container mapping port 3000
docker run -d --name f1-livetiming-api -p 3000:3000 --restart unless-stopped f1-livetiming-api

# Check logs
docker logs -f f1-livetiming-api
```

---

## Recording and Replay

While connected to the live feed the container records every session's raw SignalR messages (the full snapshot plus all deltas) and keeps the **latest recording per session type**: practice (FP1-FP3 share one slot), qualifying, sprint qualifying, race and sprint race. A newer session of the same type overwrites the older one, unless you ticked **Keep** on it in the replay page: a kept recording is moved aside (still listed and replayable) when a newer session of its type is saved, and the new session is saved as the latest as usual. Unticking Keep on an archived recording deletes it. Recordings live in the `f1-recordings` volume (`/app/data`) and survive rebuilds.

The replay page highlights the session being recorded right now with a blinking red **RECORDING** badge, which turns into a green **SESSION OVER** badge once the feed reports the session as finished (or timing data has been quiet for 10 minutes) until the next session starts.

A recording is saved when the next session starts, and also as a checkpoint once timing data has been quiet for 10 minutes, so a session is replayable shortly after it ends. Anything shorter than 100 messages or 60 seconds is discarded, and it never replaces a fuller recording of the same session.

Replay feeds the stored messages through the same handlers as the live connection, at the original pace, so every `/api/*` endpoint answers as it does live. While a replay runs, the live feed is ignored.

1. Choose a token and put it in `docker/.env` (git-ignored), then rebuild:
   ```
   REPLAY_TOKEN=some-long-random-string
   ```
   Without a token the replay API answers `503` and nothing can be started.
2. Open `http://<host>:3000/replay`, enter the token, and press **Play** on a recording. Each recording has a speed multiplier (0.1-100, or 0 to pause) and, for races and sprints, a starting lap. While playing you can change the speed, jump to any lap, or return to live data. Speed 0 freezes the position, so every `/api/*` endpoint keeps returning the same data, which is handy for debugging; set a speed again to resume. The session clock freezes while paused.

The same actions are available over HTTP with the header `x-replay-token: <token>` (or `Authorization: Bearer <token>`):

*   `GET /replay/api/recordings`, `GET /replay/api/status`
*   `POST /replay/api/start` with `{"id":"race","speed":1,"lap":20}` (`speed` and `lap` optional; `slot` is accepted as an alias, and archived kept recordings use the `id` shown by `GET /replay/api/recordings`)
*   `POST /replay/api/keep` with `{"id":"race","keep":true}`
*   `POST /replay/api/speed` with `{"speed":5}`, `POST /replay/api/jump` with `{"lap":30}`, `POST /replay/api/live`

Notes: `/api/session` reports the session clock for the current replay position, but the app counts it down in real time between refreshes, so above 1x the clock falls behind and jumps forward at each refresh (about every 5 s; roughly 20 s at 5x). Jumping rebuilds the state from the start of the recording, so it can take a second on a long race. A recording that begins after the session started (for example the container was restarted mid-race) is marked **PARTIAL**.

The replay code has unit tests; run them with Node 20 or newer from `docker/replay`:

```bash
node --test
```

---

## FIA Documents

The container keeps a local copy of the FIA decision documents listed by `https://api.f1cosmos.com/docs/fia`, in `fia-docs.json` in the same `f1-recordings` volume as the recordings.

- On the first start, or whenever that file is missing or empty, it reads every page of the list (about 23 pages of 100) and saves them all. If any page fails, nothing is saved and it tries again 5 minutes later.
- Afterwards it checks once an hour, reading from the newest page onward and stopping at the first page holding a document it already has. Published documents never change, so this is usually a single request.

`GET /api/documents?year=2026&round=18` returns the documents of that round, newest first:

```json
{
  "year": 2026,
  "round": 18,
  "lastRefreshAt": "2026-10-25T11:49:06.694Z",
  "documents": [
    {
      "id": 2295,
      "title": "Doc 44 - Provisional Starting Grid",
      "category": ["Grid/Points"],
      "file_url": "https://www.fia.com/system/files/decision-document/2026_united_states_grand_prix_-_provisional_starting_grid.pdf",
      "published_at": "2026-10-25T00:31:00.000Z"
    }
  ]
}
```

A round with no documents returns an empty list. A missing or non-numeric `year` or `round` answers `400`, and `503` means the first load has not finished yet.

---

## Live Snapshot

`GET /api/live` is what the app polls (every 5 s while a session is live, every 60 s otherwise). The backend reads its live channel state and works out everything the live timing screen shows, so the app only displays the reply:

- **Session:** names, laps, display status (`STARTED`, `SESSION FINISHED`, `NO ACTIVE SESSION` or the feed's own), whether it is live, whether it runs on time (practice, qualifying), the qualifying part (`Q1`-`Q3`, `SQ1`-`SQ3`, worked out from the eliminations) and the session clock.
- **Track status** for the header pill: `CLEAR`, `SAFETY_CAR`, `VIRTUAL_SAFETY_CAR` or `RED_FLAG`. A suspended (`Aborted`) session is a red flag. Otherwise it follows the feed's `TrackStatus`, or the race control messages when that is missing.
- **Weather**, already formatted with units (`--` when missing).
- **Race control:** the latest 5 messages, newest first, with their time in epoch millis (the app shows it in the device's time zone).
- **Drivers** in display order: the right gap fields for each session type (race gaps, practice and qualifying time differences per part), sectors, tyres and stints, pit stops, retired/stopped/knocked out, "catching" and the session's fastest lap.

```json
{
  "schemaVersion": 1,
  "generatedAt": 1793785579306,
  "session": {
    "meetingName": "São Paulo Grand Prix", "sessionName": "Race", "sessionType": "Race",
    "currentLap": 20, "totalLaps": 71, "status": "STARTED", "isLive": true,
    "isTimed": false, "qualifyingPart": null,
    "clock": { "remainingMillis": 5400000, "utcMillis": 1794158400123, "extrapolating": true }
  },
  "trackStatus": "SAFETY_CAR",
  "weather": { "airTemp": "24.1 °C", "trackTemp": "44.0 °C", "humidity": "60.0 %", "pressure": "925.1 hPa", "windSpeed": "1.4 m/s", "windDirection": "120°", "isRaining": false },
  "raceControl": [ { "utcMillis": 1794158280000, "category": "SafetyCar", "flag": "", "message": "SAFETY CAR DEPLOYED", "scope": "" } ],
  "drivers": [
    {
      "position": "1", "racingNumber": "4", "driverCode": "NOR", "fullName": "Lando NORRIS",
      "teamName": "McLaren", "teamColorHex": "F47600",
      "gapToLeader": "LAP 20", "intervalToAhead": "LAP 20", "bestLapTime": "1:12.300", "lastLapTime": "--",
      "sector1": "--", "sector2": "--", "sector3": "--",
      "inPit": false, "pitStops": 1, "totalLaps": 0, "isRetired": false, "isStopped": false,
      "currentCompound": "HARD", "currentCompoundAge": 3, "isCurrentCompoundNew": false,
      "stintsHistory": [ { "stintNumber": 1, "compound": "SOFT", "totalLaps": 10, "isNew": true }, { "stintNumber": 2, "compound": "HARD", "totalLaps": 3, "isNew": false } ],
      "isOverallBestLap": false, "isCatchingAhead": null, "isKnockedOut": false
    }
  ]
}
```

`schemaVersion` only goes up when a field changes meaning or is removed. Adding fields does not change it, and the app ignores fields it does not know. An app that receives a higher version than it supports asks to be updated.

The rules are tested in [`live/live-snapshot.test.js`](live/live-snapshot.test.js); run `node --test` in `docker/live/`.

---
## Available Endpoints

*   `GET /api/status`: Health check, connection state (`CONNECTED` or `DISCONNECTED`), uptime, and timestamp.
*   `GET /api/standings`: Live driver classification with intervals, best lap times, and sectors.
*   `GET /api/leaderboard`: High-performance leaderboard feed.
*   `GET /api/tyres`: Full driver tyre compound history and stint chronological breakdown.
*   `GET /api/drivers`: Active driver metadata and identification.
*   `GET /api/session`: Current meeting name, session type (Race, Practice, Qualifying, Sprint), and session lifecycle.
*   `GET /api/lap-count`: Current and total lap count.
*   `GET /api/weather`: Real-time track weather (air temp, track temp, humidity, pressure, wind speed, rain sensor).
*   `GET /api/race-control`: Official FIA track status flags (GREEN, YELLOW, RED, SC, VSC).
*   `GET /api/radio`: Team radio telemetry broadcasts.
*   `GET /api/pitstops`: Pit lane times: the entries the feed shows now (`PitTimes`) and every completed stop of the session per driver (`PitStops`).
*   `GET /api/timing-stats`: Personal bests, best sectors and best speeds per driver, with rank.
*   `GET /api/documents?year=&round=`: FIA decision documents of a grand prix (`file_url`, `published_at`, title, category).
*   `GET /api/live`: Everything the app's live timing screen shows, in one display-ready response. See [Live Snapshot](#live-snapshot).
