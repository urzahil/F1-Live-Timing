/**
 * @file live-snapshot.test.js
 * @description Tests for the /api/live snapshot. Run with `node --test` from this directory. Most cases were
 * ported from the Android app's tests when the interpretation moved here.
 */

const { test } = require('node:test');
const assert = require('node:assert/strict');
const {
    SCHEMA_VERSION,
    TrackStatus,
    buildLiveSnapshot,
    buildDrivers,
    buildSession,
    buildWeather,
    deriveTrackStatus,
    parseLapTimeToSeconds,
    parseRaceControlMessages,
    parseSessionClock
} = require('./live-snapshot');

/** A { Leaderboard, Lines } leaderboard holding [lines] in the given order, keyed by racing number. */
function leaderboard(lines) {
    return {
        Leaderboard: lines.map((l) => l.RacingNumber),
        Lines: Object.fromEntries(lines.map((l) => [l.RacingNumber, l]))
    };
}

const byCode = (drivers) => Object.fromEntries(drivers.map((d) => [d.driverCode, d]));

test('an empty state is a well-formed snapshot with no active session', () => {
    const snapshot = buildLiveSnapshot({ now: 1234 });
    assert.equal(snapshot.schemaVersion, SCHEMA_VERSION);
    assert.equal(snapshot.generatedAt, 1234);
    assert.equal(snapshot.session.isLive, false);
    assert.equal(snapshot.session.status, 'NO ACTIVE SESSION');
    assert.equal(snapshot.session.clock, null);
    assert.equal(snapshot.session.qualifyingPart, null);
    assert.equal(snapshot.trackStatus, TrackStatus.CLEAR);
    assert.deepEqual(snapshot.raceControl, []);
    assert.deepEqual(snapshot.drivers, []);
    assert.equal(snapshot.weather.airTemp, '--');
    assert.equal(snapshot.weather.isRaining, false);
});

test('session names fall back when the preferred field is missing', () => {
    const session = buildSession(
        { Meeting: { OfficialName: 'FORMULA 1 GRAND PRIX' }, Session: { Type: 'Race' }, Name: 'Race', SessionStatus: 'Started' },
        {},
        { CurrentLap: 12, TotalLaps: '71' }
    );
    assert.equal(session.meetingName, 'FORMULA 1 GRAND PRIX');
    assert.equal(session.sessionName, 'Race');
    assert.equal(session.sessionType, 'Race');
    assert.equal(session.currentLap, 12);
    assert.equal(session.totalLaps, 71);
    assert.equal(session.status, 'STARTED');
    assert.equal(session.isLive, true);
});

test('session status: finished, unknown and missing identity', () => {
    const info = { Meeting: { Name: 'Brazilian Grand Prix' }, Name: 'Race', Type: 'Race' };
    assert.equal(buildSession({ ...info, SessionStatus: 'Finalised' }).status, 'SESSION FINISHED');
    assert.equal(buildSession({ ...info, SessionStatus: 'Finalised' }).isLive, false);
    assert.equal(buildSession({ ...info, SessionStatus: 'Inactive' }).status, 'INACTIVE');
    // Started but with nothing to name the session by is not live.
    assert.equal(buildSession({ SessionStatus: 'Started' }).isLive, false);
});

test('empty leaderboards do not invent an empty leader', () => {
    assert.deepEqual(buildDrivers({ Leaderboard: [], Lines: {} }), []);
    // A line object without any driver information is a placeholder.
    assert.deepEqual(buildDrivers({ Leaderboard: ['1'], Lines: { 1: {} } }), []);
});

test('lap time parser accepts valid formats and rejects placeholders', () => {
    assert.ok(Math.abs(parseLapTimeToSeconds('1:23.456') - 83.456) < 1e-4);
    assert.ok(Math.abs(parseLapTimeToSeconds('59.900') - 59.9) < 1e-4);
    assert.ok(Math.abs(parseLapTimeToSeconds('1:02:03.5') - 3723.5) < 1e-4);
    for (const bad of ['--', '', 'NO TIME', 'NaN', '1e3']) assert.equal(parseLapTimeToSeconds(bad), null, bad);
});

