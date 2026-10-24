# F1 Live Timing

An Android app for following Formula 1 race weekends: live timing, tyre stints, track weather, championship standings and session results, all in a dark "pit wall" interface.

> Unofficial fan project. See the [disclaimer](#disclaimer).

## Features

### Timing
- Live driver leaderboard
- Session progress
- Race control messages
- Session status and track status
- Track weather
- Per-driver tyre history
- Automatic polling every 5 s while a session is live

### Standings
- Drivers' and constructors' championships for the current season

### Calendar
- Season race calendar.
- Per-weekend results for with timings from all sessions

## How it works

The app combines two data sources:

| Data | Source |
| --- | --- |
| Live timing, tyres, weather, race control | A self-hosted [`julesr0y/f1-livetiming-api`](https://github.com/julesr0y/f1-livetiming-api) backend (configurable in the app) |
| Standings, calendar, session results | The public [Jolpica F1 API](https://api.jolpi.ca/ergast/f1/) (Ergast-compatible) |

The live backend URL can be changed at any time from settings.

### Live backend

[`docker/`](docker/) builds the backend from a pinned upstream commit with local patches.

See [`docker/README.md`](docker/README.md) for setup. After pulling changes to `docker/`, rebuild with `docker compose up -d --build`. The app still works with an unpatched backend, but without the session clock and with the stale-data and missing-tyre issues those patches fix.

## License

Released under the [GNU General Public License v3.0](LICENSE).

## Disclaimer

This is an independently developed, unofficial Formula 1 companion app and is not affiliated with, endorsed by or associated with Formula One, the FIA or any Formula 1 team. Formula 1 and related marks are the property of their respective owners. Data availability and accuracy depend on the configured backend and its upstream sources.
