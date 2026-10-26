# Dockerfile for julesr0y/f1-livetiming-api
FROM node:20-alpine

WORKDIR /app

# Install git for repository cloning and curl for container healthcheck
RUN apk add --no-cache git curl

# Clone the official repository, pinned so the local patches below keep applying
ARG UPSTREAM_COMMIT=76512d8c0124acd21e499ef924363e1dbdd825b4
RUN git clone https://github.com/julesr0y/f1-livetiming-api.git . \
 && git checkout --quiet "$UPSTREAM_COMMIT"

# Local patches, applied in filename order:
# 01 - Upstream merges every SignalR delta into one long-lived state tree and never resets it, so a newly
#      loaded session keeps serving the previous session's tyres and gaps. Resets state on a session
#      change and on each snapshot, and merges index-keyed array deltas (Stints, Sectors) properly.
# 02 - Subscribes to ExtrapolatedClock and returns it from /api/session for the session timer.
# 03 - Records each live session's raw feed and can replay it through the normal handlers (src/replay/,
#      copied below; the patch only wires it into f1-client.js and server.js).
# 04 - Mounts GET /api/documents, backed by a local cache of the FIA documents listed by api.f1cosmos.com
#      (src/fia-docs/, copied below).
# 05 - Subscribes to PitLaneTimeCollection and serves it from GET /api/pitstops.
# 06 - Subscribes to TimingStats and serves it from GET /api/timing-stats (needs 05).
# 07 - Subscribes to SessionData and serves it from GET /api/session-data (needs 06).
# CRs are stripped in case a Windows checkout converted a patch; git apply fails the build if one no longer applies.
COPY patches/ /tmp/patches/
RUN sed -i 's/\r$//' /tmp/patches/*.patch \
 && git apply /tmp/patches/*.patch \
 && rm -rf /tmp/patches
COPY replay/ /app/src/replay/
COPY fia-docs/ /app/src/fia-docs/

# Install production dependencies only (express, cors, ws)
RUN npm install --omit=dev

# Upstream server.js advertises /docs (startup log and 404 body) but never mounts it.
# The prebuilt JSDoc site is committed in ./docs, so serve it statically before the 404 fallback.
# The grep fails the build if upstream changes and the patch no longer applies.
RUN sed -i "/^app.use((req, res) => {/i app.use('/docs', express.static(path.join(__dirname, '..', 'docs')));" src/server.js \
 && grep -q "express.static" src/server.js

ENV NODE_ENV=production
ENV PORT=3000

# Recordings of the latest session per type and the FIA document cache; mount a volume here so they survive rebuilds
ENV DATA_DIR=/app/data

# Expose live timing server port
EXPOSE 3000

# Health check to ensure API is responding
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
  CMD curl -f http://localhost:3000/api/status || exit 1

# Start the live timing API server (node src/server.js)
CMD ["npm", "start"]
