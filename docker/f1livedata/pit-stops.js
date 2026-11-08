/**
 * @file pit-stops.js
 * @description Polls f1livedata.com for the current session's pit stops every 30 s while a session is running, and
 * serves them from GET /api/f1livedata/pit-stops.
 *
 * Each poll that finds a change is fed to F1LiveClient as the "F1LiveDataPitStops" channel, through the same path as
 * the SignalR feed: it is written to the session recording and merged into the client state. A replay therefore
 * serves the pit stops recorded with the session, and the endpoint reads the client state either way.
 *
 * Polling only runs while SessionStatus is Started, Aborted (red flag) or Finished, and never during a replay.
 * f1livedata.com marks a stop is_synthetic when F1 did not publish its details: pit_stop_time is then 0 and
 * pit_lane_time is their approximation. Stops are passed through as they come.
 */

const express = require('express');

const SOURCE = 'https://f1livedata.com';
const CHANNEL = 'F1LiveDataPitStops';
const POLL_MS = 30 * 1000;
const REQUEST_TIMEOUT_MS = 15 * 1000;
const USER_AGENT = 'F1-Live-Timing personal backend (+https://github.com/urzahil/F1-Live-Timing)';
const ACTIVE_STATUSES = ['Started', 'Aborted', 'Finished'];
const SESSION_KEYS = ['session_year', 'session_number', 'session_event_number'];

/**
 * @param {string} pathAndQuery - Path on f1livedata.com, with its query string.
 * @returns {Promise<any>} Parsed JSON body.
 */
async function getJson(pathAndQuery) {
    const res = await fetch(`${SOURCE}${pathAndQuery}`, {
        headers: { Accept: 'application/json', 'User-Agent': USER_AGENT },
        signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS)
    });
    if (!res.ok) {
        const err = new Error(`${pathAndQuery} answered HTTP ${res.status}`);
        err.status = res.status;
        throw err;
    }
    return res.json();
}

/**
 * @description Flattens the pit stop rows into one stop per entry. The REST answer is a list of stops; the site's
 * live stream wraps them as { data: { "<driver>": {...} } }, so that shape is unwrapped too.
 * @param {any} rows - Body of /api/timing_data/pit_stops.
 * @returns {Object[]} Stops, newest first.
 */
function flattenStops(rows) {
    const stops = [];
    for (const row of Array.isArray(rows) ? rows : []) {
        if (row && row.data && typeof row.data === 'object' && !Array.isArray(row.data)) {
            for (const [driver, stop] of Object.entries(row.data)) {
                if (stop && typeof stop === 'object') stops.push({ driver, ...stop });
            }
        } else if (row && typeof row === 'object') {
            stops.push(row);
        }
    }
    stops.sort((a, b) => String(b.timestamp || '').localeCompare(String(a.timestamp || '')));
    return stops;
}

class F1LiveDataPoller {
    /**
     * @param {F1LiveClient} client - Client whose state and recording receive the pit stops.
     */
    constructor(client) {
        this.client = client;
        this.timer = null;
        this.running = false;
        this.lastPollAt = null;
        this.lastError = null;
        this.lastStops = null;
    }

    /**
     * @returns {boolean} Whether a live session is running, so polling is worthwhile.
     */
    active() {
        if (this.client.replaying) return false;
        const status = (this.client.getSessionStatus && this.client.getSessionStatus()) || {};
        return ACTIVE_STATUSES.includes(String(status.Status || ''));
    }

    start() {
        if (this.timer) return;
        this.timer = setInterval(() => this.poll(), POLL_MS);
        this.timer.unref();
    }

    async poll() {
        if (this.running || !this.active()) return;
        this.running = true;
        try {
            const session = await getJson('/api/session/current');
            const params = new URLSearchParams();
            for (const key of SESSION_KEYS) {
                if (session && session[key] !== undefined && session[key] !== null) params.append(key, String(session[key]));
            }
            const stops = flattenStops(await getJson(`/api/timing_data/pit_stops?${params.toString()}`));
            this.lastPollAt = new Date().toISOString();
            this.lastError = null;
            // Only changes are fed in, so a recording holds one record per new or corrected stop, not one per poll.
            const serialized = JSON.stringify(stops);
            if (serialized !== this.lastStops || !this.client.getF1LiveDataPitStops()) {
                const fed = this.client.ingest(CHANNEL, { source: SOURCE, fetchedAt: this.lastPollAt, session, stops });
                if (fed) this.lastStops = serialized;
            }
        } catch (err) {
            this.lastPollAt = new Date().toISOString();
            // fetch() reports network errors as a bare "fetch failed"; the reason is in err.cause.
            this.lastError = err.status === 404
                ? 'f1livedata.com has no current session'
                : err.cause ? `${err.message}: ${err.cause.message || err.cause}` : err.message;
            if (err.status !== 404) console.error('[F1LiveData] Pit stops poll failed:', this.lastError);
        } finally {
            this.running = false;
        }
    }

    /**
     * @returns {{polling: boolean, intervalSeconds: number, lastPollAt: string|null, lastError: string|null}}
     */
    status() {
        return { polling: this.active(), intervalSeconds: POLL_MS / 1000, lastPollAt: this.lastPollAt, lastError: this.lastError };
    }
}

/**
 * @param {F1LiveClient} client - Client holding the pit stops (live or replayed).
 * @param {F1LiveDataPoller} poller - Poller reported alongside them.
 * @returns {import('express').Router} Router serving GET / (mounted at /api/f1livedata/pit-stops).
 */
function createF1LiveDataPitStopsRouter(client, poller) {
    const router = express.Router();
    router.get('/', (req, res) => {
        try {
            const data = client.getF1LiveDataPitStops() || {};
            res.status(200).json({
                source: SOURCE,
                replay: !!client.replaying,
                fetchedAt: data.fetchedAt || null,
                session: data.session || null,
                stops: data.stops || [],
                poller: poller.status()
            });
        } catch (error) {
            console.error('[API Error] Failed to retrieve f1livedata.com pit stops:', error);
            res.status(500).json({ error: 'Internal server error while reading f1livedata.com pit stops.' });
        }
    });
    return router;
}

module.exports = { CHANNEL, F1LiveDataPoller, createF1LiveDataPitStopsRouter, flattenStops };