test('drivers keep retired cars last, blank sectors as placeholders and flag the fastest lap once', () => {
    const drivers = buildDrivers(leaderboard([
        { RacingNumber: '11', Tla: 'PER', Position: '1', BestLapTime: { Value: '1:21.000' } },
        { RacingNumber: '44', Tla: 'HAM', Position: '11', Retired: true },
        { RacingNumber: '1', Tla: 'VER', Position: '11', BestLapTime: { Value: '1:20.000' }, Sectors: [{ Value: '' }, { Value: '30.100' }] }
    ]));

    assert.deepEqual(drivers.map((d) => d.driverCode), ['PER', 'VER', 'HAM']);
    const ver = drivers.find((d) => d.racingNumber === '1');
    assert.equal(ver.sector1, '--');
    assert.equal(ver.sector2, '30.100');
    assert.equal(ver.sector3, '--');
    assert.deepEqual(drivers.filter((d) => d.isOverallBestLap).map((d) => d.driverCode), ['VER']);
});

test('a line without a position is numbered by its place in the leaderboard before sorting', () => {
    const drivers = buildDrivers(leaderboard([
        { RacingNumber: '10', Tla: 'GAS', Position: '2' },
        { RacingNumber: '6', Tla: 'HAD', Position: '1' },
        { RacingNumber: '5', Tla: 'BOR', Position: '' }
    ]));
    assert.deepEqual(drivers.map((d) => d.driverCode), ['HAD', 'GAS', 'BOR']);
    assert.equal(drivers[2].position, '3');
});

test('practice and qualifying gaps fall back to time diff fields', () => {
    const drivers = byCode(buildDrivers(leaderboard([
        { RacingNumber: '4', Tla: 'NOR', Position: '1', TimeDiffToFastest: '', TimeDiffToPositionAhead: '' },
        { RacingNumber: '81', Tla: 'PIA', Position: '2', TimeDiffToFastest: '+0.215', TimeDiffToPositionAhead: '+0.215' },
        {
            RacingNumber: '16', Tla: 'LEC', Position: '3', BestLapTime: { Value: '1:35.200' },
            Stats: [
                { TimeDiffToFastest: '+0.400', TimeDiffToPositionAhead: '+0.100' },
                { TimeDiffToFastest: '+0.312', TimeDiffToPositionAhead: '+0.097' },
                { TimeDiffToFastest: '', TimeDiffToPositionAhead: '' }
            ]
        },
        {
            RacingNumber: '63', Tla: 'RUS', Position: '5', BestLapTime: { Value: '' },
            Stats: [
                { TimeDiffToFastest: '+0.500', TimeDiffToPositionAhead: '+0.050' },
                { TimeDiffToFastest: '', TimeDiffToPositionAhead: '' },
                { TimeDiffToFastest: '', TimeDiffToPositionAhead: '' }
            ]
        },
        {
            RacingNumber: '1', Tla: 'VER', Position: '1', BestLapTime: { Value: '1:34.471' },
            BestLapTimes: [{ Value: '1:35.215' }, { Value: '1:34.471' }, {}],
            Stats: [
                { TimeDiffToFastest: '+0.186', TimeDifftoPositionAhead: '+0.015' },
                { TimeDiffToFastest: '', TimeDifftoPositionAhead: '' },
                { TimeDiffToFastest: '', TimeDifftoPositionAhead: '' }
            ]
        },
        { RacingNumber: '44', Tla: 'HAM', Position: '4', GapToLeader: '+5.1', IntervalToPositionAhead: { Value: '+1.2' }, TimeDiffToFastest: '+9.9' }
    ])));

    assert.equal(drivers.NOR.gapToLeader, 'LEADER');
    assert.equal(drivers.PIA.gapToLeader, '+0.215');
    assert.equal(drivers.PIA.intervalToAhead, '+0.215');
    // Q3 not yet set but a lap is set in this part: use the latest part that has a value (Q2).
    assert.equal(drivers.LEC.gapToLeader, '+0.312');
    assert.equal(drivers.LEC.intervalToAhead, '+0.097');
    // No lap yet in the current part: the earlier part's gap must not linger.
    assert.equal(drivers.RUS.gapToLeader, '--');
    assert.equal(drivers.RUS.intervalToAhead, '--');
    // The leader in Q2 has no gap in this part; the Q1 gap must not show.
    assert.equal(drivers.VER.gapToLeader, 'LEADER');
    assert.equal(drivers.VER.intervalToAhead, '-');
    // Race fields still take precedence.
    assert.equal(drivers.HAM.gapToLeader, '+5.1');
    assert.equal(drivers.HAM.intervalToAhead, '+1.2');
});

