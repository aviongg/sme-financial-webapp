"""FinSight S9 Cryptographic Backup Engine (age / X25519 specification)

Implements public-key encrypted logical backup streams based on X25519 and ChaCha20-Poly1305.
- The backup server stores ONLY the public age recipient (age1...).
- The owner keeps the private age identity (AGE-SECRET-KEY-1...) offline.
- Decryption requires the offline private identity.
- Every chunk is authenticated; any corruption or tampering fails closed.
"""

import io
import os
import struct
from typing import BinaryIO, Tuple

from cryptography.exceptions import InvalidSignature, InvalidTag
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric.x25519 import X25519PrivateKey, X25519PublicKey
from cryptography.hazmat.primitives.ciphers.aead import ChaCha20Poly1305
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.hazmat.primitives.serialization import Encoding, NoEncryption, PrivateFormat, PublicFormat

BECH32_CHARSET = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"
MAGIC_HEADER = b"finsight-age-v1\n"
CHUNK_SIZE = 64 * 1024


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
# Bech32 Encoding / Decoding
# -----------------------------------------------------------------------------

def _bech32_polymod(values):
    generator = [0x3B6A57B2, 0x26508E6D, 0x1EA119FA, 0x3D4233DD, 0x2A1462B3]
    chk = 1
    for value in values:
        top = chk >> 25
        chk = (chk & 0x1FFFFFF) << 5 ^ value
        for i in range(5):
            chk ^= generator[i] if ((top >> i) & 1) else 0
    return chk


def _bech32_hrp_expand(hrp):
    return [ord(x) >> 5 for x in hrp] + [0] + [ord(x) & 31 for x in hrp]


def _bech32_create_checksum(hrp, data):
    values = _bech32_hrp_expand(hrp) + data
    polymod = _bech32_polymod(values + [0, 0, 0, 0, 0, 0]) ^ 1
    return [(polymod >> 5 * (5 - i)) & 31 for i in range(6)]


def _bech32_verify_checksum(hrp, data):
    return _bech32_polymod(_bech32_hrp_expand(hrp) + data) == 1


def _convertbits(data, frombits, tobits, pad=True):
    acc = 0
    bits = 0
    ret = []
    maxv = (1 << tobits) - 1
    max_acc = (1 << (frombits + tobits - 1)) - 1
    for value in data:
        acc = ((acc << frombits) | value) & max_acc
        bits += frombits
        while bits >= tobits:
            bits -= tobits
            ret.append((acc >> bits) & maxv)
    if pad:
        if bits:
            ret.append((acc << (tobits - bits)) & maxv)
    elif bits >= frombits or ((acc << (tobits - bits)) & maxv):
        return None
    return ret


def encode_bech32(hrp: str, data: bytes) -> str:
    converted = _convertbits(list(data), 8, 5)
    combined = converted + _bech32_create_checksum(hrp, converted)
    return hrp + "1" + "".join([BECH32_CHARSET[d] for d in combined])


def decode_bech32(bech: str) -> Tuple[str, bytes]:
    bech = bech.strip().lower()
    pos = bech.rfind("1")
    if pos < 1 or pos + 7 > len(bech):
        raise InvalidKeyError(f"Invalid bech32 key format: {bech[:12]}...")
    hrp = bech[:pos]
    data = []
    for c in bech[pos + 1:]:
        d = BECH32_CHARSET.find(c)
        if d == -1:
            raise InvalidKeyError(f"Invalid character in key: {c}")
        data.append(d)
    if not _bech32_verify_checksum(hrp, data):
        raise InvalidKeyError("Key checksum verification failed")
    decoded = _convertbits(data[:-6], 5, 8, pad=False)
    if decoded is None or len(decoded) != 32:
        raise InvalidKeyError(f"Decoded key length must be 32 bytes, got {len(decoded) if decoded else 0}")
    return hrp, bytes(decoded)


# -----------------------------------------------------------------------------
# Key Pair Generation and Parsing
# -----------------------------------------------------------------------------

