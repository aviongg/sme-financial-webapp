"""Configuration and executable shell-boundary checks; NOT container acceptance.

Run: python -m unittest discover -s scripts -p test_production_configuration.py -v
Requires PyYAML. POSIX shell tests use sh/bash, or FINSIGHT_TEST_SH on Windows.
Image builds, real PostgreSQL TLS/roles, nginx -t and persistent uploads must
still be exercised against the final containers; see docs/PRODUCTION_RUNBOOK.md.
"""
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest

import yaml

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8-sig")


def posix_path(path):
    value = str(path).replace("\\", "/")
    if os.name == "nt" and re.match(r"^[A-Za-z]:/", value):
        return "/" + value[0].lower() + value[2:]
    return value


class ProductionConfigurationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compose = yaml.safe_load(read("docker-compose.prod.yml"))
        cls.services = cls.compose["services"]

    def test_only_edge_publishes_ports(self):
        publishers = {name for name, service in self.services.items() if service.get("ports")}
        self.assertEqual({"nginx"}, publishers)
        self.assertTrue(self.compose["networks"]["finsight-db-net"]["internal"])
        self.assertEqual({"finsight-db-net"}, set(self.services["postgres"]["networks"]))

    def test_secret_files_are_service_scoped(self):
        runtime_secrets = set(self.services["backend-runtime"]["secrets"])
        self.assertEqual({"app_db_password", "crypto_key_k1", "ocr_service_key", "smtp_password"}, runtime_secrets)
        self.assertEqual(["migrator_db_password"], self.services["backend-migration"]["secrets"])
        for name, service in self.services.items():
            if name != "postgres":
                self.assertNotIn("dba_db_password", service.get("secrets", []))
            for key, value in service.get("environment", {}).items():
                if "PASSWORD" in key:
                    self.assertTrue(key.endswith("_FILE"), f"Literal password environment in {name}")
        ignored = read(".dockerignore").splitlines()
        for sensitive in ("secrets", "certs", "**/.env", "**/*.key", "**/*.pem"):
            self.assertIn(sensitive, ignored)

    def test_fresh_postgres_init_has_secret_and_explicit_tls_hba(self):
        postgres = self.services["postgres"]
        self.assertEqual("/run/secrets/dba_db_password", postgres["environment"]["POSTGRES_PASSWORD_FILE"])
        self.assertNotIn("POSTGRES_PASSWORD", postgres["environment"])
        self.assertEqual(["CMD", "pg_isready", "-h", "postgres.finsight.internal", "-U", "finsight_app", "-d", "sme_health"],
                         postgres["healthcheck"]["test"])
        self.assertFalse(any("pg_hba" in volume for volume in postgres["volumes"]))
        dockerfile = read("docker/Dockerfile.postgres")
        for required in ("hba_file=/etc/postgresql/pg_hba.conf", "ssl=on", "ssl_min_protocol_version=TLSv1.2"):
            self.assertIn(required, dockerfile)
        entrypoint = read("docker/postgres/entrypoint.sh")
        self.assertIn("install -m 0600 -o postgres -g postgres", entrypoint)
        self.assertIn('exec /usr/local/bin/docker-entrypoint.sh "$@"', entrypoint)

    def test_hba_authenticates_only_socket_dba_and_tls_runtime_roles(self):
        rules = [line.split() for line in read("docker/postgres/pg_hba.prod.conf").splitlines()
                 if line.strip() and not line.lstrip().startswith("#")]
        self.assertEqual(["local", "all", "finsight_dba", "scram-sha-256"], rules[0])
        for rule in rules[1:4]:
            self.assertEqual("hostssl", rule[0])
            self.assertEqual("172.28.10.0/24", rule[3])
            self.assertEqual("scram-sha-256", rule[4])
        self.assertEqual({"finsight_migrator", "finsight_app", "finsight_backup"}, {rule[2] for rule in rules[1:4]})
        self.assertEqual(["hostnossl", "all", "all", "all", "reject"], rules[4])
        self.assertEqual(["host", "all", "all", "all", "reject"], rules[5])

    def test_role_bootstrap_has_no_password_literal_or_runtime_ddl(self):
        script = read("docker/postgres/init/10-roles.sh")
        for role in ("migrator", "app", "backup"):
            self.assertIn(f"/run/secrets/{role}_db_password", script)
            self.assertRegex(script, rf"ALTER ROLE finsight_{role} WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS PASSWORD :'")
        self.assertIn("ALTER SCHEMA public OWNER TO finsight_migrator", script)
        self.assertIn("CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public", script)
        self.assertIn("REVOKE ALL ON DATABASE sme_health FROM PUBLIC", script)
        self.assertNotRegex(script, r"GRANT.*CREATE.*TO finsight_(app|backup)")
        self.assertNotRegex(script, r"GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES")
        self.assertNotRegex(read("scripts/provision_backup_role.sql"), r"PASSWORD\s+'[^']+'")

    def test_backend_build_source_is_relative_to_backend_workdir(self):
        dockerfile = read("docker/Dockerfile.backend")
        self.assertIn("WORKDIR /workspace/app/backend", dockerfile)
        self.assertIn("COPY backend/src ./src", dockerfile)
        self.assertIn("USER finsight:finsight", dockerfile)
        self.assertTrue((ROOT / "frontend/public/.gitkeep").is_file())

    def test_storage_initialization_precedes_nonroot_backend(self):
        backend = self.services["backend-runtime"]
        storage = self.services["storage-init"]
        self.assertEqual("/storage/documents", backend["environment"]["DOCUMENTS_STORAGE_DIR"])
        self.assertIn("document_storage:/storage", backend["volumes"])
        self.assertIn("document_storage:/storage", storage["volumes"])
        self.assertEqual("none", storage["network_mode"])
        self.assertEqual("service_completed_successfully", backend["depends_on"]["storage-init"]["condition"])
        self.assertIn("10001:10001", storage["command"][-1])
        self.assertNotIn("user", backend)

    def test_ocr_hostname_secret_and_credentials_match_both_sides(self):
        backend = self.services["backend-runtime"]["environment"]
        ai = self.services["ai-service"]
        self.assertEqual("true", backend["OCR_INTEGRATION_ENABLED"])
        self.assertEqual("https://ai.finsight.internal:8000", backend["OCR_SERVICE_URL"])
        self.assertIn("ai.finsight.internal", ai["networks"]["finsight-ai-net"]["aliases"])
        self.assertEqual(backend["OCR_SERVICE_KEY_FILE"], ai["environment"]["OCR_SERVICE_SECRET_FILE"])
        self.assertEqual("/run/secrets/google_vision_credentials", ai["environment"]["GOOGLE_APPLICATION_CREDENTIALS"])
        self.assertIn("google_vision_credentials", ai["secrets"])
        self.assertEqual("true", ai["environment"]["AI_REQUIRE_TLS"])
        self.assertNotIn("APP_OCR_URL", backend)

    def test_provider_egress_is_restricted_to_ai_proxy(self):
        networks = self.compose["networks"]
        self.assertTrue(networks["finsight-ai-net"]["internal"])
        self.assertTrue(networks["finsight-ai-egress-net"]["internal"])
        provider_clients = {name for name, service in self.services.items()
                            if "finsight-provider-egress-net" in service.get("networks", {})}
        proxy_clients = {name for name, service in self.services.items()
                         if "finsight-ai-egress-net" in service.get("networks", {})}
        self.assertEqual({"ocr-egress-proxy"}, provider_clients)
        self.assertEqual({"ai-service", "ocr-egress-proxy"}, proxy_clients)
        proxy = read("docker/ocr-egress/squid.conf")
        self.assertIn("acl tls_port port 443", proxy)
        self.assertIn("acl provider_hosts dstdomain vision.googleapis.com oauth2.googleapis.com", proxy)
        self.assertIn("http_access allow ai_clients connect_method tls_port provider_hosts", proxy)
        self.assertIn("http_access deny all", proxy)

    def test_nginx_template_preserves_nonce_and_runtime_variables(self):
        template = read("docker/nginx/templates/finsight.conf.template")
        self.assertEqual({"APP_DOMAIN"}, set(re.findall(r"\$\{([^}]+)\}", template)))
        self.assertNotIn("APP_DOMAIN:-", template)
        self.assertIn("server_name ${APP_DOMAIN};", template)
        self.assertIn("return 301 https://$host$request_uri;", template)
        self.assertIn("proxy_set_header x-nonce $request_id;", template)
        self.assertIn("'nonce-$request_id'", template)
        self.assertEqual("true", self.services["frontend-runtime"]["environment"]["TRUST_INGRESS_NONCE"])
        self.assertIn("ENV NGINX_ENVSUBST_FILTER=^APP_DOMAIN$", read("docker/Dockerfile.nginx"))
        self.assertNotIn("NGINX_ENVSUBST_FILTER", self.services["nginx"]["environment"])
        self.assertIn("nginx -t", read("docker/nginx/99-validate-config.sh"))
        self.assertEqual("https://${APP_DOMAIN:?Set APP_DOMAIN to the public HTTPS hostname}",
                         self.services["backend-runtime"]["environment"]["APP_PUBLIC_ORIGIN"])


class ProductionEntrypointTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.shell = os.environ.get("FINSIGHT_TEST_SH") or shutil.which("sh") or shutil.which("bash")
        if not cls.shell:
            raise unittest.SkipTest("POSIX shell unavailable; entrypoint execution remains pending")

    def run_domain(self, domain, filter_value="^APP_DOMAIN$"):
        env = os.environ | {"APP_DOMAIN": domain, "NGINX_ENVSUBST_FILTER": filter_value}
        return subprocess.run([self.shell, "docker/nginx/19-validate-domain.sh"], cwd=ROOT, env=env,
                              capture_output=True, text=True)

    def test_domain_accepts_dns_names(self):
        for domain in ("closure.example.test", "localhost", "xn--bcher-kva.example", "app-1.example"):
            with self.subTest(domain=domain):
                result = self.run_domain(domain)
                self.assertEqual(0, result.returncode, result.stderr)

    def test_domain_rejects_empty_urls_ports_newlines_and_config_injection(self):
        for domain in ("", "https://example.test", "example.test:443", "example.test; return 200;",
                       "example.test\nserver", "example.test\n", "-app.test", "app..test", "app.test.",
                       "x" * 64 + ".test"):
            with self.subTest(domain=repr(domain)):
                self.assertNotEqual(0, self.run_domain(domain).returncode)

    def test_domain_rejects_broad_substitution_filter(self):
        self.assertNotEqual(0, self.run_domain("closure.example.test", ".*").returncode)

    def test_ai_missing_tls_key_fails_before_starting_uvicorn(self):
        env = os.environ | {"AI_REQUIRE_TLS": "true", "AI_SSL_CERTFILE": "/missing/cert",
                            "AI_SSL_KEYFILE": "/missing/key"}
        result = subprocess.run([self.shell, "docker/entrypoint-ai.sh"], cwd=ROOT, env=env,
                                capture_output=True, text=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("AI TLS certificate and private key are required", result.stderr)

    def test_ai_tls_arguments_preserve_paths_with_spaces(self):
        with tempfile.TemporaryDirectory(prefix="finsight-entrypoint-") as directory:
            path = Path(directory)
            executable = path / "uvicorn"
            executable.write_text("#!/bin/sh\nprintf 'ARG:%s\\n' \"$@\"\n", encoding="utf-8", newline="\n")
            executable.chmod(0o755)
            for filename in ("test certificate.crt", "test private.key"):
                (path / filename).write_text("test-only placeholder", encoding="utf-8")
            cert, key = (posix_path(path / filename) for filename in ("test certificate.crt", "test private.key"))
            env = os.environ | {"AI_REQUIRE_TLS": "true", "AI_SSL_CERTFILE": cert, "AI_SSL_KEYFILE": key}
            result = subprocess.run([self.shell, "-c", 'export PATH="$1:$PATH"; exec sh "$2"',
                                     "test", posix_path(path), "docker/entrypoint-ai.sh"],
                                    cwd=ROOT, env=env, capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual(["app.main:app", "--host", "0.0.0.0", "--port", "8000",
                              "--ssl-certfile", cert, "--ssl-keyfile", key],
                             [line.removeprefix("ARG:") for line in result.stdout.splitlines()])


if __name__ == "__main__":
    unittest.main()
