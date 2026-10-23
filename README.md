# F1 Live Timing

An Android app for following Formula 1 race weekends: live timing, tyre stints, track weather, championship standings and session results, all in a dark "pit wall" interface.

> Unofficial fan project. See the [disclaimer](#disclaimer).

## Features

### Timing
- Live driver leaderboard with best and last laps, sector times and gaps that follow the session format:
  - Races and sprints: time gap to the leader and interval to the car ahead
  - Practice: best-lap gap to the fastest driver and to the car ahead
  - Qualifying: best-lap gaps from the latest part (Q1/Q2/Q3) with a time
- Session progress above the table: lap counter (`LAP 17 / 44`) in races and sprints, session clock (`REMAINING 42:17`) in practice and qualifying. The clock ticks every second and holds when the clock is stopped (red flag, between Q parts); the dot is green while it runs, yellow while it is stopped
- Race control box below the classification with the 5 latest messages (track limits, flags, safety car…) in local time
- Session status and track-status flags (green, yellow, red, SC, VSC)
- Track weather: air and track temperature, humidity, pressure, wind and rain
- Per-driver tyre compounds and full stint history; no badge is shown until a driver has tyre data
- Data is cleared when a new session loads, so nothing from the previous session carries over
- Automatic polling every 5 s while a session is live

### Standings
- Drivers' and constructors' championships for the current season

### Calendar
- Season race calendar.
- Per-weekend results for race, qualifying, sprint, sprint qualifying and practice timings

## How it works

The app combines two data sources:

| Data | Source |
| --- | --- |
| Live timing, tyres, weather, race control | A self-hosted [`julesr0y/f1-livetiming-api`](https://github.com/julesr0y/f1-livetiming-api) backend (configurable in the app) |
| Standings, calendar, session results | The public [Jolpica F1 API](https://api.jolpi.ca/ergast/f1/) (Ergast-compatible) |

The live backend URL can be changed at any time from settings.

### Live backend

[`docker/`](docker/) builds the backend from a pinned upstream commit with local patches from [`docker/patches/`](docker/patches/):

1. **`01-session-state.patch`** clears the cached state when a new session loads, so the previous session's tyres and gaps don't carry over, and on every reconnect snapshot. It also applies F1's partial list updates (stints, sectors) entry by entry instead of dropping the rest of the list.
2. **`02-extrapolated-clock.patch`** subscribes to F1's session clock (`ExtrapolatedClock`) and returns it from `/api/session`.

See [`docker/README.md`](docker/README.md) for setup. After pulling changes to `docker/`, rebuild with `docker compose up -d --build`. The app still works with an unpatched backend, but without the session clock and with the stale-data and missing-tyre issues those patches fix.

## License

Released under the [GNU General Public License v3.0](LICENSE).

## Disclaimer

This is an independently developed, unofficial Formula 1 companion app and is not affiliated with, endorsed by or associated with Formula One, the FIA or any Formula 1 team. Formula 1 and related marks are the property of their respective owners. Data availability and accuracy depend on the configured backend and its upstream sources.