def generate_keypair() -> Tuple[str, str]:
    """Generates an offline owner age keypair (public recipient, private identity)."""
    priv = X25519PrivateKey.generate()
    pub = priv.public_key()

    priv_raw = priv.private_bytes(Encoding.Raw, PrivateFormat.Raw, NoEncryption())
    pub_raw = pub.public_bytes(Encoding.Raw, PublicFormat.Raw)

    recipient = encode_bech32("age", pub_raw)
    identity = encode_bech32("age-secret-key-", priv_raw).upper()
    return recipient, identity


def parse_recipient(recipient_str: str) -> X25519PublicKey:
    """Parses a public recipient string into an X25519PublicKey."""
    recipient_str = recipient_str.strip()
    if recipient_str.startswith("age1"):
        hrp, raw = decode_bech32(recipient_str)
        if hrp != "age":
            raise InvalidKeyError(f"Expected hrp 'age', got '{hrp}'")
        return X25519PublicKey.from_public_bytes(raw)
    try:
        # Fallback to 32-byte hex if provided
        raw = bytes.fromhex(recipient_str)
        if len(raw) == 32:
            return X25519PublicKey.from_public_bytes(raw)
    except Exception:
        pass
    raise InvalidKeyError(f"Unrecognized recipient format: {recipient_str[:12]}...")


def parse_identity(identity_str: str) -> X25519PrivateKey:
    """Parses a private identity string into an X25519PrivateKey."""
    identity_str = identity_str.strip()
    if identity_str.lower().startswith("age-secret-key-1"):
        hrp, raw = decode_bech32(identity_str)
        if hrp != "age-secret-key-":
            raise InvalidKeyError(f"Expected hrp 'age-secret-key-', got '{hrp}'")
        return X25519PrivateKey.from_private_bytes(raw)
    try:
        # Fallback to 32-byte hex if provided
        raw = bytes.fromhex(identity_str)
        if len(raw) == 32:
            return X25519PrivateKey.from_private_bytes(raw)
    except Exception:
        pass
    raise InvalidKeyError("Unrecognized identity format")


# -----------------------------------------------------------------------------
# Streaming Encryption and Decryption
# -----------------------------------------------------------------------------

def encrypt_stream(in_stream: BinaryIO, out_stream: BinaryIO, recipient_str: str) -> int:
    """
    Encrypts in_stream using recipient's public key and writes to out_stream.
    Returns total bytes written.
    """
    recipient_pub = parse_recipient(recipient_str)
    recipient_bytes = recipient_pub.public_bytes(Encoding.Raw, PublicFormat.Raw)

    # 1. Generate random file key and ephemeral keypair
    file_key = os.urandom(32)
    ephemeral_priv = X25519PrivateKey.generate()
    ephemeral_pub_bytes = ephemeral_priv.public_key().public_bytes(Encoding.Raw, PublicFormat.Raw)

    # 2. Key agreement and wrapping
    shared_secret = ephemeral_priv.exchange(recipient_pub)
    wrap_key = HKDF(
        hashes.SHA256(),
        32,
        None,
        b"finsight-age-v1/wrap" + ephemeral_pub_bytes + recipient_bytes
    ).derive(shared_secret)

    wrapped_file_key = ChaCha20Poly1305(wrap_key).encrypt(b"finsight-key", file_key, None)

    # 3. Write header
    out_stream.write(MAGIC_HEADER)
    out_stream.write(ephemeral_pub_bytes)  # 32 bytes
    out_stream.write(struct.pack(">I", len(wrapped_file_key)))  # 4 bytes
    out_stream.write(wrapped_file_key)

    bytes_written = len(MAGIC_HEADER) + 32 + 4 + len(wrapped_file_key)

    # 4. Stream data chunks
    cipher = ChaCha20Poly1305(file_key)
    chunk_index = 0

    while True:
        chunk = in_stream.read(CHUNK_SIZE)
        peek = in_stream.read(1)
        if peek:
            is_last = False
            in_stream.seek(-1, io.SEEK_CUR)
        else:
            is_last = True

        nonce = struct.pack(">Q", chunk_index) + b"\x00\x00\x00" + (b"\x01" if is_last else b"\x00")
        encrypted_chunk = cipher.encrypt(nonce, chunk, None)

        out_stream.write(struct.pack(">I", len(encrypted_chunk)))
        out_stream.write(encrypted_chunk)
        bytes_written += 4 + len(encrypted_chunk)

        if is_last:
            break
        chunk_index += 1

    return bytes_written


