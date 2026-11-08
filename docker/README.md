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
9. **`09-pit-stop-series.patch`** subscribes to F1's `PitStopSeries` channel and returns it from `GET /api/pit-stop-series` as `{"Stops": [...], "PitStopSeries": {...}}`. `Stops` lists every stop of the session, newest first, with `RacingNumber`, `Lap`, `PitStopTime` (stationary, seconds) and `PitLaneTime` (seconds) as the feed sends them. `PitStopSeries` is the raw merged channel, for checking the format against a live session. Applies on top of 08.
10. **`10-f1livedata-pit-stops.patch`** mounts `GET /api/f1livedata/pit-stops`, which returns pit stops as listed by f1livedata.com (the code is in [`docker/f1livedata/`](f1livedata/)). Without parameters it reads their current session; `?session_year=2026&session_number=18&session_event_number=4` picks a past one, numbered as in `https://f1livedata.com/api/session/past_events`. Each stop has `driver`, `stop_number`, `lap`, `pit_stop_time` (stationary, seconds), `pit_lane_time` (seconds), `timestamp` and `is_synthetic`. When `is_synthetic` is true F1 did not publish the stop's details: `pit_stop_time` is `0` and `pit_lane_time` is f1livedata.com's approximation. Nothing is fetched in the background, and each answer is reused for 30 s. With no current session it answers `404`, and `502` means f1livedata.com could not be reached. Applies on top of 09.

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
*   `GET /api/pit-stop-series`: Every pit stop of the session with stationary time (`PitStopTime`) and pit lane time (`PitLaneTime`), plus the raw `PitStopSeries` channel.
*   `GET /api/f1livedata/pit-stops`: Pit stops (stationary and pit lane time) as listed by f1livedata.com, for the current session or a past one.
*   `GET /api/documents?year=&round=`: FIA decision documents of a grand prix (`file_url`, `published_at`, title, category).
