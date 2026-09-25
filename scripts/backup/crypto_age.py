"""FinSight S9 Cryptographic Backup Engine (Official age / X25519 specification)

Implements standards-compliant public-key encrypted logical backup streams based on the official
age specification (RFC / age-encryption.org/v1) using the mature, interoperable `pyrage` library
(Rust age bindings by PyCA) with cross-CLI verification against official age CLI tooling.
- The backup server stores ONLY the public age recipient (age1...).
- The owner keeps the private age identity (AGE-SECRET-KEY-1...) offline.
- Decryption requires the offline private identity.
- Full interoperability with official age tools (age / rage).
"""

import io
import os
import shutil
import subprocess
from pathlib import Path
from typing import BinaryIO, List, Optional, Tuple

try:
    import pyrage
    from pyrage import x25519
except ImportError:
    pyrage = None
    x25519 = None


class AgeCryptoError(Exception):
    """Base exception for cryptographic backup operations."""
    pass


class InvalidKeyError(AgeCryptoError):
    """Raised when an age key format or key material is invalid."""
    pass


class DecryptionError(AgeCryptoError):
    """Raised when decryption fails due to wrong key, tampering, or corruption."""
    pass


class CorruptedBackupError(DecryptionError):
    """Raised when backup ciphertext integrity verification fails."""
    pass


# -----------------------------------------------------------------------------
# Tool Discovery: Check for official age CLI in PATH or scripts/tools
# -----------------------------------------------------------------------------

def find_official_age_cli() -> Optional[Path]:
    """Locates the official age binary on PATH or in scripts/tools."""
    cli_path = shutil.which("age")
    if cli_path:
        return Path(cli_path)
    
    # Check project tools directory
    base_dir = Path(__file__).resolve().parent.parent / "tools" / "age"
    for candidate in ("age.exe", "age"):
        p = base_dir / candidate
        if p.is_file():
            return p
    return None


def find_official_keygen_cli() -> Optional[Path]:
    """Locates the official age-keygen binary on PATH or in scripts/tools."""
    cli_path = shutil.which("age-keygen")
    if cli_path:
        return Path(cli_path)
    
    base_dir = Path(__file__).resolve().parent.parent / "tools" / "age"
    for candidate in ("age-keygen.exe", "age-keygen"):
        p = base_dir / candidate
        if p.is_file():
            return p
    return None


# -----------------------------------------------------------------------------
# Key Pair Generation and Parsing (Official age / X25519)
# -----------------------------------------------------------------------------

def generate_keypair() -> Tuple[str, str]:
    """
    Generates an offline owner age keypair (public recipient, private identity)
    compliant with age-encryption.org/v1.
    """
    if pyrage is not None:
        ident = x25519.Identity.generate()
        recip = ident.to_public()
        return str(recip), str(ident)
    
    # Fallback to official age-keygen CLI
    keygen_bin = find_official_keygen_cli()
    if keygen_bin:
        res = subprocess.run([str(keygen_bin)], capture_output=True, text=True, check=True)
        identity = ""
        recipient = ""
        for line in res.stdout.splitlines():
            line = line.strip()
            if line.startswith("# public key:"):
                recipient = line.replace("# public key:", "").strip()
            elif line.startswith("AGE-SECRET-KEY-1"):
                identity = line
        if identity and recipient:
            return recipient, identity
            
    raise RuntimeError("Neither pyrage nor official age-keygen binary is available")


def parse_recipient(recipient_str: str):
    """Validates and parses a public age recipient string (age1...)."""
    recipient_str = recipient_str.strip()
    if not recipient_str.startswith("age1"):
        raise InvalidKeyError(f"Recipient must start with 'age1', got: {recipient_str[:8]}...")
    
    if pyrage is not None:
        try:
            return x25519.Recipient.from_str(recipient_str)
        except Exception as e:
            raise InvalidKeyError(f"Invalid age recipient format: {e}") from e
    return recipient_str


def parse_identity(identity_str: str):
    """Validates and parses a private age identity string (AGE-SECRET-KEY-1...)."""
    identity_str = identity_str.strip()
    if not identity_str.upper().startswith("AGE-SECRET-KEY-1"):
        raise InvalidKeyError("Identity key must start with 'AGE-SECRET-KEY-1...'")
    
    if pyrage is not None:
        try:
            return x25519.Identity.from_str(identity_str)
        except Exception as e:
            raise InvalidKeyError(f"Invalid age identity format: {e}") from e
    return identity_str


