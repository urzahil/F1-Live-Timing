/**
 * @file live-snapshot.js
 * @description Builds the single display-ready live timing snapshot served by GET /api/live. Everything the
 * app's live timing screen shows (session, clock, track status, weather, race control and the driver table)
 * is read from the client's channel state and interpreted here, so the app makes one request per refresh
 * and only displays the result.
 *
 * The interpretation rules (gap fields per session type, qualifying parts, track status, sorting) used to
 * live in the Android app's F1LiveRepository and were ported as they were.
 */

/** Bumped when a field changes meaning or is removed; adding fields does not change it. */
const SCHEMA_VERSION = 1;

/** Race control messages included in the snapshot, newest first. */
const RACE_CONTROL_LIMIT = 5;

const TrackStatus = Object.freeze({
    CLEAR: 'CLEAR',
    SAFETY_CAR: 'SAFETY_CAR',
    VIRTUAL_SAFETY_CAR: 'VIRTUAL_SAFETY_CAR',
    RED_FLAG: 'RED_FLAG'
});

// ---------------------------------------------------------------------------------------------------------
// Value helpers. The feed mixes strings, numbers and booleans for the same fields, and SignalR delivers some
// lists as index-keyed objects, so every read goes through one of these.
// ---------------------------------------------------------------------------------------------------------

function isPlainObject(value) {
    return value !== null && typeof value === 'object' && !Array.isArray(value);
}

/** The value as a string: "" for null, undefined, objects and arrays. */
function str(value) {
    if (value === null || value === undefined || typeof value === 'object') return '';
    return String(value);
}

function bool(value) {
    if (typeof value === 'boolean') return value;
    if (typeof value === 'string') return value.trim().toLowerCase() === 'true';
    return false;
}

/** A whole number from a number or numeric string, otherwise [fallback]. */
function int(value, fallback = 0) {
    if (typeof value === 'number' && Number.isFinite(value)) return Math.trunc(value);
    if (typeof value === 'string' && value.trim() !== '') {
        const parsed = Number(value.trim());
        if (Number.isFinite(parsed)) return Math.trunc(parsed);
    }
    return fallback;
}

/** Strict integer parse of a position such as "3"; null for anything else. */
function positionNumber(value) {
    return /^[+-]?\d+$/.test(value) ? parseInt(value, 10) : null;
}

/**
 * Reads a list field that SignalR may deliver either as an array or as an index-keyed object
 * ({"0": {...}, "1": {...}}). Object entries are placed at their index so positional fields (sector 1-3)
 * stay aligned; returns null when the field is missing or holds no indexed entries.
 */
function indexedArray(obj, key) {
    const value = isPlainObject(obj) ? obj[key] : undefined;
    if (Array.isArray(value)) return value;
    if (!isPlainObject(value)) return null;
    const entries = Object.keys(value)
        .filter((k) => /^\d+$/.test(k))
        .map((k) => [parseInt(k, 10), value[k]]);
    if (entries.length === 0) return null;
    const array = [];
    for (const [index, item] of entries) array[index] = item;
    return array;
}

/** A timing field that is either a plain value or a { Value } object. */
function extractValue(obj, key) {
    const item = isPlainObject(obj) ? obj[key] : undefined;
    if (isPlainObject(item)) return str(item.Value);
    if (typeof item === 'string') return item;
    if (typeof item === 'number') return String(item);
    return '';
}

// ---------------------------------------------------------------------------------------------------------
// Times
// ---------------------------------------------------------------------------------------------------------

/** Parses the feed's UTC timestamps ("2026-10-23T17:51:31", optional ".fff…" and "Z") to epoch millis. */
function parseFeedUtcMillis(utc) {
    const trimmed = str(utc).trim();
    if (trimmed.length < 19) return null;
    const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})$/.exec(trimmed.slice(0, 19));
    if (!match) return null;
    const [, y, mo, d, h, mi, s] = match.map(Number);
    const seconds = Date.UTC(y, mo - 1, d, h, mi, s);
    if (Number.isNaN(seconds)) return null;
    const fraction = /^\.(\d{1,3})/.exec(trimmed.slice(19));
    return seconds + (fraction ? parseInt(fraction[1].padEnd(3, '0'), 10) : 0);
}

