#!/bin/sh
set -eu

set -- uvicorn app.main:app --host 0.0.0.0 --port 8000
if [ -n "${AI_SSL_CERTFILE:-}" ] && [ -n "${AI_SSL_KEYFILE:-}" ] && [ -s "$AI_SSL_CERTFILE" ] && [ -s "$AI_SSL_KEYFILE" ]; then
    set -- "$@" --ssl-certfile "$AI_SSL_CERTFILE" --ssl-keyfile "$AI_SSL_KEYFILE"
elif [ "${AI_REQUIRE_TLS:-false}" = "true" ]; then
    echo "AI TLS certificate and private key are required" >&2
    exit 1
fi

exec "$@"
