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