const LAP_TIME_PATTERN = /^(?:(\d+):)?(?:(\d+):)?(\d+(?:\.\d+)?)$/;

/**
 * Parses "ss.mmm", "m:ss.mmm" or "h:mm:ss.mmm" to seconds. Anything else (placeholders such as "--" or
 * "NO TIME", "NaN", exponent notation) is rejected rather than guessed at.
 */
function parseLapTimeToSeconds(time) {
    const match = LAP_TIME_PATTERN.exec(str(time).trim());
    if (!match) return null;
    const [, first, second, last] = match;
    const seconds = parseFloat(last);
    if (first !== undefined && second !== undefined) return Number(first) * 3600 + Number(second) * 60 + seconds;
    if (first !== undefined) return Number(first) * 60 + seconds;
    return seconds;
}

/**
 * ExtrapolatedClock ({Utc, Remaining: "HH:MM:SS", Extrapolating}) as { remainingMillis, utcMillis,
 * extrapolating }: remainingMillis were left at utcMillis, and while extrapolating the clock keeps counting
 * down from that point. Null when absent or malformed.
 */
function parseSessionClock(clock) {
    if (!isPlainObject(clock)) return null;
    const parts = str(clock.Remaining).trim().split(':');
    if (parts.length !== 3 || parts.some((p) => !/^\d+$/.test(p))) return null;
    const [h, m, s] = parts.map(Number);
    const utcMillis = parseFeedUtcMillis(clock.Utc);
    if (utcMillis === null) return null;
    return {
        remainingMillis: (h * 3600 + m * 60 + s) * 1000,
        utcMillis,
        extrapolating: bool(clock.Extrapolating)
    };
}

// ---------------------------------------------------------------------------------------------------------
// Session
// ---------------------------------------------------------------------------------------------------------

/** Practice, Qualifying and Sprint Qualifying/Shootout run on time rather than a lap count. */
function isTimedSession(sessionType, sessionName) {
    const type = sessionType.trim().toLowerCase();
    if (type === 'practice' || type === 'qualifying') return true;
    if (type !== '') return false;
    const name = sessionName.toLowerCase();
    return name.includes('practice') || name.includes('qualifying') || name.includes('shootout');
}

/**
 * "Q1"/"Q2"/"Q3" (or "SQ1"… for sprint qualifying), or null outside qualifying. The feed does not name the
 * part, so it is inferred from the eliminations: 6 drivers are out after Q1 and 12 after Q2.
 */
function qualifyingPart(sessionType, sessionName, knockedOut) {
    const type = sessionType.trim().toLowerCase();
    const name = sessionName.toLowerCase();
    const isQualifying = type === 'qualifying' ||
        (type === '' && (name.includes('qualifying') || name.includes('shootout')));
    if (!isQualifying) return null;
    const prefix = name.includes('sprint') || name.includes('shootout') ? 'SQ' : 'Q';
    // Assumes 6 drivers are knocked out per part; change this if the format changes.
    const part = knockedOut >= 12 ? 3 : knockedOut >= 6 ? 2 : 1;
    return `${prefix}${part}`;
}

const STARTED_STATUSES = ['started', 'active', 'live'];
const FINISHED_STATUSES = ['finished', 'finalised', 'finalized', 'ended', 'ends'];

/**
 * Session identity, laps, display status and liveness. [sessionStatus] is the live SessionStatus channel,
 * which follows every state change; SessionInfo's own SessionStatus field is the fallback.
 */
