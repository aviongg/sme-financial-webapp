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
from backup.keyring_validation import validate_keyring


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

    # Prod binds crypto_key_k1/k2 via ConfigTree, NOT legacy FINSIGHT_CRYPTO_KEY_*.
    # Select one secrets directory, never silently merge stale files/environment keys.
    explicit_dir = secrets_dir or os.environ.get("FINSIGHT_SECRETS_DIR")
    if explicit_dir:
        source_dir = Path(explicit_dir)
        if not source_dir.is_dir():
            raise ValueError("Configured keyring secrets directory does not exist")
    else:
        source_dir = next((p for p in (Path("/run/secrets"), Path("./secrets")) if p.is_dir()), None)
    if source_dir is not None:
        for path in source_dir.glob("crypto_key_*"):
            if path.is_file():
                keys[path.name.removeprefix("crypto_key_")] = path.read_text(encoding="utf-8").strip()
    else:
        # Explicit non-prod environment-only workflow; no development literal fallback.
        for name, value in os.environ.items():
            if name.startswith("FINSIGHT_CRYPTO_KEY_"):
                keys[name.removeprefix("FINSIGHT_CRYPTO_KEY_").lower()] = value.strip()

    if not keys:
        raise RuntimeError("No application encryption keys found in environment or secrets directory")

    bundle = {
        "active_key_id": active_key_id,
        "keys": keys
    }
    validate_keyring(bundle)
    return bundle


def run_keyring_backup(
    recipient: str,
    output_dir: Path,
    secrets_dir: Optional[Path] = None,
    explicit_keys: Optional[Dict[str, str]] = None,
    explicit_active_id: Optional[str] = None
) -> Dict[str, any]:
    parse_recipient(recipient)
    output_dir.mkdir(parents=True, exist_ok=True)

    if explicit_keys is not None:
        bundle_data = {
            "active_key_id": explicit_active_id or "k1",
            "keys": explicit_keys
        }
    else:
        bundle_data = collect_keyring(secrets_dir)

    validate_keyring(bundle_data)

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
    parser.add_argument("--secrets-dir", help="ConfigTree directory containing crypto_key_* secrets (takes precedence over legacy key environment variables)")

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
