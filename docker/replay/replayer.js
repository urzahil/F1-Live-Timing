/**
 * @file replayer.js
 * @description Plays a stored recording back through F1LiveClient.handleSnapshot / routeData, the same
 * handlers the live SignalR connection uses, at the original pace (or a multiple of it).
 */

const fs = require('fs');
const zlib = require('zlib');
const { splitLine, parsePayload, SLOTS } = require('./recorder');

const MIN_SPEED = 0.1;
const MAX_SPEED = 100;
// Upper bound for a single timer sleep so a speed change or stop takes effect promptly.
const MAX_SLEEP_MS = 1000;

function httpError(status, message) {
    const err = new Error(message);
    err.status = status;
    return err;
}

/**
 * @description Shifts the Utc of an ExtrapolatedClock payload. The clock is "Remaining at Utc", counted
 * down by wall clock, so a replayed clock must claim the time the message would have been sent now.
 */
function rebaseClock(clock, shiftMs) {
    if (!clock || typeof clock.Utc !== 'string') return;
    const ms = Date.parse(clock.Utc);
    if (Number.isFinite(ms)) clock.Utc = new Date(ms + shiftMs).toISOString();
}

class Replayer {
    /**
     * @param {F1LiveClient} client - Client whose state the replay drives.
     * @param {Recorder} recorder - Source of the stored recordings.
     */
    constructor(client, recorder) {
        this.client = client;
        this.recorder = recorder;
        this.rec = null;
        this.slot = null;
        this.timer = null;
        this.speed = 1;
        this.idx = 0;
        this.base = 0;
        this.wallStart = 0;
        this.finished = false;
    }

    parseSpeed(value) {
        const speed = value === undefined || value === null ? 1 : Number(value);
        // 0 pauses: the position freezes, so every endpoint keeps returning the same data until the speed changes.
        if (!Number.isFinite(speed) || (speed !== 0 && speed < MIN_SPEED) || speed > MAX_SPEED) {
            throw httpError(400, `Speed must be 0 (paused) or between ${MIN_SPEED} and ${MAX_SPEED}.`);
        }
        return speed;
    }

    load(slot) {
        const file = this.recorder.recordingPath(slot);
        if (!fs.existsSync(file)) throw httpError(404, `No recording stored for "${slot}".`);

        const lines = zlib.gunzipSync(fs.readFileSync(file)).toString('utf-8').split('\n');
        const header = JSON.parse(lines[0]);
        const ts = [];
        const ws = [];
        const bodies = [];
        const lapStart = new Map();
        let lastLap = 0;
        let totalLaps = 0;

        for (let i = 1; i < lines.length; i++) {
            if (lines[i].length === 0) continue;
            const { t, w, body } = splitLine(lines[i]);
            if (!Number.isFinite(t) || !Number.isFinite(w)) continue;
            if (body.includes('LapCount')) {
                try {
                    const rec = JSON.parse(body);
                    const lapCount = rec.k === 's' ? rec.d.LapCount : rec.c === 'LapCount' ? parsePayload(rec.d) : null;
                    if (lapCount && lapCount.TotalLaps !== undefined) totalLaps = Number(lapCount.TotalLaps) || totalLaps;
                    const lap = lapCount ? Number(lapCount.CurrentLap) : NaN;
                    if (Number.isFinite(lap) && lap > lastLap) {
                        for (let l = lastLap + 1; l <= lap; l++) lapStart.set(l, ts.length);
                        lastLap = lap;
                    }
                } catch (e) {}
            }
            ts.push(t);
            ws.push(w);
            bodies.push(body);
        }
        if (ts.length === 0) throw httpError(409, `The "${slot}" recording is empty.`);
        return { header, ts, ws, bodies, n: ts.length, lapStart, lastLap, totalLaps };
    }

    /**
     * @description Starts replaying a stored recording, replacing live data (or a running replay).
     * @param {string} slot - Recording slot.
     * @param {{speed?: number, lap?: number}} [opts] - Playback speed multiplier and lap to start from.
     */
    start(slot, opts = {}) {
        if (!SLOTS.includes(slot)) throw httpError(400, `Unknown slot "${slot}".`);
        const speed = this.parseSpeed(opts.speed);
        const rec = this.load(slot);
        const target = this.lapIndex(rec, opts.lap);

        this.halt();
        this.rec = rec;
        this.slot = slot;
        this.speed = speed;
        this.client.enterReplay();
        this.seek(target);
    }