function buildSession(sessionInfo, sessionStatus, lapCount) {
    const info = isPlainObject(sessionInfo) ? sessionInfo : {};
    const meeting = isPlainObject(info.Meeting) ? info.Meeting : {};
    const nested = isPlainObject(info.Session) ? info.Session : {};

    const meetingName = str(meeting.Name) || str(meeting.OfficialName) || str(info.MeetingName);
    const sessionName = str(nested.Name) || str(info.Name) || str(info.SessionName);
    const sessionType = str(nested.Type) || str(info.Type) || str(info.SessionType);

    const laps = isPlainObject(lapCount) ? lapCount : {};
    const rawStatus = (isPlainObject(sessionStatus) ? str(sessionStatus.Status) : '') ||
        str(info.SessionStatus) || str(info.Status);
    const status = rawStatus.toLowerCase();

    // The feed reports a red-flagged session as "Aborted"; it is still running and resumes later.
    const isAborted = status === 'aborted';
    const isStarted = isAborted || STARTED_STATUSES.includes(status);
    const isFinished = FINISHED_STATUSES.includes(status);

    let displayStatus = 'NO ACTIVE SESSION';
    if (isStarted && !isFinished) displayStatus = 'STARTED';
    else if (isFinished) displayStatus = 'SESSION FINISHED';
    else if (rawStatus !== '') displayStatus = rawStatus.toUpperCase();

    // A session is only live once explicitly started, while not finished, and with something to name it by.
    const hasIdentity = sessionName !== '' || meetingName !== '' || sessionType !== '';
    return {
        meetingName,
        sessionName,
        sessionType,
        currentLap: int(laps.CurrentLap),
        totalLaps: int(laps.TotalLaps),
        status: displayStatus,
        isLive: isStarted && !isFinished && hasIdentity,
        isAborted
    };
}

// ---------------------------------------------------------------------------------------------------------
// Track status and race control
// ---------------------------------------------------------------------------------------------------------

/**
 * The feed's TrackStatus ({Status: "1".."7", Message}); null when absent or unknown. Yellow (status 2) is
 * a local condition with no pill of its own, so it reads as clear, as does a VSC that is already ending
 * (status 7), matching how the race control messages are read.
 */
function parseTrackStatus(trackStatus) {
    if (!isPlainObject(trackStatus)) return null;
    switch (str(trackStatus.Status).trim()) {
        case '1': case '2': case '7': return TrackStatus.CLEAR;
        case '4': return TrackStatus.SAFETY_CAR;
        case '5': return TrackStatus.RED_FLAG;
        case '6': return TrackStatus.VIRTUAL_SAFETY_CAR;
    }
    switch (str(trackStatus.Message).trim().toUpperCase()) {
        case 'ALLCLEAR': case 'YELLOW': case 'VSCENDING': return TrackStatus.CLEAR;
        case 'SCDEPLOYED': return TrackStatus.SAFETY_CAR;
        case 'RED': return TrackStatus.RED_FLAG;
        case 'VSCDEPLOYED': return TrackStatus.VIRTUAL_SAFETY_CAR;
    }
    return null;
}

/** All race control messages with text, oldest first, as { utc, category, flag, message, scope }. */
function parseRaceControlMessages(raceControl) {
    let messages = [];
    if (Array.isArray(raceControl)) messages = raceControl;
    else if (isPlainObject(raceControl)) {
        messages = indexedArray(raceControl, 'RaceControl') || indexedArray(raceControl, 'Messages') || [];
    }
    return messages
        .filter(isPlainObject)
        .map((m) => ({
            utc: str(m.Utc),
            category: str(m.Category).trim(),
            flag: str(m.Flag).trim().toUpperCase(),
            message: str(m.Message).trim(),
            scope: str(m.Scope).trim()
        }))
        .filter((m) => m.message !== '');
}

/**
 * Track condition after replaying [messagesOldestFirst]; the fallback when the feed reports no TrackStatus.
 * A safety car, VSC or red flag stays in force until a track-wide clear/green (or the VSC announces its end);
 * a red flag only ends on a green track flag, a standing/rolling start announcement, a safety car or VSC
 * restart, or the chequered flag. Sector-scoped flags are ignored.
 */