test('stints and sectors delivered as index-keyed objects are read', () => {
    const drivers = byCode(buildDrivers(leaderboard([
        {
            RacingNumber: '81', Tla: 'PIA', Position: '2',
            Stints: { 0: { Compound: 'MEDIUM', New: 'true', TotalLaps: 4 } },
            Sectors: { 0: { Value: '25.101' }, 2: { Value: '40.250' } }
        },
        { RacingNumber: '23', Tla: 'ALB', Position: '19', Stints: { 0: { TotalLaps: 1 }, 1: { Compound: 'HARD', New: 'false', TotalLaps: 1 } } },
        { RacingNumber: '30', Tla: 'LAW', Position: '20', Compound: 'soft', TyreAge: '3' }
    ])));

    assert.equal(drivers.PIA.currentCompound, 'MEDIUM');
    assert.equal(drivers.PIA.currentCompoundAge, 4);
    assert.equal(drivers.PIA.isCurrentCompoundNew, true);
    assert.equal(drivers.PIA.sector1, '25.101');
    assert.equal(drivers.PIA.sector2, '--');
    assert.equal(drivers.PIA.sector3, '40.250');
    assert.deepEqual(drivers.ALB.stintsHistory.map((s) => s.compound), ['UNKNOWN', 'HARD']);
    assert.deepEqual(drivers.ALB.stintsHistory.map((s) => s.stintNumber), [1, 2]);
    assert.equal(drivers.ALB.currentCompound, 'HARD');
    // Without stints the line's own compound and tyre age are used.
    assert.equal(drivers.LAW.currentCompound, 'SOFT');
    assert.equal(drivers.LAW.currentCompoundAge, 3);
});

test('driver identity, pit and lap counters and the catching flag', () => {
    const drivers = byCode(buildDrivers(leaderboard([
        { RacingNumber: '1', Tla: 'VER', Position: '1', IntervalToPositionAhead: { Value: '', Catching: true } },
        {
            RacingNumber: '4', BroadcastName: 'L NORRIS', FirstName: 'Lando', LastName: 'Norris', TeamName: 'McLaren',
            TeamColour: '#F47600', Position: '2', IntervalToPositionAhead: { Value: '+0.8', Catching: true },
            NumberOfPitStops: 2, NumberOfLaps: '40', LastLapTime: { Value: '1:21.500' }
        },
        { RacingNumber: '16', Tla: 'LEC', Position: '3', IntervalToPositionAhead: { Value: '+1.1', Catching: false }, InPit: true },
        { RacingNumber: '63', Tla: 'RUS', Position: '4', IntervalToPositionAhead: { Value: '+2.0', Catching: false } },
        { RacingNumber: '44', Tla: 'HAM', Position: '5', IntervalToPositionAhead: { Value: '+3.0' } }
    ])));

    // Without a Tla the code is taken from the broadcast name.
    const nor = drivers['L N'];
    assert.equal(nor.fullName, 'Lando Norris');
    assert.equal(nor.teamColorHex, 'F47600');
    assert.equal(nor.pitStops, 2);
    assert.equal(nor.totalLaps, 40);
    assert.equal(nor.lastLapTime, '1:21.500');
    assert.equal(nor.isCatchingAhead, true);
    // The leader and a car in the pit lane chase no one.
    assert.equal(drivers.VER.isCatchingAhead, null);
    assert.equal(drivers.LEC.isCatchingAhead, null);
    assert.equal(drivers.RUS.isCatchingAhead, false);
    assert.equal(drivers.HAM.isCatchingAhead, null);
    assert.equal(drivers.HAM.fullName, 'Driver 44');
});

