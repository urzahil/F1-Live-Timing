# F1 Live Timing

An Android app for following Formula 1 race weekends: live timing, tyre stints, track weather, championship standings and session results, all in a dark "pit wall" interface.

> Unofficial fan project. See the [disclaimer](#disclaimer).

## Features

### Timing
- Live driver leaderboard with gaps and intervals, best laps and sector times.
- Session status, lap count and race-control / track-status flags (green, yellow, red, SC, VSC).
- Track weather: air and track temperature, humidity, pressure, wind and rain.
- Per-driver tyre compounds and full stint history.
- Adaptive polling: every 5 s while a session is live, every 60 s otherwise. You can pause or resume polling and refresh manually from the top bar.
- Optional **Show Latest Session Data** setting, which shows the most recent session instead of an empty "no live session" state.

### Standings
- Drivers' and constructors' championships for the current season.

### Calendar
- Season race calendar.
- Per-weekend results for race, qualifying, sprint, sprint qualifying and practice timings.

## How it works

The app combines two data sources:

| Data | Source |
| --- | --- |
| Live timing, tyres, weather, race control | A self-hosted [`julesr0y/f1-livetiming-api`](https://github.com/julesr0y/f1-livetiming-api) backend (configurable in the app) |
| Standings, calendar, session results | The public [Jolpica F1 API](https://api.jolpi.ca/ergast/f1/) (Ergast-compatible) |

The live backend URL can be changed at any time from settings.

## License

Released under the [GNU General Public License v3.0](LICENSE).

## Disclaimer

This is an independently developed, unofficial Formula 1 companion app and is not affiliated with, endorsed by or associated with Formula One, the FIA or any Formula 1 team. Formula 1 and related marks are the property of their respective owners. Data availability and accuracy depend on the configured backend and its upstream sources.