function deriveTrackStatus(messagesOldestFirst) {
    let status = TrackStatus.CLEAR;
    for (const m of messagesOldestFirst) {
        const text = m.message.toUpperCase();
        const trimmed = text.trim();
        const trackWide = m.scope.toLowerCase() === 'track' || trimmed === 'TRACK CLEAR';
        if (text.includes('VIRTUAL SAFETY CAR') || text.includes('VSC')) {
            if (text.includes('ENDING')) status = TrackStatus.CLEAR;
            else if (text.includes('DEPLOYED')) status = TrackStatus.VIRTUAL_SAFETY_CAR;
        } else if (text.includes('SAFETY CAR')) {
            if (text.includes('DEPLOYED')) status = TrackStatus.SAFETY_CAR;
            else if (text.includes('ENDING')) status = TrackStatus.CLEAR;
            // A race resumed behind the safety car is under it until it comes in.
            else if (status === TrackStatus.RED_FLAG && text.includes('IN THIS LAP')) status = TrackStatus.SAFETY_CAR;
        } else if ((m.flag === 'RED' || trimmed.startsWith('RED FLAG') || text.includes('SUSPENDED')) &&
            (trackWide || m.scope === '')) {
            // Races announce it as "RED FLAG - RACE SUSPENDED" with no Flag or Scope at all.
            status = TrackStatus.RED_FLAG;
        } else if (m.flag === 'CHEQUERED') {
            status = TrackStatus.CLEAR;
        } else if (status === TrackStatus.RED_FLAG && text.includes('ROLLING START')) {
            // A race resumes with "STANDING START" at the announced time, then "OVERTAKE ENABLED" once racing;
            // neither posts a green flag. "RACE WILL RESUME AT" does not end it. A "ROLLING START" resumption
            // leaves behind the safety car, so it continues as a safety car period.
            status = TrackStatus.SAFETY_CAR;
        } else if (status === TrackStatus.RED_FLAG &&
            (text.includes('STANDING START') || trimmed === 'RACE START' || trimmed === 'OVERTAKE ENABLED')) {
            status = TrackStatus.CLEAR;
        } else if (trackWide && m.flag === 'GREEN') {
            // Marshals clearing the track during a suspension post "TRACK CLEAR" while the session is still
            // stopped, so a red flag only ends when the pit exit opens again (a green track flag).
            status = TrackStatus.CLEAR;
        } else if (trackWide && m.flag === 'CLEAR' && status !== TrackStatus.RED_FLAG) {
            status = TrackStatus.CLEAR;
        }
    }
    return status;
}

// ---------------------------------------------------------------------------------------------------------
// Weather
// ---------------------------------------------------------------------------------------------------------

function weatherValue(weather, key, unit) {
    const value = str(weather[key]);
    return value.trim() !== '' ? `${value}${unit}` : '--';
}

/** Weather readings formatted for display ("--" when missing) and whether the rain sensor is wet. */
function buildWeather(weatherData) {
    const w = isPlainObject(weatherData) ? (isPlainObject(weatherData.Weather) ? weatherData.Weather : weatherData) : {};
    const rainfall = parseFloat(str(w.Rainfall) || '0');
    return {
        airTemp: weatherValue(w, 'AirTemp', ' °C'),
        trackTemp: weatherValue(w, 'TrackTemp', ' °C'),
        humidity: weatherValue(w, 'Humidity', ' %'),
        pressure: weatherValue(w, 'Pressure', ' hPa'),
        windSpeed: weatherValue(w, 'WindSpeed', ' m/s'),
        windDirection: weatherValue(w, 'WindDirection', '°'),
        isRaining: Number.isFinite(rainfall) && rainfall > 0
    };
}

// ---------------------------------------------------------------------------------------------------------
// Drivers
// ---------------------------------------------------------------------------------------------------------

/** Tyre stints in order, numbered from 1, as { stintNumber, compound, totalLaps, isNew }. */
function parseStints(stints) {
    const result = [];
    stints.forEach((stint, i) => {
        if (!isPlainObject(stint)) return;
        const isNew = stint.New;
        result.push({
            stintNumber: i + 1,
            compound: str(stint.Compound).trim().toUpperCase() || 'UNKNOWN',
            totalLaps: int(stint.TotalLaps),
            isNew: typeof isNew === 'number' ? isNew !== 0 : bool(isNew)
        });
    });
    return result;
}