test('race control keeps the latest five messages newest first', () => {
    const messages = [1, 2, 3, 4, 5, 6, 7].map((i) => ({
        Utc: `2026-10-23T17:4${i}:00`, Category: 'Flag', Flag: 'yellow', Message: `MSG ${i}`
    }));
    messages.push({ Utc: '2026-10-23T17:50:00', Category: 'Other', Message: '' });

    const { raceControl } = buildLiveSnapshot({ raceControl: messages });

    assert.deepEqual(raceControl.map((m) => m.message), ['MSG 7', 'MSG 6', 'MSG 5', 'MSG 4', 'MSG 3']);
    assert.equal(raceControl[0].flag, 'YELLOW');
    assert.equal(raceControl[0].utcMillis, Date.UTC(2026, 9, 23, 17, 47, 0));
    assert.deepEqual(buildLiveSnapshot({ raceControl: [] }).raceControl, []);
    // The raw channel shape is read too.
    assert.equal(parseRaceControlMessages({ Messages: { 0: { Message: 'A' } } })[0].message, 'A');
});

test('track status follows safety car, VSC and red flag messages', () => {
    const status = (...msgs) => deriveTrackStatus(parseRaceControlMessages(msgs.map((m) => {
        const [Flag, Scope, Message] = m.split('|');
        return { Utc: '2026-10-23T17:40:00', Category: 'Other', Flag, Scope, Message };
    })));

    assert.equal(status(), TrackStatus.CLEAR);
    assert.equal(status('||SAFETY CAR DEPLOYED'), TrackStatus.SAFETY_CAR);
    assert.equal(status('||SAFETY CAR DEPLOYED', '||SAFETY CAR IN THIS LAP'), TrackStatus.SAFETY_CAR);
    assert.equal(status('||SAFETY CAR DEPLOYED', '||SAFETY CAR IN THIS LAP', 'CLEAR|Track|TRACK CLEAR'), TrackStatus.CLEAR);
    assert.equal(status('||VIRTUAL SAFETY CAR DEPLOYED'), TrackStatus.VIRTUAL_SAFETY_CAR);
    assert.equal(status('||VIRTUAL SAFETY CAR DEPLOYED', '||VIRTUAL SAFETY CAR ENDING'), TrackStatus.CLEAR);
    assert.equal(status('RED|Track|RED FLAG'), TrackStatus.RED_FLAG);
    assert.equal(status('|Track|RED FLAG'), TrackStatus.RED_FLAG);
    // A sector-only clear must not end a track-wide condition.
    assert.equal(status('||SAFETY CAR DEPLOYED', 'CLEAR|Sector|CLEAR IN TRACK SECTOR 4'), TrackStatus.SAFETY_CAR);

    // Mexico City 2026: safety car, then a red flag with no Flag/Scope, then TRACK CLEAR during the suspension.
    const suspension = [
        '||SAFETY CAR DEPLOYED',
        '||RED FLAG - RACE SUSPENDED',
        'CLEAR|Sector|CLEAR IN TRACK SECTOR 2',
        'CLEAR|Track|TRACK CLEAR',
        'DOUBLE YELLOW|Sector|DOUBLE YELLOW IN TRACK SECTOR 3'
    ];
    assert.equal(status(...suspension), TrackStatus.RED_FLAG);
    // Still suspended while the resumption is announced and the safety car lights come on.
    const resuming = [...suspension, '||RACE WILL RESUME AT 15:45', '||SAFETY CAR LIGHTS ON'];
    assert.equal(status(...resuming), TrackStatus.RED_FLAG);
    // The race resumed with a standing start (or, failing that, overtaking is re-enabled after it).
    assert.equal(status(...resuming, '||STANDING START'), TrackStatus.CLEAR);
    assert.equal(status(...resuming, '||OVERTAKE ENABLED'), TrackStatus.CLEAR);
    // A restart behind the safety car is a safety car period, which ends as usual.
    assert.equal(status(...resuming, '||ROLLING START'), TrackStatus.SAFETY_CAR);
    assert.equal(status(...resuming, '||SAFETY CAR IN THIS LAP'), TrackStatus.SAFETY_CAR);
    assert.equal(status(...resuming, '||ROLLING START', '||SAFETY CAR IN THIS LAP', 'CLEAR|Track|TRACK CLEAR'), TrackStatus.CLEAR);
    assert.equal(status(...resuming, '||SAFETY CAR IN THIS LAP', 'GREEN|Track|GREEN FLAG'), TrackStatus.CLEAR);
    // The red flag also ends when the pit exit opens again, or with a safety car restart.
    assert.equal(status(...suspension, 'GREEN|Track|GREEN LIGHT - PIT EXIT OPEN'), TrackStatus.CLEAR);
    assert.equal(status(...suspension, '||SAFETY CAR DEPLOYED'), TrackStatus.SAFETY_CAR);
});

