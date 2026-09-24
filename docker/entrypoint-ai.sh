#!/bin/sh
set -e

SSL_ARGS=""
if [ -n "$AI_SSL_CERTFILE" ] && [ -n "$AI_SSL_KEYFILE" ] && [ -f "$AI_SSL_CERTFILE" ] && [ -f "$AI_SSL_KEYFILE" ]; then
    SSL_ARGS="--ssl-certfile $AI_SSL_CERTFILE --ssl-keyfile $AI_SSL_KEYFILE"
fi

exec uvicorn app.main:app --host 0.0.0.0 --port 8000 $SSL_ARGS