/**
 * Value of the first of [keys] in the current qualifying part's Stats entry. Only the current part counts:
 * the leader has no gap in Q2/Q3, and walking back to an earlier part would show the gap from Q1 instead of
 * LEADER. The current part is the last one with a best lap time, or the last Stats entry without those.
 */
function latestQualifyingStat(driver, ...keys) {
    const stats = indexedArray(driver, 'Stats');
    if (!stats) return '';
    const times = indexedArray(driver, 'BestLapTimes');
    let parts;
    if (times) {
        let part = -1;
        for (let i = times.length - 1; i >= 0; i--) {
            if (isPlainObject(times[i]) && extractValue(times[i], 'Value') !== '') { part = i; break; }
        }
        if (part < 0) return '';
        parts = [part];
    } else {
        parts = [];
        for (let i = stats.length - 1; i >= 0; i--) parts.push(i);
    }
    for (const i of parts) {
        const entry = stats[i];
        if (!isPlainObject(entry)) continue;
        for (const key of keys) {
            const value = extractValue(entry, key).trim();
            if (value !== '') return value;
        }
    }
    return '';
}

function sectorValue(sectors, index) {
    const sector = sectors ? sectors[index] : undefined;
    return (isPlainObject(sector) ? str(sector.Value).trim() : '') || '--';
}

/** Feed "Catching" flag, from IntervalToPositionAhead or the line itself; null when not reported. */
function catchingFlag(driver) {
    const interval = driver.IntervalToPositionAhead;
    if (isPlainObject(interval) && 'Catching' in interval) return bool(interval.Catching);
    if ('Catching' in driver) return bool(driver.Catching);
    return null;
}

