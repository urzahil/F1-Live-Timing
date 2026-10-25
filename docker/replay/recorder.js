/**
 * @file recorder.js
 * @description Records the raw SignalR feed (snapshots and deltas) of each live session to disk, keeping only
 * the most recent recording per session slot, plus any older recording the user marked as "keep". Recordings are replayed by replayer.js through the normal
 * F1LiveClient handlers, so every REST endpoint behaves exactly as it does live.
 *
 * File format (gzipped on promotion): the first line is a JSON header, every further line is
 * `<ms since start>\t<wall clock ms>\t<JSON {k: 's'|'f', c: channel, d: payload}>`.
 */

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const { pipeline } = require('stream/promises');

const SLOTS = ['practice', 'qualifying', 'sprint-qualifying', 'race', 'sprint'];

// A recording shorter than this is a connection blip or a container start between sessions, not a session.
const MIN_MESSAGES = 100;
const MIN_DURATION_MS = 60 * 1000;

// A session is only closed when the next one starts, which can be days away. Once timing data has been quiet
// this long, the recording so far is saved as a checkpoint so it is replayable straight after the session.
const CHECKPOINT_IDLE_MS = 10 * 60 * 1000;

function parsePayload(data) {
    if (typeof data !== 'string') return data;
    try {
        return JSON.parse(data);
    } catch (e) {
        return null;
    }
}

/**
 * @description Maps SessionInfo to a recording slot. Practice 1-3 share one slot.
 * @param {Object} info - SessionInfo object ({ Type, Name, ... }).
 * @returns {string|null} Slot name, or null when the session is not one of the recorded kinds.
 */
function classifySession(info) {
    const type = String((info && info.Type) || '').toLowerCase();
    const name = String((info && info.Name) || '').toLowerCase();
    const sprint = name.includes('sprint');
    if (type === 'practice' || name.includes('practice')) return 'practice';
    if (type === 'qualifying' || name.includes('qualifying') || name.includes('shootout')) {
        return sprint ? 'sprint-qualifying' : 'qualifying';
    }
    if (type === 'race' || name === 'race') return sprint ? 'sprint' : 'race';
    return null;
}

/**
 * @description Whether SessionInfo says the session's scheduled end is already well in the past. SessionInfo
 * dates are local track time, with GmtOffset giving the difference to UTC.
 * @param {Object} info - SessionInfo object ({ EndDate, GmtOffset, ... }).
 * @returns {boolean} True when the session ended more than the checkpoint idle time ago.
 */
function sessionEndedLongAgo(info) {
    const match = /^(-?)(\d+):(\d+)/.exec(String((info && info.GmtOffset) || ''));
    const end = Date.parse(`${info && info.EndDate}Z`);
    if (!match || !Number.isFinite(end)) return false;
    const offsetMs = (match[1] ? -1 : 1) * (Number(match[2]) * 60 + Number(match[3])) * 60 * 1000;
    return Date.now() - (end - offsetMs) > CHECKPOINT_IDLE_MS;
}

function newStats() {
    return { messages: 0, durationMs: 0, firstLap: null, maxLap: 0, totalLaps: 0 };
}

function noteLaps(stats, lapCount) {
    if (!lapCount) return;
    const lap = Number(lapCount.CurrentLap);
    if (lapCount.CurrentLap !== undefined && Number.isFinite(lap)) {
        if (stats.firstLap === null) stats.firstLap = lap;
        if (lap > stats.maxLap) stats.maxLap = lap;
    }
    const total = Number(lapCount.TotalLaps);
    if (lapCount.TotalLaps !== undefined && Number.isFinite(total)) stats.totalLaps = total;
}

/**
 * @description Splits a recorded line into its time prefix and JSON body without parsing the body.
 * @param {string} line - One recorded line.
 * @returns {{t: number, w: number, body: string}} Ms since start, wall clock ms and the JSON text.
 */
