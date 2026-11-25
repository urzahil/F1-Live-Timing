#!/bin/sh
# Fix ownership of the data directory, then run the server without root privileges.
set -e

DATA_DIR="${DATA_DIR:-/app/data}"
mkdir -p "$DATA_DIR"
# A bind mount can refuse the chown; the server then still works if the directory is already writable.
chown -R node:node "$DATA_DIR" 2>/dev/null || echo "[entrypoint] Could not change ownership of $DATA_DIR" >&2

exec su-exec node "$@"
