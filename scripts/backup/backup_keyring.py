#!/usr/bin/env python3
"""FinSight S9 Application Crypto Keyring Recovery Tool

Extracts retained S5/S7 encryption keys and active key ID into an in-memory bundle,
encrypting it immediately using the owner's offline age recipient.
- Zero plaintext keyring material touches persistent storage.
- Completely separated from the database backup process.
- Produces an encrypted .age bundle and safe metadata manifest.
"""

import argparse
import datetime
import hashlib
import json
import os
import sys
import uuid
from pathlib import Path
from typing import Dict, List, Optional

sys.path.insert(0, str(Path(__file__).parent.parent))
from backup.crypto_age import encrypt_bytes, parse_recipient, AgeCryptoError


def compute_sha256(filepath: Path) -> str:
    hasher = hashlib.sha256()
    with open(filepath, "rb") as f:
        while chunk := f.read(64 * 1024):
            hasher.update(chunk)
    return hasher.hexdigest()


def collect_keyring(secrets_dir: Optional[Path] = None) -> Dict[str, any]:
    """
    Collects active key ID and all retained key entries from environment or secrets directory.
    """
    active_key_id = os.environ.get("FINSIGHT_CRYPTO_ACTIVE_KEY_ID", "k1").strip()
    keys: Dict[str, str] = {}

    # 1. Check environment variables
    for env_var, val in os.environ.items():
        if env_var.startswith("FINSIGHT_CRYPTO_KEY_") and val.strip():
            kid = env_var.replace("FINSIGHT_CRYPTO_KEY_", "").lower()
            keys[kid] = val.strip()

    # 2. Check secrets directory (/run/secrets or ./secrets)
    search_dirs = []
    if secrets_dir and secrets_dir.exists():
        search_dirs.append(secrets_dir)
    search_dirs.extend([Path("/run/secrets"), Path("./secrets")])

    for s_dir in search_dirs:
        if s_dir.is_dir():
            for p in s_dir.glob("crypto_key_*"):
                if p.is_file():
                    kid = p.name.replace("crypto_key_", "")
                    try:
                        key_val = p.read_text(encoding="utf-8").strip()
                        if key_val and kid not in keys:
                            keys[kid] = key_val
                    except Exception:
                        pass

    if not keys:
        # Fallback for dev/test harness if default key k1 exists
        dev_k1 = os.environ.get("CRYPTO_KEY_K1")
        if dev_k1:
            keys["k1"] = dev_k1.strip()

    if not keys:
        raise RuntimeError("No application encryption keys found in environment or secrets directory")

    if active_key_id not in keys:
        # If active_key_id not in keys, select first available key
        active_key_id = next(iter(keys.keys()))

    return {
        "active_key_id": active_key_id,
        "keys": keys
    }


def run_keyring_backup(
    recipient: str,
    output_dir: Path,
    secrets_dir: Optional[Path] = None,
    explicit_keys: Optional[Dict[str, str]] = None,
    explicit_active_id: Optional[str] = None
) -> Dict[str, any]:
    parse_recipient(recipient)
    output_dir.mkdir(parents=True, exist_ok=True)

    if explicit_keys:
        bundle_data = {
            "active_key_id": explicit_active_id or "k1",
            "keys": explicit_keys
        }
    else:
        bundle_data = collect_keyring(secrets_dir)

    backup_uuid = str(uuid.uuid4())
    now_utc = datetime.datetime.now(datetime.timezone.utc)
    timestamp_str = now_utc.strftime("%Y%m%dT%H%M%SZ")

    base_name = f"finsight_keyring_{timestamp_str}_{backup_uuid[:8]}"
    final_artifact = output_dir / f"{base_name}.age"
    partial_artifact = output_dir / f"{base_name}.age.partial"
    manifest_path = output_dir / f"{base_name}_manifest.json"

    # Serialize bundle in memory
    plaintext_bundle = json.dumps(bundle_data, indent=2).encode("utf-8")

    # Encrypt directly in memory without writing plaintext to disk
    encrypted_bytes = encrypt_bytes(plaintext_bundle, recipient)

    # Write partial and atomically rename
    with open(partial_artifact, "wb") as f:
        f.write(encrypted_bytes)
    partial_artifact.rename(final_artifact)

    sha256 = compute_sha256(final_artifact)
    file_size = final_artifact.stat().st_size

    # Manifest with non-sensitive metadata only (NO key secrets or values!)
    manifest = {
        "backup_id": backup_uuid,
        "timestamp_utc": now_utc.isoformat(),
        "bundle_type": "s5_crypto_keyring_recovery",
        "active_key_id": bundle_data["active_key_id"],
        "retained_key_ids": sorted(list(bundle_data["keys"].keys())),
        "encrypted_size_bytes": file_size,
        "sha256_checksum": sha256,
        "age_recipient": recipient,
        "encryption_algorithm": "X25519-ChaCha20Poly1305"
    }

    with open(manifest_path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2)

    return {
        "status": "SUCCESS",
        "backup_id": backup_uuid,
        "artifact_path": str(final_artifact),
        "manifest_path": str(manifest_path),
        "active_key_id": bundle_data["active_key_id"],
        "retained_key_ids": sorted(list(bundle_data["keys"].keys())),
        "size_bytes": file_size,
        "sha256": sha256
    }


def main():
    parser = argparse.ArgumentParser(description="FinSight S9 Crypto Keyring Recovery Backup Tool")
    parser.add_argument("--recipient", help="Public age recipient key (age1...)")
    parser.add_argument("--recipient-file", help="Path to file containing public age recipient key")
    parser.add_argument("--output-dir", default="./backups/keyring", help="Target output directory")
    parser.add_argument("--secrets-dir", default="./secrets", help="Directory containing crypto_key_* secrets")

    args = parser.parse_args()

    recipient = args.recipient
    if not recipient and args.recipient_file:
        with open(args.recipient_file, "r", encoding="utf-8") as f:
            recipient = f.read().strip()
    if not recipient:
        recipient = os.environ.get("FINSIGHT_BACKUP_RECIPIENT", "")
    if not recipient:
        print("ERROR: Public age recipient key is required (--recipient or --recipient-file)", file=sys.stderr)
        sys.exit(1)

    secrets_dir = Path(args.secrets_dir) if args.secrets_dir else None

    try:
        result = run_keyring_backup(
            recipient=recipient,
            output_dir=Path(args.output_dir),
            secrets_dir=secrets_dir
        )
        print(json.dumps(result, indent=2))
    except Exception as e:
        print(f"ERROR: {str(e)}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