function splitLine(line) {
    const first = line.indexOf('\t');
    const second = line.indexOf('\t', first + 1);
    return { t: Number(line.slice(0, first)), w: Number(line.slice(first + 1, second)), body: line.slice(second + 1) };
}

class Recorder {
    /**
     * @param {string} dir - Directory holding the recordings (a mounted volume in Docker).
     */
    constructor(dir) {
        this.dir = dir;
        this.current = null;
        try {
            fs.mkdirSync(dir, { recursive: true });
            this.recoverLeftover();
        } catch (err) {
            console.error('[Recorder] Disabled, cannot use data directory:', err.message);
            this.dir = null;
        }
    }

    get currentPath() {
        return path.join(this.dir, 'current.rec');
    }

    // A recording id is either a slot name (the latest of that type) or `archive-<slot>-<key>` (a kept
    // recording that a newer session of the same type has since replaced as the latest).
    recordingPath(id) {
        return path.join(this.dir, `${id}.rec.gz`);
    }

    metaPath(id) {
        return path.join(this.dir, `${id}.json`);
    }

    readMeta(id) {
        try {
            return JSON.parse(fs.readFileSync(this.metaPath(id), 'utf-8'));
        } catch (e) {
            return null;
        }
    }

    archiveId(meta) {
        return `archive-${meta.slot}-${String(meta.key).replace(/[^\w.-]/g, '_')}`;
    }

    /**
     * @returns {string[]} Ids of every stored recording: the latest per slot, then the archived kept ones.
     */
    ids() {
        if (!this.dir) return [];
        const archived = fs.readdirSync(this.dir)
            .filter((file) => file.startsWith('archive-') && file.endsWith('.json'))
            .map((file) => file.slice(0, -'.json'.length))
            .sort();
        return [...SLOTS, ...archived];
    }

    /**
     * @param {string} id - Recording id.
     * @returns {boolean} Whether a complete recording with this id is stored.
     */
    has(id) {
        return typeof id === 'string' && this.ids().includes(id) && !!this.readMeta(id) && fs.existsSync(this.recordingPath(id));
    }

    /**
     * @returns {Object[]} Metadata of every stored recording, latest per slot first, then the archived ones.
     */
    list() {
        if (!this.dir) return [];
        return this.ids().filter((id) => this.has(id)).map((id) => ({ ...this.readMeta(id), id }));
    }

    /**
     * @returns {Object|null} The session being recorded right now ({slot, key, meeting, name, state, messages}),
     * where state is 'recording' until the session is over, then 'over' until the next session starts.
     */
    activity() {
        const cur = this.current;
        if (!this.dir || !cur) return null;
        const { slot, key, meeting, name } = cur.header;
        return { slot, key, meeting, name, state: cur.ended ? 'over' : 'recording', messages: cur.stats.messages };
    }

    /**
     * @description Marks a recording as kept (exempt from replacement by newer sessions) or not. A kept
     * latest recording is archived when a newer session of its type is saved. Unkeeping an archived
     * recording deletes it, since it is no longer the latest of anything.
     * @param {string} id - Recording id.
     * @param {boolean} keep - Whether to keep it.
     */
    setKeep(id, keep) {
        if (!this.has(id)) return false;
        if (!keep && id.startsWith('archive-')) {
            fs.unlinkSync(this.recordingPath(id));
            fs.unlinkSync(this.metaPath(id));
            console.log(`[Recorder] Deleted archived recording ${id}`);
            return true;
        }
        fs.writeFileSync(this.metaPath(id), JSON.stringify({ ...this.readMeta(id), keep: !!keep }));
        return true;
    }

