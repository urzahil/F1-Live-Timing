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

The image pins a specific upstream commit and applies the patches in `patches/` in filename order (session-change state reset and index-keyed array deltas; the session clock from the `ExtrapolatedClock` channel, returned by `/api/session`). After pulling changes to this folder, rebuild with `docker compose up -d --build`.

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

While connected to the live feed the container records every session's raw SignalR messages (the full snapshot plus all deltas) and keeps the **latest recording per session type**: practice (FP1-FP3 share one slot), qualifying, sprint qualifying, race and sprint race. A newer session of the same type overwrites the older one. Recordings live in the `f1-recordings` volume (`/app/data`) and survive rebuilds.

A recording is saved when the next session starts, and also as a checkpoint once timing data has been quiet for 10 minutes, so a session is replayable shortly after it ends. Anything shorter than 100 messages or 60 seconds is discarded, and it never replaces a fuller recording of the same session.

Replay feeds the stored messages through the same handlers as the live connection, at the original pace, so every `/api/*` endpoint answers as it does live. While a replay runs, the live feed is ignored.

1. Choose a token and put it in `docker/.env` (git-ignored), then rebuild:
   ```
   REPLAY_TOKEN=some-long-random-string
   ```
   Without a token the replay API answers `503` and nothing can be started.
2. Open `http://<host>:3000/replay`, enter the token, and press **Play** on a recording. Each recording has a speed multiplier (0.1-100) and, for races and sprints, a starting lap. While playing you can change the speed, jump to any lap, or return to live data.

The same actions are available over HTTP with the header `x-replay-token: <token>` (or `Authorization: Bearer <token>`):

*   `GET /replay/api/recordings`, `GET /replay/api/status`
*   `POST /replay/api/start` with `{"slot":"race","speed":1,"lap":20}` (`speed` and `lap` optional)
*   `POST /replay/api/speed` with `{"speed":5}`, `POST /replay/api/jump` with `{"lap":30}`, `POST /replay/api/live`

Notes: the session clock is only correct at 1x, because the app counts it down in real time. Jumping rebuilds the state from the start of the recording, so it can take a second on a long race. A recording that begins after the session started (for example the container was restarted mid-race) is marked **PARTIAL**.

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
