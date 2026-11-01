/**
 * @file replayer.test.js
 * @description Tests for the replayed session clock. Run with `node --test` from this directory.
 */

const { test, beforeEach, afterEach, mock } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const os = require('os');
const path = require('path');
const zlib = require('zlib');
const { Replayer, formatRemaining } = require('./replayer');

// Wall clock of the recorded session's first message.
const W0 = Date.UTC(2026, 9, 31, 14, 0, 0);
const iso = (ms) => new Date(ms).toISOString();

// Recording timeline (position in ms since the start of the recording):
//   10 s     Q1 clock starts: 18:00 at position 9.5 s (Utc half a second before the message arrived)
//   600 s    unrelated record, used as a seek target in the middle of Q1
//   1090 s   clock stops at 0:00, sent as a partial delta without Remaining, then Remaining on its own
//   1500 s   Q2 clock starts: 15:00 at position 1499.8 s
//   2500 s   last record
const CLOCK_START = 9_500;
const CLOCK_STOP = CLOCK_START + 18 * 60_000;
const RESTART = 1_499_800;
const END = 2_500_000;
const MID_Q1 = 600_000;
const MID_Q1_INDEX = 2;

/** Remaining session time the replay should report at a recording position. */
function expectedRemaining(pos) {
    if (pos < CLOCK_START) return null;
    if (pos < CLOCK_STOP) return 18 * 60_000 - (pos - CLOCK_START);
    if (pos < RESTART) return 0;
    return Math.max(0, 15 * 60_000 - (pos - RESTART));
}

/**
 * @param {number} gapMs - Wall-clock time skipped from 1200 s on, as a recording resumed after a
 * container restart leaves the downtime out of its timeline.
 */
function recordingLines(gapMs = 0) {
    const wall = (t) => W0 + t + (t >= 1_200_000 ? gapMs : 0);
    const line = (t, body) => `${t}\t${wall(t)}\t${JSON.stringify(body)}`;
    const clock = (t, d) => line(t, { k: 'f', c: 'ExtrapolatedClock', d });
    return [
        JSON.stringify({ slot: 'qualifying', name: 'Qualifying', meeting: 'Test GP' }),
        line(0, { k: 's', d: { SessionInfo: { Key: 1 } } }),
        clock(10_000, { Utc: iso(wall(CLOCK_START)), Remaining: '0:18:00', Extrapolating: true }),
        line(MID_Q1, { k: 'f', c: 'WeatherData', d: {} }),
        // Partial delta: F1 sometimes sends only the fields that changed.
        clock(CLOCK_STOP + 500, { Utc: iso(wall(CLOCK_STOP)), Extrapolating: false }),
        clock(CLOCK_STOP + 600, { Remaining: '0:00:00' }),
        // A recorded payload can also arrive as a JSON string.
        clock(1_500_000, JSON.stringify({ Utc: iso(wall(RESTART)), Remaining: '0:15:00', Extrapolating: true })),
        line(END, { k: 'f', c: 'WeatherData', d: {} })
    ];
}

function fakeClient() {
    return {
        replaying: false,
        extrapolatedClock: {},
        resetState() {
            this.extrapolatedClock = {};
        },
        enterReplay() {
            this.replaying = true;
        },
        exitReplay() {
            this.replaying = false;
            this.resetState();
        },
        handleSnapshot(d) {
            if (d.ExtrapolatedClock) Object.assign(this.extrapolatedClock, d.ExtrapolatedClock);
        },
        routeData(channel, payload) {
            if (channel === 'ExtrapolatedClock') Object.assign(this.extrapolatedClock, payload);
        },
        getExtrapolatedClock() {
            return this.extrapolatedClock;
        },
        getLapCount() {
            return {};
        }
    };
}

let dir;
let client;
let replayer;

function setup(gapMs = 0) {
    const file = path.join(dir, `rec-${gapMs}.jsonl.gz`);
    fs.writeFileSync(file, zlib.gzipSync(recordingLines(gapMs).join('\n')));
    client = fakeClient();
    replayer = new Replayer(client, { has: () => true, recordingPath: () => file });
}

/** Remaining time the app would compute right now from the /api/session clock: Remaining - (now - Utc). */
function appRemaining() {
    const clock = client.getExtrapolatedClock();
    if (!clock || clock.Remaining === undefined) return null;
    const [h, m, s] = clock.Remaining.split(':').map(Number);
    const remaining = (h * 3600 + m * 60 + s) * 1000;
    return clock.Extrapolating
        ? Math.max(0, remaining - (Date.now() - Date.parse(clock.Utc)))
        : remaining;
}

/** Advances real time, letting the replay timers fire. */
function wait(ms) {
    mock.timers.tick(ms);
}

beforeEach(() => {
    dir = fs.mkdtempSync(path.join(os.tmpdir(), 'replayer-test-'));
    // A replay "now" far from the recording's own wall clock, as when replaying a past session.
    mock.timers.enable({ apis: ['setTimeout', 'Date'], now: W0 + 30 * 86_400_000 + 123 });
    setup();
});

afterEach(() => {
    replayer.stop();
    mock.timers.reset();
    fs.rmSync(dir, { recursive: true, force: true });
});

test('no clock is reported before the recording sends one', () => {
    replayer.start('qualifying', { speed: 1 });
    wait(5_000);
    assert.deepEqual(client.getExtrapolatedClock(), {});
});

for (const speed of [1, 5, 10]) {
    test(`the clock matches the replay position at ${speed}x`, () => {
        replayer.start('qualifying', { speed });
        for (let realMs = 0; realMs * speed < END; realMs += 7_300) {
            const pos = replayer.position();
            const expected = expectedRemaining(pos);
            if (expected === null) assert.equal(appRemaining(), null, `at ${pos}`);
            else assert.ok(Math.abs(appRemaining() - expected) <= 1, `at ${pos}: ${appRemaining()} vs ${expected}`);
            wait(7_300);
        }
    });
}