def decrypt_stream(in_stream: BinaryIO, out_stream: BinaryIO, identity_str: str) -> int:
    """
    Decrypts in_stream using offline owner's private identity and writes to out_stream.
    Returns total plaintext bytes recovered.
    Fails closed on any corruption, truncation, or key mismatch.
    """
    identity_priv = parse_identity(identity_str)
    recipient_pub = identity_priv.public_key()
    recipient_bytes = recipient_pub.public_bytes(Encoding.Raw, PublicFormat.Raw)

    # 1. Verify Header
    magic = in_stream.read(len(MAGIC_HEADER))
    if magic != MAGIC_HEADER:
        raise DecryptionError("Invalid magic header: not a FinSight encrypted backup file")

    ephem_pub_bytes = in_stream.read(32)
    if len(ephem_pub_bytes) != 32:
        raise CorruptedBackupError("Truncated header: missing ephemeral public key")

    len_bytes = in_stream.read(4)
    if len(len_bytes) != 4:
        raise CorruptedBackupError("Truncated header: missing wrapped key length")
    wrapped_len = struct.unpack(">I", len_bytes)[0]

    wrapped_file_key = in_stream.read(wrapped_len)
    if len(wrapped_file_key) != wrapped_len:
        raise CorruptedBackupError("Truncated header: incomplete wrapped key payload")

    # 2. Key agreement and unwrapping
    try:
        ephem_pub = X25519PublicKey.from_public_bytes(ephem_pub_bytes)
        shared_secret = identity_priv.exchange(ephem_pub)
        wrap_key = HKDF(
            hashes.SHA256(),
            32,
            None,
            b"finsight-age-v1/wrap" + ephem_pub_bytes + recipient_bytes
        ).derive(shared_secret)

        file_key = ChaCha20Poly1305(wrap_key).decrypt(b"finsight-key", wrapped_file_key, None)
    except (InvalidSignature, InvalidTag, ValueError) as e:
        raise DecryptionError("Decryption failed: wrong private key or corrupted key header") from e

    # 3. Stream data chunks
    cipher = ChaCha20Poly1305(file_key)
    chunk_index = 0
    total_plaintext_bytes = 0
    saw_final_chunk = False

    while True:
        len_buf = in_stream.read(4)
        if not len_buf:
            break
        if len(len_buf) < 4:
            raise CorruptedBackupError("Truncated chunk length encountered")

        chunk_len = struct.unpack(">I", len_buf)[0]
        enc_chunk = in_stream.read(chunk_len)
        if len(enc_chunk) != chunk_len:
            raise CorruptedBackupError("Truncated encrypted chunk encountered")

        # Peek ahead to determine if this is the final chunk in the stream
        peek = in_stream.read(4)
        if peek:
            is_last = False
            in_stream.seek(-len(peek), io.SEEK_CUR)
        else:
            is_last = True

        nonce = struct.pack(">Q", chunk_index) + b"\x00\x00\x00" + (b"\x01" if is_last else b"\x00")
        try:
            pt_chunk = cipher.decrypt(nonce, enc_chunk, None)
        except (InvalidSignature, InvalidTag) as e:
            raise CorruptedBackupError(f"Integrity check failed on chunk {chunk_index}: ciphertext corrupted") from e

        out_stream.write(pt_chunk)
        total_plaintext_bytes += len(pt_chunk)

        if is_last:
            saw_final_chunk = True
            break
        chunk_index += 1

    if not saw_final_chunk:
        raise CorruptedBackupError("Backup is truncated: end-of-stream flag was never reached")

    return total_plaintext_bytes


def encrypt_bytes(data: bytes, recipient_str: str) -> bytes:
    in_buf = io.BytesIO(data)
    out_buf = io.BytesIO()
    encrypt_stream(in_buf, out_buf, recipient_str)
    return out_buf.getvalue()


def decrypt_bytes(ciphertext: bytes, identity_str: str) -> bytes:
    in_buf = io.BytesIO(ciphertext)
    out_buf = io.BytesIO()
    decrypt_stream(in_buf, out_buf, identity_str)
    return out_buf.getvalue()
