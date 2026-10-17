# Dockerfile for julesr0y/f1-livetiming-api
FROM node:20-alpine

WORKDIR /app

# Install git for repository cloning and curl for container healthcheck
RUN apk add --no-cache git curl

# Clone the official repository directly
RUN git clone https://github.com/julesr0y/f1-livetiming-api.git .

# Install production dependencies only (express, cors, ws)
RUN npm install --omit=dev

# Upstream server.js advertises /docs (startup log and 404 body) but never mounts it.
# The prebuilt JSDoc site is committed in ./docs, so serve it statically before the 404 fallback.
# The grep fails the build if upstream changes and the patch no longer applies.
RUN sed -i "/^app.use((req, res) => {/i app.use('/docs', express.static(path.join(__dirname, '..', 'docs')));" src/server.js \
 && grep -q "express.static" src/server.js

ENV NODE_ENV=production
ENV PORT=3000

# Expose live timing server port
EXPOSE 3000

# Health check to ensure API is responding
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
  CMD curl -f http://localhost:3000/api/status || exit 1

# Start the live timing API server (node src/server.js)
CMD ["npm", "start"]