test('Remaining is whole seconds and Utc carries the fraction', () => {
    replayer.start('qualifying', { speed: 5 });
    wait(10_123);
    const clock = client.getExtrapolatedClock();
    assert.match(clock.Remaining, /^\d+:\d\d:\d\d$/);
    const expected = expectedRemaining(replayer.position());
    assert.equal(appRemaining(), expected);
    assert.ok(Date.parse(clock.Utc) <= Date.now() && Date.now() - Date.parse(clock.Utc) < 1000);
});

test('a partial stop delta keeps Remaining and freezes the clock at 0', () => {
    replayer.start('qualifying', { speed: 10 });
    wait((CLOCK_STOP + 1_000) / 10);
    const clock = client.getExtrapolatedClock();
    assert.equal(clock.Extrapolating, false);
    assert.equal(clock.Remaining, '0:00:00');
    wait(5_000);
    assert.equal(appRemaining(), 0);
});

test('the clock is not reported running once it reaches 0', () => {
    replayer.start('qualifying', { speed: 10 });
    // Between the clock hitting 0 and the stop message arriving.
    wait((CLOCK_STOP + 200) / 10);
    assert.equal(client.getExtrapolatedClock().Extrapolating, false);
    assert.equal(appRemaining(), 0);
});

test('a restarted clock counts down from the new value', () => {
    replayer.start('qualifying', { speed: 10 });
    wait(1_600_000 / 10);
    assert.equal(client.getExtrapolatedClock().Extrapolating, true);
    assert.ok(Math.abs(appRemaining() - expectedRemaining(replayer.position())) <= 1);
});

test('pausing freezes the clock', () => {
    replayer.start('qualifying', { speed: 5 });
    wait(60_000);
    replayer.setSpeed(0);
    const clock = client.getExtrapolatedClock();
    const frozen = appRemaining();
    assert.equal(clock.Extrapolating, false);
    // A stopped clock carries whole seconds only, rounded up as the app rounds its display.
    assert.equal(frozen, Math.ceil(expectedRemaining(replayer.position()) / 1000) * 1000);
    wait(10_000);
    assert.equal(appRemaining(), frozen);
    replayer.setSpeed(1);
    assert.equal(client.getExtrapolatedClock().Extrapolating, true);
});

test('changing speed keeps the clock continuous', () => {
    replayer.start('qualifying', { speed: 1 });
    wait(60_000);
    const before = appRemaining();
    replayer.setSpeed(5);
    assert.ok(Math.abs(appRemaining() - before) <= 1);
    wait(10_000);
    assert.ok(Math.abs(appRemaining() - (before - 50_000)) <= 1);
});

test('seeking mid-session at 5x reports the clock for that position', () => {
    // seek() is what start({lap}) and jump() use; call it directly since this recording has no laps.
    replayer.start('qualifying', { speed: 5 });
    wait(1_000);
    replayer.seek(MID_Q1_INDEX); // replays the Q1 start record at once, 590 s after it was due
    assert.equal(replayer.position(), MID_Q1);
    assert.equal(appRemaining(), expectedRemaining(MID_Q1));
    wait(20_000);
    assert.ok(Math.abs(appRemaining() - expectedRemaining(replayer.position())) <= 1);
});

test('a finished replay reports a stopped clock', () => {
    replayer.start('qualifying', { speed: 100 });
    wait(END / 100 + 2_000);
    assert.equal(replayer.finished, true);
    assert.equal(client.getExtrapolatedClock().Extrapolating, false);
    const frozen = appRemaining();
    assert.equal(frozen, expectedRemaining(END));
    wait(10_000);
    assert.equal(appRemaining(), frozen);
});

test('downtime skipped by the recording does not shift the clock', () => {
    replayer.stop();
    setup(120_000);
    replayer.start('qualifying', { speed: 10 });
    wait(1_700_000 / 10);
    assert.ok(Math.abs(appRemaining() - expectedRemaining(replayer.position())) <= 1);
});

test('after stopping, the client clock is returned unchanged', () => {
    replayer.start('qualifying', { speed: 5 });
    wait(60_000);
    replayer.stop();
    client.extrapolatedClock = { Utc: '2026-11-01T10:00:00Z', Remaining: '0:12:34', Extrapolating: true };
    assert.equal(client.getExtrapolatedClock(), client.extrapolatedClock);
});

test('a malformed recorded clock falls back to the client state', () => {
    replayer.start('qualifying', { speed: 1 });
    wait(15_000);
    replayer.clock.Remaining = 'soon';
    assert.equal(client.getExtrapolatedClock(), client.extrapolatedClock);
});

test('a client without getExtrapolatedClock is tolerated', () => {
    const warn = mock.method(console, 'warn', () => {});
    const bare = fakeClient();
    delete bare.getExtrapolatedClock;
    assert.doesNotThrow(() => new Replayer(bare, {}));
    assert.equal(warn.mock.callCount(), 1);
    warn.mock.restore();
});

test('formatRemaining produces the H:MM:SS form the app parses', () => {
    assert.equal(formatRemaining(0), '0:00:00');
    assert.equal(formatRemaining(59_000), '0:00:59');
    assert.equal(formatRemaining(59_999), '0:00:59');
    assert.equal(formatRemaining(3_600_000), '1:00:00');
    assert.equal(formatRemaining(11 * 3_600_000 + 61_000), '11:01:01');
    assert.equal(formatRemaining(-5), '0:00:00');
});