    /**
     * @description Jumps to the start of a lap, rebuilding state from the recording's beginning.
     * @param {number} lap - Lap number.
     */
    jump(lap) {
        if (!this.rec) throw httpError(409, 'No replay is running.');
        this.seek(this.lapIndex(this.rec, lap));
    }

    setSpeed(value) {
        if (!this.rec) throw httpError(409, 'No replay is running.');
        const speed = this.parseSpeed(value);
        // Re-anchor the virtual clock so the position does not jump when the rate changes.
        this.base = this.position();
        this.wallStart = Date.now();
        this.speed = speed;
        // A paused replay has no timer, so restart the loop (it also re-times the wait for the new rate).
        this.halt();
        this.tick();
    }

    /**
     * @description Stops the replay and hands the client back to the live feed.
     */
    stop() {
        this.halt();
        this.rec = null;
        this.slot = null;
        if (this.client.replaying) this.client.exitReplay();
    }

    lapIndex(rec, lap) {
        if (lap === undefined || lap === null || lap === '') return 0;
        const number = Number(lap);
        if (!Number.isInteger(number) || number < 1) throw httpError(400, 'Lap must be a positive integer.');
        if (number === 1) return 0;
        if (!rec.lapStart.has(number)) {
            throw httpError(400, `Lap ${number} is not in this recording (laps 1-${rec.lastLap}).`);
        }
        return rec.lapStart.get(number);
    }

    halt() {
        if (this.timer) clearTimeout(this.timer);
        this.timer = null;
    }

    seek(target) {
        this.halt();
        this.client.resetState();
        const rec = this.rec;
        const pos = target > 0 ? rec.ts[target] : 0;
        this.idx = 0;
        while (this.idx < target) this.emit(this.idx++, pos);
        this.base = pos;
        this.wallStart = Date.now();
        this.finished = false;
        this.tick();
    }

    /**
     * @returns {number} Current position in the recording, in ms since its start.
     */
    position() {
        return this.base + (Date.now() - this.wallStart) * this.speed;
    }

    tick() {
        this.timer = null;
        const rec = this.rec;
        if (!rec) return;
        const pos = this.position();
        while (this.idx < rec.n && rec.ts[this.idx] <= pos) this.emit(this.idx++, pos);
        if (this.idx >= rec.n) {
            this.finished = true;
            return;
        }
        // Paused: nothing is due until setSpeed() restarts the loop.
        if (this.speed === 0) return;
        const wait = Math.min(MAX_SLEEP_MS, Math.max(1, (rec.ts[this.idx] - pos) / this.speed));
        this.timer = setTimeout(() => this.tick(), wait);
    }

    emit(i, pos) {
        try {
            const rec = this.rec;
            const msg = JSON.parse(rec.bodies[i]);
            // Wall time at which this record would have been emitted, mapped onto its original wall clock.
            // While paused (speed 0) treat the rate as 1 so the shift stays finite.
            const shift = Date.now() - (pos - rec.ts[i]) / (this.speed || 1) - rec.ws[i];
            if (msg.k === 's') {
                if (msg.d.ExtrapolatedClock) rebaseClock(msg.d.ExtrapolatedClock, shift);
                this.client.handleSnapshot(msg.d);
            } else {
                let payload = msg.d;
                if (msg.c === 'ExtrapolatedClock') {
                    payload = parsePayload(payload);
                    rebaseClock(payload, shift);
                }
                this.client.routeData(msg.c, payload);
            }
            this.client.lastMessageAt = new Date().toISOString();
        } catch (err) {
            console.error('[Replayer] Skipped a record:', err.message);
        }
    }

    /**
     * @returns {Object} Replay state for the control page.
     */
    status() {
        if (!this.rec) return { mode: 'live' };
        const durationMs = this.rec.ts[this.rec.n - 1];
        const lapCount = this.client.getLapCount() || {};
        return {
            mode: 'replay',
            slot: this.slot,
            name: this.rec.header.name,
            meeting: this.rec.header.meeting,
            speed: this.speed,
            finished: this.finished,
            positionMs: this.finished ? durationMs : Math.min(this.position(), durationMs),
            durationMs,
            lap: lapCount.CurrentLap !== undefined ? Number(lapCount.CurrentLap) : null,
            totalLaps: this.rec.totalLaps || null,
            lastLap: this.rec.lastLap || null
        };
    }
}

module.exports = { Replayer };
