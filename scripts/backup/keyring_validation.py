"""Validate the versioned Base64 AES-256 key contract used by Java."""

import base64
import re

KEY_ID = re.compile(r"[A-Za-z0-9_-]{1,32}\Z")


def validate_keyring(bundle):
    if not isinstance(bundle, dict):
        raise ValueError("Keyring must be an object")
    active = bundle.get("active_key_id")
    entries = bundle.get("keys")
    if not isinstance(active, str) or not KEY_ID.fullmatch(active):
        raise ValueError("A valid active key ID is required")
    if not isinstance(entries, dict) or not entries or active not in entries:
        raise ValueError("Active key must be present in keyring")
    keys = {}
    for key_id, secret in entries.items():
        if not isinstance(key_id, str) or not KEY_ID.fullmatch(key_id) or not isinstance(secret, str):
            raise ValueError("Invalid keyring entry")
        try:
            raw = base64.b64decode(secret.strip(), validate=True)
        except Exception as error:
            raise ValueError("Keyring entries must be Base64") from error
        if len(raw) != 32:
            raise ValueError("Every retained key must decode to exactly 32 bytes")
        keys[key_id] = raw
    return active, keys
