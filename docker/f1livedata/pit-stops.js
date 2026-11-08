/**
 * @file pit-stops.js
 * @description GET /api/f1livedata/pit-stops: pit stops as listed by f1livedata.com, with stationary time
 * (pit_stop_time) and pit lane time (pit_lane_time) per stop. Without parameters it reads f1livedata.com's current
 * session; ?session_year=&session_number=&session_event_number= picks a past one (numbers as in their
 * /api/session/past_events, e.g. 2026, 18, 4 for the 2026 United States Grand Prix race).
 *
 * f1livedata.com marks a stop is_synthetic when F1 did not publish its details: pit_stop_time is then 0 and
 * pit_lane_time is their approximation. Stops are passed through as they come.
 *
 * Nothing is fetched in the background. Each distinct request is answered from a 30 s cache, so polling this
 * endpoint sends at most two requests to f1livedata.com every 30 s per session, however often it is called.
 */

const express = require('express');

const SOURCE = 'https://f1livedata.com';
const CACHE_MS = 30 * 1000;
const REQUEST_TIMEOUT_MS = 15 * 1000;
const USER_AGENT = 'F1-Live-Timing personal backend (+https://github.com/urzahil/F1-Live-Timing)';

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

const SESSION_KEYS = ['session_year', 'session_number', 'session_event_number'];

/**
 * @description Flattens the pit stop rows into one stop per entry. The site's live stream sends rows shaped
 * { data: { "<driver>": { stop_number, lap, pit_stop_time, pit_lane_time, timestamp, is_synthetic } } }; rows that
 * already are a stop are kept as they are. The format of the REST answer is not documented, so both are accepted.
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

/**
 * @returns {import('express').Router} Router serving GET / (mounted at /api/f1livedata/pit-stops).
 */
function createF1LiveDataPitStopsRouter() {
    const router = express.Router();
    // Keyed by the requested session ("current" or "year/number/event"): { at, body } or a pending fetch.
    const cache = new Map();

    const load = async (requested) => {
        const session = requested || (await getJson('/api/session/current'));
        const params = new URLSearchParams();
        for (const key of SESSION_KEYS) {
            if (session && session[key] !== undefined && session[key] !== null) params.append(key, String(session[key]));
        }
        const raw = await getJson(`/api/timing_data/pit_stops?${params.toString()}`);
        return { source: SOURCE, fetchedAt: new Date().toISOString(), session, stops: flattenStops(raw), raw };
    };

    router.get('/', async (req, res) => {
        const given = SESSION_KEYS.filter((key) => req.query[key] !== undefined);
        if (given.length && (given.length !== SESSION_KEYS.length || !SESSION_KEYS.every((key) => /^\d+$/.test(String(req.query[key]))))) {
            return res.status(400).json({ error: `Pass all of ${SESSION_KEYS.join(', ')} as numbers, or none for the current session.` });
        }
        const requested = given.length ? Object.fromEntries(SESSION_KEYS.map((key) => [key, Number(req.query[key])])) : null;
        const cacheKey = requested ? SESSION_KEYS.map((key) => requested[key]).join('/') : 'current';
        try {
            let entry = cache.get(cacheKey);
            if (!entry || (entry.body && Date.now() - entry.at > CACHE_MS)) {
                // Concurrent requests share one fetch instead of each calling f1livedata.com.
                entry = { pending: load(requested) };
                cache.set(cacheKey, entry);
                try {
                    cache.set(cacheKey, { at: Date.now(), body: await entry.pending });
                } catch (err) {
                    cache.delete(cacheKey);
                    throw err;
                }
                entry = cache.get(cacheKey);
            } else if (entry.pending) {
                await entry.pending.catch(() => {});
                entry = cache.get(cacheKey);
                if (!entry || !entry.body) throw new Error('f1livedata.com request failed');
            }
            res.status(200).json(entry.body);
        } catch (err) {
            if (err.status === 404 && !requested) {
                return res.status(404).json({ error: 'f1livedata.com has no current session. Pass session_year, session_number and session_event_number for a past one.' });
            }
            // fetch() reports network errors as a bare "fetch failed"; the reason is in err.cause.
            const reason = err.cause ? `${err.message}: ${err.cause.message || err.cause}` : err.message;
            console.error('[F1LiveData] Pit stops request failed:', reason);
            res.status(502).json({ error: `f1livedata.com request failed: ${reason}` });
        }
    });

    return router;
}

module.exports = { createF1LiveDataPitStopsRouter, flattenStops };