    /**
     * @description Records a subscribe result. A snapshot for a different session key closes the previous
     * recording and starts a new one; a snapshot for the same key (a reconnect) continues the current one.
     * @param {Object} result - Subscribe completion result (full feed state).
     */
    recordSnapshot(result) {
        if (!this.dir || !result) return;
        try {
            const info = result.SessionInfo;
            const key = info && info.Key;
            if (!this.current || (key !== undefined && key !== this.current.header.key)) {
                this.finalize();
                this.begin(info);
            }
            this.write('s', null, result);
        } catch (err) {
            console.error('[Recorder] Failed to record snapshot:', err.message);
        }
    }

    /**
     * @description Records one delta. A SessionInfo delta announcing a new session is not appended: the
     * client reconnects and the new session starts with its own snapshot.
     * @param {string} channel - SignalR channel name.
     * @param {string|Object} payload - Raw payload as received.
     */
    recordFeed(channel, payload) {
        if (!this.dir || !this.current) return;
        try {
            if (channel === 'SessionInfo') {
                const data = parsePayload(payload);
                if (data && data.Key !== undefined && data.Key !== this.current.header.key) return;
            }
            this.write('f', channel, payload);
        } catch (err) {
            console.error('[Recorder] Failed to record message:', err.message);
        }
    }

    begin(info) {
        const slot = info ? classifySession(info) : null;
        if (!slot) {
            this.current = null;
            return;
        }
        const header = {
            v: 1,
            slot,
            key: info.Key,
            name: info.Name || '',
            type: info.Type || '',
            meeting: (info.Meeting && info.Meeting.Name) || '',
            startedAt: new Date().toISOString()
        };
        const fd = fs.openSync(this.currentPath, 'w');
        fs.writeSync(fd, `${JSON.stringify(header)}\n`);
        this.current = { fd, header, startedMs: Date.now(), stats: newStats(), checkpointed: 0, ended: sessionEndedLongAgo(info) };
        console.log(`[Recorder] Recording ${slot} (${header.meeting} - ${header.name}, key ${header.key})`);
    }

    write(kind, channel, data) {
        const cur = this.current;
        if (!cur) return;
        const now = Date.now();
        const t = now - cur.startedMs;
        fs.writeSync(cur.fd, `${t}\t${now}\t${JSON.stringify({ k: kind, c: channel, d: data })}\n`);
        cur.stats.messages++;
        cur.stats.durationMs = t;
        if (kind === 's') noteLaps(cur.stats, data.LapCount);
        else if (channel === 'LapCount') noteLaps(cur.stats, parsePayload(data));

        if (kind === 's') this.noteStatus(data.SessionStatus);
        else if (channel === 'SessionStatus') this.noteStatus(parsePayload(data));

        // Weather keeps arriving after a session ends, so only timing data (or a snapshot) counts as activity.
        if (kind === 's' || channel === 'TimingData' || channel === 'TimingDataF1') {
            clearTimeout(this.checkpointTimer);
            this.checkpointTimer = setTimeout(() => this.checkpoint(), CHECKPOINT_IDLE_MS);
            this.checkpointTimer.unref();
        }
    }

    noteStatus(status) {
        const value = status && String(status.Status || '');
        if (!value) return;
        // Only a restart ('Started') reopens a session that was marked over; other statuses ('Inactive' in a
        // reconnect snapshot, for instance) must not flip a finished session back to recording.
        if (['Finished', 'Finalised', 'Ends'].includes(value)) this.current.ended = true;
        else if (value === 'Started') this.current.ended = false;
    }

    /**
     * @description Saves a copy of the recording so far while it stays open for further messages.
     */
    checkpoint() {
        const cur = this.current;
        if (!cur || cur.checkpointed === cur.stats.messages) return;
        cur.ended = true;
        cur.checkpointed = cur.stats.messages;
        try {
            this.promote(cur.header, { ...cur.stats }, true);
        } catch (err) {
            console.error('[Recorder] Checkpoint failed:', err.message);
        }
    }

    /**
     * @description Closes the current recording and promotes it to its slot, replacing the older one.
     */
    finalize() {
        const cur = this.current;
        if (!cur) return;
        this.current = null;
        clearTimeout(this.checkpointTimer);
        try {
            fs.closeSync(cur.fd);
        } catch (e) {}
        this.promote(cur.header, cur.stats);
    }