test('the feed track status drives the header pill and an aborted session is a live red flag', () => {
    const sessionInfo = (status = 'Started') => ({
        Meeting: { Name: 'Mexico City Grand Prix' }, Type: 'Qualifying', Name: 'Qualifying', SessionStatus: status
    });
    const snapshot = (trackStatus, status, sessionStatus) =>
        buildLiveSnapshot({ sessionInfo: sessionInfo(status), trackStatus, sessionStatus });

    const red = snapshot({ Status: '5', Message: 'Red' });
    assert.equal(red.session.isLive, true);
    assert.equal(red.trackStatus, TrackStatus.RED_FLAG);
    assert.equal(snapshot({ Status: '1', Message: 'AllClear' }).trackStatus, TrackStatus.CLEAR);
    assert.equal(snapshot({ Status: '2', Message: 'Yellow' }).trackStatus, TrackStatus.CLEAR);
    assert.equal(snapshot({ Status: '4', Message: 'SCDeployed' }).trackStatus, TrackStatus.SAFETY_CAR);
    assert.equal(snapshot({ Message: 'VSCDeployed' }).trackStatus, TrackStatus.VIRTUAL_SAFETY_CAR);

    // Without TrackStatus it is read from the race control messages.
    const fromMessages = buildLiveSnapshot({ sessionInfo: sessionInfo(), trackStatus: {}, raceControl: [{ Message: 'SAFETY CAR DEPLOYED' }] });
    assert.equal(fromMessages.trackStatus, TrackStatus.SAFETY_CAR);

    const aborted = snapshot(undefined, 'Aborted');
    assert.equal(aborted.session.isLive, true);
    assert.equal(aborted.session.status, 'STARTED');
    assert.equal(aborted.trackStatus, TrackStatus.RED_FLAG);
    assert.equal('isAborted' in aborted.session, false);

    // A suspension stays a red flag while TrackStatus still reads safety car or goes back to all clear.
    const sc = { Status: '4', Message: 'SCDeployed' };
    const allClear = { Status: '1', Message: 'AllClear' };
    assert.equal(snapshot(sc, 'Aborted').trackStatus, TrackStatus.RED_FLAG);
    assert.equal(snapshot(allClear, 'Aborted').trackStatus, TrackStatus.RED_FLAG);

    // The live SessionStatus channel wins over SessionInfo's own field.
    const suspended = snapshot(allClear, 'Started', { Status: 'Aborted' });
    assert.equal(suspended.session.isLive, true);
    assert.equal(suspended.trackStatus, TrackStatus.RED_FLAG);
    assert.equal(snapshot(sc, 'Aborted', { Status: 'Started' }).trackStatus, TrackStatus.SAFETY_CAR);
    assert.equal(snapshot(undefined, 'Started', { Status: 'Ends' }).session.isLive, false);
});

