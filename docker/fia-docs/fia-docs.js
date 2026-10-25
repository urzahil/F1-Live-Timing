/**
 * @file fia-docs.js
 * @description Local cache of the FIA decision documents listed by api.f1cosmos.com, and the
 * GET /api/documents route that queries it by season and round.
 *
 * The first start (or an empty cache) walks every page of the source. After that, an hourly refresh
 * reads from the newest page onward and stops at the first page holding a document already cached:
 * published documents never change, so everything past that point is known.
 */

const express = require('express');
const fs = require('fs');
const path = require('path');

const SOURCE_URL = 'https://api.f1cosmos.com/docs/fia';
const PAGE_LIMIT = 100;
const REFRESH_MS = 60 * 60 * 1000;
// After a failed refresh, try again sooner than the regular hour so a first start without network
// does not leave the endpoint empty for long.
const RETRY_MS = 5 * 60 * 1000;
const REQUEST_TIMEOUT_MS = 30 * 1000;
// Guards against a source that never reports its last page.
const MAX_PAGES = 1000;

class FiaDocsCache {
    /**
     * @param {string} dir - Directory holding the cache file (the mounted data volume in Docker).
     */
    constructor(dir) {
        this.file = path.join(dir, 'fia-docs.json');
        this.docs = new Map();
        this.lastRefreshAt = null;
        this.timer = null;
        try {
            fs.mkdirSync(dir, { recursive: true });
            const stored = JSON.parse(fs.readFileSync(this.file, 'utf-8'));
            for (const doc of stored.documents || []) this.docs.set(doc.id, doc);
            this.lastRefreshAt = stored.lastRefreshAt || null;
            console.log(`[FiaDocs] Loaded ${this.docs.size} cached documents.`);
        } catch (err) {
            if (err.code !== 'ENOENT') console.error('[FiaDocs] Ignoring unreadable cache:', err.message);
        }
    }

    get ready() {
        return this.docs.size > 0;
    }

    /**
     * @description Refreshes now, then keeps refreshing every hour (every few minutes after a failure).
     * @returns {void}
     */
    start() {
        const run = async () => {
            let ok = true;
            try {
                await this.refresh();
            } catch (err) {
                ok = false;
                // fetch() reports network errors as a bare "fetch failed"; the reason is in err.cause.
                console.error('[FiaDocs] Refresh failed:', err.message, err.cause ? `(${err.cause.message})` : '');
            }
            this.timer = setTimeout(run, ok ? REFRESH_MS : RETRY_MS);
            this.timer.unref();
        };
        run();
    }

    async fetchPage(page) {
        const url = `${SOURCE_URL}?page=${page}&limit=${PAGE_LIMIT}`;
        const res = await fetch(url, { signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS) });
        if (!res.ok) throw new Error(`HTTP ${res.status} for page ${page}`);
        const body = await res.json();
        if (!Array.isArray(body.data)) throw new Error(`Unexpected response for page ${page}`);
        return body;
    }

    /**
     * @description Fetches new documents. With an empty cache it reads every page; otherwise it stops at
     * the first page that contains a document already cached. Nothing is kept unless the walk succeeds,
     * so a failure halfway through a first load never leaves a partial cache behind.
     * @returns {Promise<void>}
     */
    async refresh() {
        const full = !this.ready;
        const found = new Map();
        for (let page = 1; page <= MAX_PAGES; page++) {
            const body = await this.fetchPage(page);
            let reachedKnown = false;
            for (const doc of body.data) {
                if (doc == null || doc.id == null) continue;
                if (this.docs.has(doc.id)) reachedKnown = true;
                else found.set(doc.id, doc);
            }
            if (!body.hasNextPage || (!full && reachedKnown)) break;
        }

        for (const [id, doc] of found) this.docs.set(id, doc);
        this.lastRefreshAt = new Date().toISOString();
        if (found.size > 0 || full) this.save();
        console.log(`[FiaDocs] ${full ? 'Full load' : 'Refresh'} done: ${found.size} new, ${this.docs.size} total.`);
    }

    save() {
        const tmp = `${this.file}.tmp`;
        const documents = [...this.docs.values()];
        fs.writeFileSync(tmp, JSON.stringify({ lastRefreshAt: this.lastRefreshAt, documents }));
        fs.renameSync(tmp, this.file);
    }

    /**
     * @param {number} year - Championship season.
     * @param {number} round - Round number within the season.
     * @returns {Object[]} Documents of that grand prix, newest first.
     */
    find(year, round) {
        return [...this.docs.values()]
            .filter((doc) => doc.grandprix && doc.grandprix.year === year && doc.grandprix.round === round)
            .sort((a, b) => String(b.published_at).localeCompare(String(a.published_at)));
    }
}

/**
 * @param {FiaDocsCache} cache - Cache to query.
 * @returns {express.Router} Router to mount at /api/documents.
 */
function createDocumentsRouter(cache) {
    const router = express.Router();

    /**
     * GET /api/documents?year=2026&round=18
     * Lists the FIA documents of one grand prix, newest first.
     */
    router.get('/', (req, res) => {
        const year = Number(req.query.year);
        const round = Number(req.query.round);
        if (!Number.isInteger(year) || !Number.isInteger(round)) {
            return res.status(400).json({ error: '"year" and "round" must be integers, e.g. ?year=2026&round=18.' });
        }
        if (!cache.ready) {
            return res.status(503).json({ error: 'The document cache is still loading, try again shortly.' });
        }
        const documents = cache.find(year, round).map((doc) => ({
            id: doc.id,
            title: doc.title,
            category: doc.category,
            file_url: doc.file_url,
            published_at: doc.published_at
        }));
        res.status(200).json({ year, round, lastRefreshAt: cache.lastRefreshAt, documents });
    });

    return router;
}

module.exports = { FiaDocsCache, createDocumentsRouter };
