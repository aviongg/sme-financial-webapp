"""Local HTTPS fixture for the real Java client; only the OCR provider is fake.

Started by OcrFastApiClosureProbe. Disposable keys/certs live in its temp dir.
Never import this fixture in a production service entry point.
"""

import argparse
import ipaddress
import json
import secrets
import socket
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

import uvicorn
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID, ExtendedKeyUsageOID

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from app.config import Settings
from app.main import create_app
from app.models import NormalizedOcrResult


def generate_ca(name):
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    subject = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, name)])
    now = datetime.now(timezone.utc)
    cert = (x509.CertificateBuilder().subject_name(subject).issuer_name(subject)
            .public_key(key.public_key()).serial_number(x509.random_serial_number())
            .not_valid_before(now - timedelta(minutes=5)).not_valid_after(now + timedelta(days=1))
            .add_extension(x509.BasicConstraints(ca=True, path_length=0), critical=True)
            .sign(key, hashes.SHA256()))
    return key, cert


def create_certificates(folder):
    ca_key, ca = generate_ca("Disposable FinSight OCR test CA")
    _, wrong_ca = generate_ca("Untrusted FinSight OCR test CA")
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    now = datetime.now(timezone.utc)
    cert = (x509.CertificateBuilder()
            .subject_name(x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "ai.finsight.internal")]))
            .issuer_name(ca.subject).public_key(key.public_key())
            .serial_number(x509.random_serial_number())
            .not_valid_before(now - timedelta(minutes=5)).not_valid_after(now + timedelta(days=1))
            .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
            .add_extension(x509.SubjectAlternativeName([
                x509.DNSName("ai.finsight.internal"), x509.DNSName("localhost"),
                x509.IPAddress(ipaddress.ip_address("127.0.0.1"))]), critical=False)
            .add_extension(x509.ExtendedKeyUsage([ExtendedKeyUsageOID.SERVER_AUTH]), critical=False)
            .sign(ca_key, hashes.SHA256()))
    for name, certificate in (("ca.crt", ca), ("wrong-ca.crt", wrong_ca), ("server.crt", cert)):
        (folder / name).write_bytes(certificate.public_bytes(serialization.Encoding.PEM))
    key_path = folder / "server.key"
    key_path.write_bytes(key.private_bytes(serialization.Encoding.PEM,
                                         serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
    key_path.chmod(0o600)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path, required=True)
    args = parser.parse_args()
    folder = args.directory
    folder.mkdir(parents=True, exist_ok=True)
    create_certificates(folder)
    secret_file = folder / "ocr_service_key"
    secret_file.write_text(secrets.token_urlsafe(32), encoding="utf-8")
    secret_file.chmod(0o600)
    settings = Settings.from_env({"OCR_SERVICE_SECRET_FILE": str(secret_file)})

    class FixtureProvider:
        calls = 0

        def extract(self, document):
            self.calls += 1
            (folder / "provider-calls.txt").write_text(str(self.calls), encoding="utf-8")
            if document.mime_type != "image/png" or not document.content.startswith(b"\x89PNG"):
                raise AssertionError("Real document inspector did not provide the expected PNG")
            return NormalizedOcrResult("INVOICE\nInvoice date: 2026-09-19\nTotal: 1250.50", .96)

    provider = FixtureProvider()
    # Real FastAPI auth, multipart parsing, document inspection and extraction rules.
    api = create_app(settings, provider=provider)
    listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    listener.bind(("127.0.0.1", 0))
    port = listener.getsockname()[1]

    class ReadyServer(uvicorn.Server):
        async def startup(self, sockets=None):
            await super().startup(sockets=sockets)
            if self.started:
                (folder / "endpoint.txt").write_text(f"https://127.0.0.1:{port}", encoding="utf-8")

    config = uvicorn.Config(api, host="127.0.0.1", port=port,
                            ssl_keyfile=str(folder / "server.key"), ssl_certfile=str(folder / "server.crt"),
                            access_log=False, log_level="warning")
    ReadyServer(config).run(sockets=[listener])


if __name__ == "__main__":
    main()