test('the session clock is read from ExtrapolatedClock', () => {
    const clock = parseSessionClock({ Utc: '2026-10-23T17:30:00.250Z', Remaining: '01:00:00', Extrapolating: true });
    assert.deepEqual(clock, { remainingMillis: 3_600_000, utcMillis: Date.UTC(2026, 9, 23, 17, 30, 0, 250), extrapolating: true });
    // Longer fractions keep the milliseconds.
    assert.equal(parseSessionClock({ Utc: '2026-10-23T17:30:00.2501234Z', Remaining: '0:00:05' }).utcMillis, Date.UTC(2026, 9, 23, 17, 30, 0, 250));
    // Missing and malformed values give no clock rather than a wrong one.
    assert.equal(parseSessionClock({}), null);
    assert.equal(parseSessionClock({ Utc: '2026-10-23T17:30:00Z', Remaining: '--' }), null);
    assert.equal(parseSessionClock({ Utc: 'soon', Remaining: '00:10:00' }), null);
});

test('timed sessions and the qualifying part', () => {
    const session = (Type, Name, knockedOut = 0) => buildLiveSnapshot({
        sessionInfo: { Meeting: { Name: 'Qatar Grand Prix' }, Type, Name, SessionStatus: 'Started' },
        leaderboard: leaderboard(Array.from({ length: 20 }, (_, i) => ({
            RacingNumber: String(i + 1), Tla: `D${i + 1}`, Position: String(i + 1), KnockedOut: i >= 20 - knockedOut
        })))
    }).session;

    assert.equal(session('Practice', 'Practice 1').isTimed, true);
    assert.equal(session('Race', 'Race').isTimed, false);
    assert.equal(session('Race', 'Sprint').isTimed, false);
    assert.equal(session('', 'Sprint Shootout').isTimed, true);
    assert.equal(session('Race', 'Race').qualifyingPart, null);
    assert.equal(session('Qualifying', 'Qualifying').qualifyingPart, 'Q1');
    assert.equal(session('Qualifying', 'Qualifying', 6).qualifyingPart, 'Q2');
    assert.equal(session('Qualifying', 'Qualifying', 12).qualifyingPart, 'Q3');
    assert.equal(session('Qualifying', 'Sprint Qualifying', 6).qualifyingPart, 'SQ2');
});

test('weather readings are formatted for display', () => {
    assert.deepEqual(
        buildWeather({ AirTemp: '23.4', TrackTemp: 41, Humidity: '55', Pressure: '1012.3', WindSpeed: '1.2', WindDirection: '180', Rainfall: '1' }),
        {
            airTemp: '23.4 °C', trackTemp: '41 °C', humidity: '55 %', pressure: '1012.3 hPa',
            windSpeed: '1.2 m/s', windDirection: '180°', isRaining: true
        }
    );
    const dry = buildWeather({ AirTemp: '', Rainfall: '0' });
    assert.equal(dry.airTemp, '--');
    assert.equal(dry.isRaining, false);
    // The /api/weather wrapper is accepted too.
    assert.equal(buildWeather({ Weather: { TrackTemp: '30' } }).trackTemp, '30 °C');
});
