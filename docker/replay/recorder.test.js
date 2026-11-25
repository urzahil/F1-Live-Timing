/**
 * @file recorder.test.js
 * @description Tests for promoting recordings. Run with `node --test` from this directory.
 */

const { test, beforeEach, afterEach } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const os = require('os');
const path = require('path');
const zlib = require('zlib');
const { Recorder } = require('./recorder');

let dir;

beforeEach(() => {
    dir = fs.mkdtempSync(path.join(os.tmpdir(), 'recorder-test-'));
});

afterEach(() => {
    fs.rmSync(dir, { recursive: true, force: true });
});

const stats = (messages) => ({ messages, durationMs: 10 * 60 * 1000, firstLap: 1 });

test('overlapping promotions of one slot leave one intact recording and no stray files', async () => {
    const recorder = new Recorder(dir);
    const header = { slot: 'race', key: 7 };

    // The first promotion keeps the live file (a checkpoint); the second moves it, as finalize() does.
    fs.writeFileSync(recorder.currentPath, 'first\n'.repeat(200));
    const first = recorder.promote(header, stats(200), true);
    fs.writeFileSync(recorder.currentPath, 'second\n'.repeat(400));
    const second = recorder.promote(header, stats(400));
    await Promise.all([first, second]);

    const saved = zlib.gunzipSync(fs.readFileSync(recorder.recordingPath('race'))).toString();
    assert.equal(saved, 'second\n'.repeat(400));
    assert.equal(recorder.readMeta('race').messages, 400);
    const stray = fs.readdirSync(dir).filter((name) => name.endsWith('.pending') || name.endsWith('.tmp'));
    assert.deepEqual(stray, []);
});

test('a smaller copy of the same session queued later does not replace a fuller one', async () => {
    const recorder = new Recorder(dir);
    const header = { slot: 'race', key: 7 };

    fs.writeFileSync(recorder.currentPath, 'full\n'.repeat(400));
    const full = recorder.promote(header, stats(400), true);
    fs.writeFileSync(recorder.currentPath, 'short\n'.repeat(200));
    const short = recorder.promote(header, stats(200), true);
    await Promise.all([full, short]);

    assert.equal(recorder.readMeta('race').messages, 400);
    assert.equal(zlib.gunzipSync(fs.readFileSync(recorder.recordingPath('race'))).toString(), 'full\n'.repeat(400));
});
