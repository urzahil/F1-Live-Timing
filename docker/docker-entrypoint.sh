#!/bin/sh
# Fix ownership of the data directory, then run the server without root privileges.
set -e

# Started as a non-root user already (docker --user, rootless or arbitrary-UID platforms): nothing to fix or drop.
if [ "$(id -u)" != "0" ]; then
    exec "$@"
fi

DATA_DIR="${DATA_DIR:-/app/data}"
mkdir -p "$DATA_DIR"
# Only touch what is not owned by node yet, stay on one file system and never follow symlinks (-h): the
# directory is writable by the server, so a planted link must not make root re-own a file outside it.
find "$DATA_DIR" -xdev \! -user node -exec chown -h node:node {} + 2>/dev/null \
    || echo "[entrypoint] Could not change ownership of $DATA_DIR" >&2

exec su-exec node "$@"
