/**
 * @file router.js
 * @description Express routes for the replay control page (GET /replay) and its token-protected JSON API
 * (/replay/api/*). The API stays disabled until the REPLAY_TOKEN environment variable is set.
 */

const crypto = require('crypto');
const express = require('express');
const path = require('path');
const { Replayer } = require('./replayer');

function sha256(value) {
    return crypto.createHash('sha256').update(String(value)).digest();
}

function tokenMatches(supplied, expected) {
    return crypto.timingSafeEqual(sha256(supplied), sha256(expected));
}

/**
 * @param {F1LiveClient} f1Client - Active client; must carry a `recorder`.
 * @returns {express.Router} Router to mount at /replay.
 */
function createReplayRouter(f1Client) {
    const router = express.Router();
    const replayer = new Replayer(f1Client, f1Client.recorder);
    const token = process.env.REPLAY_TOKEN || '';

    // The page itself holds no data; the token is typed into it and sent with every API call.
    router.get('/', (req, res) => {
        res.sendFile(path.join(__dirname, 'ui.html'));
    });

    router.get('/F1.svg', (req, res) => {
        res.sendFile(path.join(__dirname, 'F1.svg'));
    });

    const api = express.Router();
    api.use((req, res, next) => {
        if (!token) {
            return res.status(503).json({ error: 'Replay control is disabled: set the REPLAY_TOKEN environment variable.' });
        }
        const bearer = (req.get('authorization') || '').replace(/^Bearer\s+/i, '');
        const supplied = req.get('x-replay-token') || bearer;
        if (!supplied || !tokenMatches(supplied, token)) {
            return res.status(401).json({ error: 'Invalid or missing token.' });
        }
        next();
    });

    const handle = (action) => (req, res) => {
        try {
            res.status(200).json(action(req.body || {}));
        } catch (err) {
            if (!err.status) console.error('[Replay] Unexpected error:', err);
            res.status(err.status || 500).json({ error: err.message });
        }
    };

    api.get('/status', handle(() => replayer.status()));
    api.get('/recordings', handle(() => ({ recordings: f1Client.recorder.list() })));
    api.post('/start', handle((body) => {
        replayer.start(body.slot, { speed: body.speed, lap: body.lap });
        return replayer.status();
    }));
    api.post('/speed', handle((body) => {
        replayer.setSpeed(body.speed);
        return replayer.status();
    }));
    api.post('/jump', handle((body) => {
        replayer.jump(body.lap);
        return replayer.status();
    }));
    api.post('/live', handle(() => {
        replayer.stop();
        return replayer.status();
    }));

    router.use('/api', api);
    return router;
}

module.exports = { createReplayRouter };
