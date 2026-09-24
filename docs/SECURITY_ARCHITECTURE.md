# FinSight Security Architecture (Phases S1 - S9)

## 1. Executive Summary & Security Objectives
FinSight is an SME Financial Health Platform designed with defense-in-depth security principles. The platform enforces strict boundaries across client authentication, tenant isolation, transport encryption, database privilege separation, append-only security auditing, and offline-key encrypted disaster recovery.

---

## 2. Logical Production Topology & Network Segmentation (S8)

```
                    Internet (HTTPS Only)
                              |
                     [ Host Ports 80, 443 ]
                              |
               +------------------------------+
               |      Nginx Edge Ingress      |
               | (TLS 1.2/1.3, CSP, HSTS, L-A)|
               +------------------------------+
                              |
                    [ finsight-web-net ]
                 (172.28.20.0/24 - Internal)
                    /                    \
                   /                      \
+-----------------------+     +-------------------------+
|  Next.js Frontend     |     |   Spring Boot Backend   |
| (Port 3000, Internal) |     |  (Port 8080, Internal)  |
+-----------------------+     +-------------------------+
                                    |                \
                     [ finsight-ai-net ]              [ finsight-db-net ]
                 (172.28.30.0/24 - Internal)     (172.28.10.0/24 - Internal)
                            |                                |
               +-----------------------+        +--------------------------+
               |  FastAPI OCR Service  |        |    PostgreSQL 17 DB      |
               | (TLS, Secret Auth)    |        | (SCRAM, TLS verify-full) |
               +-----------------------+        +--------------------------+
```

### Network Isolation Invariants:
1. **Public Edge**: Only Nginx publishes host ports (80 HTTP redirect -> 443 HTTPS).
2. **finsight-web-net**: Connects Nginx, Next.js, and Spring Boot. Nginx routes `/` to Next.js and `/api/` to Spring Boot.
3. **finsight-ai-net**: Strictly isolated internal bridge connecting only Spring Boot and FastAPI. Plaintext external access is prohibited.
4. **finsight-db-net**: Strictly isolated internal bridge connecting PostgreSQL, Spring Boot, one-shot migration container, and backup runner. No direct access from Nginx, Next.js, or external hosts.

---

## 3. Core Security Pillars (S1 - S9)

### S1: Authentication & Session Security
- **Password Hashing**: Argon2id with 64MB memory cost, 3 iterations, and parallelism of 1.
- **Session Management**: Server-backed JDBC session store (`spring_session`).
- **Session Invariants**:
  - Production Cookie Name: `__Host-FINSIGHT_SESSION` (RFC 6265bis: `Secure=true`, `Path=/`, no `Domain`).
  - Fixation protection via `changeSessionId()` upon authentication.
  - Inactivity timeout: 30 minutes.
  - Absolute session maximum lifetime: 8 hours (`SessionMaxLifetimeFilter`).

### S2 & S3: Multi-Tenant Context & Authorization
- **Tenant Isolation**: All tenant resources are scoped by immutable UUIDs (`business_id`).
- **Membership Roles**: Strict RBAC per tenant (`OWNER`, `ADMIN`, `MEMBER`, `VIEWER`).
- **Horizontal & Vertical IDOR Protection**: Business authorization checks verify active user membership before executing operations.
- **Platform Separation**: Platform administrators (`SUPER_ADMIN`, `SUPPORT_ADMIN`) are isolated from tenant business data. Platform APIs strictly require platform role authority.

### S4: Document & OCR Trust Boundary
- **Upload Validation**: Enforced 10MB maximum request size, MIME type allowlist, magic-byte inspection (PDF, PNG, JPEG), and maximum 5-page PDF limit.
- **Path Traversal Protection**: Filenames sanitized to UUIDs; storage directories strictly separated.
- **Service Authentication**: Internal requests to OCR service require `X-OCR-Service-Key` header with constant-time HMAC comparison.

### S5: Persistent Cryptographic Protection
- **Field-Level Encryption**: Sensitive PII and TOTP secrets encrypted with AES-256-GCM.
- **Envelope Format**: `enc:<key_id>:<base64_iv>:<base64_ciphertext>:<base64_tag>`.
- **Keyring Lifecycle**: Active key ID with retained older keys supporting seamless zero-downtime rotation.

