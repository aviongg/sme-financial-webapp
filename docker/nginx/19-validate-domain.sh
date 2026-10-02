#!/bin/sh
set -eu

# Reject whitespace, config injection, ports and URL syntax before envsubst.
# The official 20-envsubst script runs next and substitutes APP_DOMAIN only.
if ! printf '%s\n' "${APP_DOMAIN:-}" | awk '
    length($0) == 0 || length($0) > 253 { exit 1 }
    {
        n = split($0, labels, ".")
        for (i = 1; i <= n; i++) {
            if (length(labels[i]) > 63 || labels[i] !~ /^[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?$/) exit 1
        }
    }
    END { if (NR != 1) exit 1 }
'; then
    echo 'APP_DOMAIN must be a nonempty DNS hostname without a scheme or port' >&2
    exit 1
fi
# Fix the allowlist even if an operator inherited an unsafe environment value.
test "${NGINX_ENVSUBST_FILTER:-}" = '^APP_DOMAIN$' || {
    echo 'NGINX_ENVSUBST_FILTER must substitute only APP_DOMAIN' >&2
    exit 1
}