    /**
     * @param {Object} header - Recording header.
     * @param {Object} stats - Message, duration and lap statistics.
     * @param {boolean} [keepCurrent] - Copy instead of move, because the recording is still being written.
     */
    promote(header, stats, keepCurrent = false) {
        const slot = header.slot;
        const existing = this.readMeta(slot);
        const enough = stats.messages >= MIN_MESSAGES && stats.durationMs >= MIN_DURATION_MS;
        // The same session recorded twice (container restarted mid-session): keep the fuller one.
        const worse = existing && existing.key === header.key && existing.messages >= stats.messages;
        if (!enough || worse) {
            console.log(`[Recorder] Discarding ${slot} recording (${stats.messages} messages, ${stats.durationMs} ms)`);
            if (!keepCurrent) {
                try {
                    fs.unlinkSync(this.currentPath);
                } catch (e) {}
            }
            return;
        }

        const pending = path.join(this.dir, `${slot}.pending`);
        const tmp = path.join(this.dir, `${slot}.rec.gz.tmp`);
        if (keepCurrent) fs.copyFileSync(this.currentPath, pending);
        else fs.renameSync(this.currentPath, pending);
        const meta = { ...header, ...stats, partial: stats.firstLap !== null && stats.firstLap > 1 };
        pipeline(fs.createReadStream(pending), zlib.createGzip(), fs.createWriteStream(tmp))
            .then(() => {
                // Read again: the user may have toggled "keep" while the recording was being compressed.
                const previous = this.readMeta(slot);
                if (previous && previous.keep) {
                    if (previous.key === header.key) {
                        // A fuller copy of the same session replaces the kept one and stays kept.
                        meta.keep = true;
                    } else if (fs.existsSync(this.recordingPath(slot))) {
                        // A newer session of this type: the kept recording moves aside instead of being overwritten.
                        const archived = this.archiveId(previous);
                        fs.renameSync(this.recordingPath(slot), this.recordingPath(archived));
                        fs.writeFileSync(this.metaPath(archived), JSON.stringify({ ...previous, keep: true }));
                        console.log(`[Recorder] Kept previous ${slot} recording as ${archived}`);
                    }
                }
                fs.renameSync(tmp, this.recordingPath(slot));
                fs.writeFileSync(this.metaPath(slot), JSON.stringify(meta));
                fs.unlinkSync(pending);
                console.log(`[Recorder] Saved ${slot} recording (${stats.messages} messages)`);
            })
            .catch((err) => console.error(`[Recorder] Failed to save ${slot} recording:`, err.message));
    }

    /**
     * @description Promotes a recording left behind by a container stop mid-session.
     */
    recoverLeftover() {
        if (!fs.existsSync(this.currentPath)) return;
        try {
            const lines = fs.readFileSync(this.currentPath, 'utf-8').split('\n').filter((line) => line.length > 0);
            const header = JSON.parse(lines[0]);
            const stats = newStats();
            for (let i = 1; i < lines.length; i++) {
                try {
                    const { t, body } = splitLine(lines[i]);
                    stats.messages++;
                    stats.durationMs = t;
                    if (body.includes('LapCount')) {
                        const rec = JSON.parse(body);
                        noteLaps(stats, rec.k === 's' ? rec.d.LapCount : rec.c === 'LapCount' ? parsePayload(rec.d) : null);
                    }
                } catch (e) {
                    // A line cut short by the stop is dropped.
                }
            }
            this.promote(header, stats);
        } catch (err) {
            console.error('[Recorder] Could not recover unfinished recording:', err.message);
            try {
                fs.unlinkSync(this.currentPath);
            } catch (e) {}
        }
    }
}

module.exports = { Recorder, SLOTS, classifySession, splitLine, parsePayload };
