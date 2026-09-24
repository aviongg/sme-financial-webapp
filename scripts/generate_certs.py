import os
import datetime
import ipaddress
from cryptography import x509
from cryptography.x509.oid import NameOID, ExtendedKeyUsageOID
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives import serialization

def generate_key():
    return rsa.generate_private_key(
        public_exponent=65537,
        key_size=2048
    )

def save_key(key, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(key.private_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PrivateFormat.TraditionalOpenSSL,
            encryption_algorithm=serialization.NoEncryption()
        ))

def save_cert(cert, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(cert.public_bytes(serialization.Encoding.PEM))

def create_ca(common_name, key):
    subject = issuer = x509.Name([
        x509.NameAttribute(NameOID.COUNTRY_NAME, "PK"),
        x509.NameAttribute(NameOID.ORGANIZATION_NAME, "FinSight Security"),
        x509.NameAttribute(NameOID.COMMON_NAME, common_name),
    ])
    now = datetime.datetime.now(datetime.timezone.utc)
    cert = (
        x509.CertificateBuilder()
        .subject_name(subject)
        .issuer_name(issuer)
        .public_key(key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now - datetime.timedelta(days=1))
        .not_valid_after(now + datetime.timedelta(days=3650))
        .add_extension(
            x509.BasicConstraints(ca=True, path_length=None),
            critical=True,
        )
        .add_extension(
            x509.KeyUsage(
                digital_signature=True,
                content_commitment=False,
                key_encipherment=False,
                data_encipherment=False,
                key_agreement=False,
                key_cert_sign=True,
                crl_sign=True,
                encipher_only=False,
                decipher_only=False,
            ),
            critical=True,
        )
        .add_extension(
            x509.SubjectKeyIdentifier.from_public_key(key.public_key()),
            critical=False,
        )
        .sign(key, hashes.SHA256())
    )
    return cert

def create_server_cert(common_name, san_dns_list, san_ip_list, server_key, ca_cert, ca_key):
    subject = x509.Name([
        x509.NameAttribute(NameOID.COUNTRY_NAME, "PK"),
        x509.NameAttribute(NameOID.ORGANIZATION_NAME, "FinSight"),
        x509.NameAttribute(NameOID.COMMON_NAME, common_name),
    ])
    now = datetime.datetime.now(datetime.timezone.utc)
    san_names = [x509.DNSName(dns) for dns in san_dns_list]
    for ip_str in san_ip_list:
        san_names.append(x509.IPAddress(ipaddress.ip_address(ip_str)))

    builder = (
        x509.CertificateBuilder()
        .subject_name(subject)
        .issuer_name(ca_cert.subject)
        .public_key(server_key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now - datetime.timedelta(days=1))
        .not_valid_after(now + datetime.timedelta(days=3650))
        .add_extension(
            x509.BasicConstraints(ca=False, path_length=None),
            critical=True,
        )
        .add_extension(
            x509.KeyUsage(
                digital_signature=True,
                content_commitment=False,
                key_encipherment=True,
                data_encipherment=False,
                key_agreement=False,
                key_cert_sign=False,
                crl_sign=False,
                encipher_only=False,
                decipher_only=False,
            ),
            critical=True,
        )
        .add_extension(
            x509.ExtendedKeyUsage([ExtendedKeyUsageOID.SERVER_AUTH]),
            critical=False,
        )
        .add_extension(
            x509.SubjectAlternativeName(san_names),
            critical=False,
        )
        .add_extension(
            x509.AuthorityKeyIdentifier.from_issuer_public_key(ca_key.public_key()),
            critical=False,
        )
    )
    cert = builder.sign(ca_key, hashes.SHA256())
    return cert

def main():
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    certs_dir = os.path.join(base_dir, "certs")
    fixtures_dir = os.path.join(certs_dir, "test-fixtures")
    os.makedirs(fixtures_dir, exist_ok=True)

    print("Generating FinSight Root CA...")
    ca_key = generate_key()
    ca_cert = create_ca("FinSight Internal Root CA", ca_key)
    save_key(ca_key, os.path.join(certs_dir, "postgres-ca.key"))
    save_cert(ca_cert, os.path.join(certs_dir, "postgres-ca.crt"))

    print("Generating PostgreSQL Server Certificate...")
    server_key = generate_key()
    server_cert = create_server_cert(
        "postgres.finsight.internal",
        ["postgres.finsight.internal", "postgres", "localhost"],
        ["127.0.0.1"],
        server_key,
        ca_cert,
        ca_key
    )
    save_key(server_key, os.path.join(certs_dir, "postgres-server.key"))
    save_cert(server_cert, os.path.join(certs_dir, "postgres-server.crt"))

    print("Generating Rogue CA & Server Cert (for negative tests)...")
    rogue_ca_key = generate_key()
    rogue_ca_cert = create_ca("Rogue Untrusted CA", rogue_ca_key)
    save_key(rogue_ca_key, os.path.join(fixtures_dir, "rogue-ca.key"))
    save_cert(rogue_ca_cert, os.path.join(fixtures_dir, "rogue-ca.crt"))

    rogue_server_key = generate_key()
    rogue_server_cert = create_server_cert(
        "postgres.finsight.internal",
        ["postgres.finsight.internal", "postgres", "localhost"],
        ["127.0.0.1"],
        rogue_server_key,
        rogue_ca_cert,
        rogue_ca_key
    )
    save_key(rogue_server_key, os.path.join(fixtures_dir, "rogue-server.key"))
    save_cert(rogue_server_cert, os.path.join(fixtures_dir, "rogue-server.crt"))

    print("Generating Mismatched Hostname Server Cert (for negative tests)...")
    mismatched_key = generate_key()
    mismatched_cert = create_server_cert(
        "wrong.finsight.internal",
        ["wrong.finsight.internal", "unrelated.host.internal"],
        [],
        mismatched_key,
        ca_cert,
        ca_key
    )
    save_key(mismatched_key, os.path.join(fixtures_dir, "mismatched-server.key"))
    save_cert(mismatched_cert, os.path.join(fixtures_dir, "mismatched-server.crt"))

    print("Certificates successfully generated in:", certs_dir)

if __name__ == "__main__":
    main()