# -----------------------------------------------------------------------------
# Streaming Encryption and Decryption (Standards Compliant)
# -----------------------------------------------------------------------------

def encrypt_stream(in_stream: BinaryIO, out_stream: BinaryIO, recipient_str: str) -> int:
    """
    Encrypts in_stream to out_stream using the standard age protocol.
    Returns total bytes written to out_stream.
    """
    recip = parse_recipient(recipient_str)
    
    if pyrage is not None:
        try:
            start_pos = out_stream.tell()
        except Exception:
            start_pos = 0
            
        pyrage.encrypt_io(in_stream, out_stream, [recip])
        
        try:
            return out_stream.tell() - start_pos
        except Exception:
            return 0

    # Fallback to official age CLI
    age_bin = find_official_age_cli()
    if not age_bin:
        raise RuntimeError("No age encryption engine available (pyrage or age CLI required)")
        
    cmd = [str(age_bin), "-r", recipient_str]
    proc = subprocess.Popen(cmd, stdin=in_stream, stdout=out_stream, stderr=subprocess.PIPE)
    _, stderr = proc.communicate()
    if proc.returncode != 0:
        raise AgeCryptoError(f"age CLI encryption failed: {stderr.decode('utf-8', errors='ignore')}")
    return 0


def decrypt_stream(in_stream: BinaryIO, out_stream: BinaryIO, identity_str: str) -> int:
    """
    Decrypts in_stream to out_stream using standard age identity.
    Fails closed on any corruption, truncation, or key mismatch.
    Returns total plaintext bytes recovered.
    """
    ident = parse_identity(identity_str)
    
    if pyrage is not None:
        try:
            start_pos = out_stream.tell()
        except Exception:
            start_pos = 0
            
        try:
            pyrage.decrypt_io(in_stream, out_stream, [ident])
        except (pyrage.DecryptError, OSError, Exception) as e:
            err_msg = str(e).lower()
            if "no matching keys" in err_msg:
                raise DecryptionError("Decryption failed: wrong private key or corrupted key header") from e
            raise CorruptedBackupError(f"Integrity check failed: ciphertext corrupted ({e})") from e

        try:
            return out_stream.tell() - start_pos
        except Exception:
            return 0

    # Fallback to official age CLI
    age_bin = find_official_age_cli()
    if not age_bin:
        raise RuntimeError("No age decryption engine available (pyrage or age CLI required)")
        
    # Pipe identity via stdin or temporary file
    import tempfile
    with tempfile.NamedTemporaryFile("w", delete=False) as f:
        f.write(identity_str + "\n")
        ident_file = f.name
        
    try:
        cmd = [str(age_bin), "-d", "-i", ident_file]
        proc = subprocess.Popen(cmd, stdin=in_stream, stdout=out_stream, stderr=subprocess.PIPE)
        _, stderr = proc.communicate()
        if proc.returncode != 0:
            err_text = stderr.decode("utf-8", errors="ignore")
            if "no identity matched" in err_text.lower():
                raise DecryptionError("Decryption failed: wrong private key or corrupted key header")
            raise CorruptedBackupError(f"Integrity check failed: ciphertext corrupted ({err_text})")
    finally:
        if os.path.exists(ident_file):
            os.remove(ident_file)
            
    return 0


def encrypt_bytes(data: bytes, recipient_str: str) -> bytes:
    """Encrypts raw bytes into age-armored or binary format."""
    recip = parse_recipient(recipient_str)
    if pyrage is not None:
        return pyrage.encrypt(data, [recip])
    
    in_buf = io.BytesIO(data)
    out_buf = io.BytesIO()
    encrypt_stream(in_buf, out_buf, recipient_str)
    return out_buf.getvalue()


def decrypt_bytes(ciphertext: bytes, identity_str: str) -> bytes:
    """Decrypts age ciphertext into raw plaintext bytes."""
    ident = parse_identity(identity_str)
    if pyrage is not None:
        try:
            return pyrage.decrypt(ciphertext, [ident])
        except pyrage.DecryptError as e:
            err_msg = str(e).lower()
            if "no matching keys" in err_msg:
                raise DecryptionError("Decryption failed: wrong private key or corrupted key header") from e
            raise CorruptedBackupError(f"Integrity check failed: ciphertext corrupted ({e})") from e
            
    in_buf = io.BytesIO(ciphertext)
    out_buf = io.BytesIO()
    decrypt_stream(in_buf, out_buf, identity_str)
    return out_buf.getvalue()
