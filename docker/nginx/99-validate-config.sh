#!/bin/sh
set -eu
# The rendered file is writable by the nginx user; all other config is static.
test -s /etc/nginx/conf.d/finsight.conf
if grep -q '\${APP_DOMAIN' /etc/nginx/conf.d/finsight.conf; then
    echo 'APP_DOMAIN was not rendered' >&2
    exit 1
fi
nginx -t