/** One table row from a merged DriverList + TimingAppData + TimingData line, or null for a placeholder. */
function buildDriver(d, fallbackPosition) {
    const racingNumber = (str(d.RacingNumber) || str(d.DriverNumber)).trim();
    const broadcastName = str(d.BroadcastName).trim();
    const tla = ((str(d.Tla) || str(d.Code)).trim().toUpperCase()) || broadcastName.slice(0, 3).toUpperCase();
    const first = str(d.FirstName).trim();
    const last = str(d.LastName).trim();
    const fullName = str(d.FullName).trim() || (first !== '' || last !== '' ? `${first} ${last}`.trim() : broadcastName);
    const teamName = str(d.TeamName).trim();
    const teamColour = str(d.TeamColour).replace(/#/g, '').trim();

    // A line with nothing to identify the driver by is an empty placeholder.
    if (fullName === '' && tla === '' && broadcastName === '' && teamName === '') return null;
    const pos = str(d.Position).trim();
    if (racingNumber === '' && pos === '') return null;

    const bestLap = extractValue(d, 'BestLapTime').trim() || '--';

    // Races report GapToLeader/IntervalToPositionAhead; practice reports TimeDiffToFastest/
    // TimeDiffToPositionAhead instead, and qualifying keeps them per part (Q1-Q3) in Stats. The Stats
    // fallback is only used once the driver has a lap time in this part; otherwise Q1's gap would linger at
    // the start of Q2/Q3.
    const hasLap = bestLap !== '--';
    const gapRaw = extractValue(d, 'GapToLeader').trim() ||
        extractValue(d, 'TimeDiffToFastest').trim() ||
        (hasLap ? latestQualifyingStat(d, 'TimeDiffToFastest') : '');
    const gapToLeader = gapRaw || (pos === '1' ? 'LEADER' : '--');

    const intervalRaw = extractValue(d, 'IntervalToPositionAhead').trim() ||
        extractValue(d, 'TimeDiffToPositionAhead').trim() ||
        (hasLap ? latestQualifyingStat(d, 'TimeDiffToPositionAhead', 'TimeDifftoPositionAhead') : '');
    const intervalToAhead = intervalRaw || (pos === '1' ? '-' : '--');

    const sectors = indexedArray(d, 'Sectors');
    const inPit = bool(d.InPit);
    const isRetired = bool(d.Retired);
    const isStopped = bool(d.Stopped);

    // The leader has no car ahead, and retired, stopped or pitting cars are not chasing one.
    const noChase = pos === '1' || gapToLeader === 'LEADER' || isRetired || isStopped || inPit;

    const stintsArray = indexedArray(d, 'Stints');
    const stints = stintsArray ? parseStints(stintsArray) : [];
    const current = stints.length > 0 ? stints[stints.length - 1] : null;
    const compound = current && current.compound !== 'UNKNOWN' && current.compound !== ''
        ? current.compound
        : (str(d.Compound) || '--').toUpperCase();

    return {
        position: pos || String(fallbackPosition),
        racingNumber,
        driverCode: tla || 'DRV',
        fullName: fullName || `Driver ${racingNumber}`,
        teamName,
        teamColorHex: teamColour,
        gapToLeader,
        intervalToAhead,
        bestLapTime: bestLap,
        lastLapTime: extractValue(d, 'LastLapTime').trim() || '--',
        sector1: sectorValue(sectors, 0),
        sector2: sectorValue(sectors, 1),
        sector3: sectorValue(sectors, 2),
        inPit,
        pitStops: int(d.NumberOfPitStops, int(d.PitStops)),
        totalLaps: int(d.NumberOfLaps, int(d.Laps)),
        isRetired,
        isStopped,
        currentCompound: compound,
        currentCompoundAge: current ? current.totalLaps : int(d.TyreAge),
        isCurrentCompoundNew: current ? current.isNew : false,
        stintsHistory: stints,
        isOverallBestLap: false,
        isCatchingAhead: noChase ? null : catchingFlag(d),
        // Qualifying only: eliminated in Q1 or Q2. The app hides these rows.
        isKnockedOut: bool(d.KnockedOut)
    };
}

/**
 * The driver table from a { Leaderboard: [racing numbers], Lines: { number: line } } leaderboard, sorted by
 * position (unpositioned entries last; among equals running cars before retired/stopped ones, otherwise in
 * leaderboard order) with the session's fastest lap flagged once.
 */
function buildDrivers(leaderboard) {
    const lb = isPlainObject(leaderboard) ? leaderboard : {};
    const lines = isPlainObject(lb.Lines) ? lb.Lines : {};
    const order = Array.isArray(lb.Leaderboard) && lb.Leaderboard.length > 0 ? lb.Leaderboard : Object.keys(lines);

    const drivers = [];
    for (const number of order) {
        const line = lines[str(number)];
        if (!isPlainObject(line)) continue;
        const driver = buildDriver(line, drivers.length + 1);
        if (driver) drivers.push(driver);
    }

    // Array.prototype.sort is stable, so leaderboard order is kept for any remaining ties.
    drivers.sort((a, b) => {
        const pa = positionNumber(a.position) ?? Number.MAX_SAFE_INTEGER;
        const pb = positionNumber(b.position) ?? Number.MAX_SAFE_INTEGER;
        if (pa !== pb) return pa - pb;
        return Number(a.isRetired || a.isStopped) - Number(b.isRetired || b.isStopped);
    });

    let fastest = null;
    for (const driver of drivers) {
        const seconds = parseLapTimeToSeconds(driver.bestLapTime);
        if (seconds !== null && seconds > 0 && (fastest === null || seconds < fastest.seconds)) {
            fastest = { racingNumber: driver.racingNumber, seconds };
        }
    }
    if (fastest) {
        for (const driver of drivers) {
            if (driver.racingNumber === fastest.racingNumber) driver.isOverallBestLap = true;
        }
    }
    return drivers;
}

// ---------------------------------------------------------------------------------------------------------
// Snapshot
// ---------------------------------------------------------------------------------------------------------

/**
 * Builds the snapshot from raw channel state. Every input is optional.
 * @param {Object} state
 * @param {Object} [state.sessionInfo] SessionInfo channel.
 * @param {Object} [state.sessionStatus] SessionStatus channel.
 * @param {Object} [state.lapCount] LapCount channel.
 * @param {Object} [state.clock] ExtrapolatedClock channel.
 * @param {Object} [state.trackStatus] TrackStatus channel.
 * @param {Object} [state.weather] WeatherData channel.
 * @param {Array|Object} [state.raceControl] Race control messages, oldest first.
 * @param {Object} [state.leaderboard] { Leaderboard, Lines } with DriverList, TimingAppData and TimingData merged per driver.
 * @param {number} [state.now] Time the snapshot is generated, in epoch millis.
 * @returns {Object} The /api/live response.
 */
function buildLiveSnapshot(state = {}) {
    const session = buildSession(state.sessionInfo, state.sessionStatus, state.lapCount);
    const drivers = buildDrivers(state.leaderboard);
    const messages = parseRaceControlMessages(state.raceControl);

    // A suspended session is a red flag whatever TrackStatus says: during a race suspension the feed's
    // TrackStatus can keep reading SCDeployed, or go back to AllClear, until the restart. Otherwise the
    // feed's own TrackStatus is authoritative and the race control messages are only a fallback.
    const trackStatus = session.isAborted
        ? TrackStatus.RED_FLAG
        : parseTrackStatus(state.trackStatus) ?? deriveTrackStatus(messages);

    const knockedOut = drivers.filter((d) => d.isKnockedOut).length;
    const { isAborted, ...sessionFields } = session;

    return {
        schemaVersion: SCHEMA_VERSION,
        generatedAt: state.now ?? Date.now(),
        session: {
            ...sessionFields,
            isTimed: isTimedSession(session.sessionType, session.sessionName),
            qualifyingPart: qualifyingPart(session.sessionType, session.sessionName, knockedOut),
            clock: parseSessionClock(state.clock)
        },
        trackStatus,
        weather: buildWeather(state.weather),
        raceControl: messages
            .slice(-RACE_CONTROL_LIMIT)
            .reverse()
            .map(({ utc, ...m }) => ({ utcMillis: parseFeedUtcMillis(utc), ...m })),
        drivers
    };
}

/** The snapshot of a live client's current state. */
function snapshotFromClient(f1Client) {
    // Required here, like express below, so the functions above can be tested without the upstream code.
    const { getLiveLeaderboard } = require('../services/leaderboard-service');
    return buildLiveSnapshot({
        sessionInfo: f1Client.getSessionInfo(),
        sessionStatus: f1Client.getSessionStatus(),
        lapCount: f1Client.getLapCount(),
        clock: f1Client.getExtrapolatedClock(),
        trackStatus: f1Client.getTrackStatus(),
        weather: f1Client.getWeatherData(),
        raceControl: f1Client.getRaceControlMessages(),
        leaderboard: getLiveLeaderboard(f1Client)
    });
}

/**
 * GET /api/live: the whole live timing screen in one response.
 * @param {F1LiveClient} f1Client
 * @returns {express.Router}
 */
function createLiveRouter(f1Client) {
    const express = require('express');
    const router = express.Router();
    router.get('/', (req, res) => {
        try {
            res.set('Cache-Control', 'no-store');
            res.status(200).json(snapshotFromClient(f1Client));
        } catch (error) {
            console.error('[API Error] Failed to build the live snapshot:', error);
            res.status(500).json({ error: 'Internal server error while building the live snapshot.' });
        }
    });
    return router;
}

module.exports = {
    SCHEMA_VERSION,
    TrackStatus,
    buildLiveSnapshot,
    snapshotFromClient,
    createLiveRouter,
    // Exported for tests
    buildDrivers,
    buildSession,
    buildWeather,
    deriveTrackStatus,
    parseFeedUtcMillis,
    parseLapTimeToSeconds,
    parseRaceControlMessages,
    parseSessionClock,
    parseTrackStatus,
    qualifyingPart,
    isTimedSession
};