### S6: PostgreSQL Least Privilege & Transport Security
- **Four-Plane Role Architecture**:
  1. `finsight_dba`: Superuser / schema owner, restricted to local container Unix socket only.
  2. `finsight_migrator`: DDL only via one-shot Flyway runner.
  3. `finsight_app`: DML only (`SELECT`, `INSERT`, `UPDATE`, `DELETE`) without DDL permissions.
  4. `finsight_backup`: Read-only `SELECT` privileges only; all mutations denied.
- **Transport Security**: Database connections enforce `hostssl` with SCRAM-SHA-256 authentication and `verify-full` root CA verification.

### S7: Security Auditing, Platform Admin & Mandatory MFA
- **Append-Only Security Audit**: Database triggers (`trg_audit_no_truncate`, `trg_audit_no_update_delete`) prohibit mutation or deletion of audit logs.
- **Platform Role Protection**: Database trigger `trg_protect_platform_role` restricts role modifications to authorized DBA processes.
- **Multi-Factor Authentication**: RFC 6238 TOTP with encrypted secret storage, single-use recovery codes, and strict 5-failure challenge rate limiting.

### S8: Edge Hardening & Abuse Protection
- **Nginx Ingress**: TLS 1.2 and TLS 1.3 only; HSTS (`max-age=31536000`); Content Security Policy without `unsafe-eval`; clickjacking protection (`X-Frame-Options: DENY`); MIME sniffing protection (`nosniff`).
- **HTTP Method Hardening**: Non-essential methods (`TRACE`, `TRACK`, `CONNECT`) rejected.
- **Layered Rate Limiting**:
  - *Layer A (Nginx IP-based)*: 5 req/min on authentication surfaces (`/login`, `/register`, `/password-reset`, `/mfa`), 10 req/min on `/upload`, 30 req/s global API burst.
  - *Layer B (Spring Identity-based)*: In-memory bounded cache with SHA-256 derived keys limiting login attempts and password reset requests per account identifier.
- **Trusted Proxy Validation**: `TrustedProxyValidationFilter` checks immediate TCP peer; forwarding headers (`X-Real-IP`, `X-Forwarded-For`) are trusted only when arriving from configured internal proxy CIDRs.
- **Internal AI Transport**: Mutual TLS transport between Spring Boot and FastAPI with internal CA verification.

### S9: Encrypted Backup & Disaster Recovery
- **Dedicated Backup Role**: `finsight_backup` holds only `SELECT` privileges; all DDL and DML write permissions are denied.
- **Owner-Held Offline Key**: Public-key encryption architecture using X25519 (`age`). The backup server holds only the public recipient (`age1...`); the private identity (`AGE-SECRET-KEY-1...`) remains offline.
- **Streaming Pipeline**: `pg_dump` streams directly into age encryption; zero plaintext logical dump touches disk.
- **S5 Keyring Recovery Bundle**: Application encryption keys are backed up in a separate in-memory encrypted bundle, ensuring persistent ciphertext remains decryptable after restore.
- **Post-Restore Ephemeral Data Purge**: Restore tooling automatically invalidates active Spring sessions, password reset tokens, and pending MFA enrollments.
- **Validated Disaster Recovery**: Full restore drill verified against disposable PostgreSQL instances.

---

## 4. Operational Invariants & External Deployment Dependencies

| Control Domain | Implementation State | External Deployment Requirement |
| :--- | :--- | :--- |
| Edge TLS Certificate | Mount-based secrets (`/etc/nginx/certs/edge.*`) | Production public CA certificates (e.g., Let's Encrypt / Certbot or corporate PKI) |
| Offline Backup Key | Ephemeral drill / CLI tooling | Owner offline hardware security module (HSM) or offline paper/encrypted key storage |
| Backup Destination | Configurable destination path | Offsite cloud storage replication (e.g., AWS S3 bucket with Object Lock or encrypted NAS) |
| Application Keyring | File/environment-based (`/run/secrets/crypto_key_*`) | Production HashiCorp Vault or AWS KMS / GCP Cloud KMS integration |
| Multi-Node Limiting | Single-node in-memory bounded limiter | Distributed Redis rate-limiter if horizontal scaling beyond single Spring instance occurs |
