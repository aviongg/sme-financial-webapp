# Storage & Volume Encryption Architecture (Phase S6)

## 1. Scope and Cryptographic Boundary

In FinSight Phase 1.5 S6, database credential security, SCRAM-SHA-256 authentication, TLS verify-full transport encryption, and three-plane database role isolation are enforced at the network and engine layer.

However, **Docker volumes do not provide data-at-rest encryption by default**. Storing database cluster data (`/var/lib/postgresql/data`) or uploaded documents (`/storage/documents`) in Docker named volumes or host directory mounts places data in unencrypted plaintext at the filesystem block layer of the underlying host operating system.

## 2. Infrastructure Deployment Requirements

To achieve cryptographic data-at-rest protection for persisted FinSight state, host infrastructure must implement block-level volume encryption:

### 2.1 On-Premises / Bare Metal / Dedicated Linux Hosts
- **Mechanism**: `dm-crypt` with **LUKS2** (Linux Unified Key Setup).
- **Cipher**: `aes-xts-plain64` with 512-bit key size (AES-256 equivalent in XTS mode).
- **Target Volumes**:
  1. PostgreSQL data directory: `/var/lib/postgresql/data` mounted on a LUKS-backed block device.
  2. Document storage directory: `/storage/documents` mounted on a LUKS-backed block device.
- **Key Management**: Keys must be managed via hardware TPM 2.0 (`systemd-cryptenroll`), network-bound disk encryption (Clevis/Tang), or external HSM.

### 2.2 Cloud Managed Infrastructure (AWS / GCP / Azure)
- **AWS**: Amazon EBS volumes encrypted by default using AWS KMS Customer Managed Keys (CMKs) with automatic key rotation enabled.
- **GCP**: Persistent Disks encrypted with Customer-Managed Encryption Keys (CMEK) via Google Cloud KMS.
- **Azure**: Azure Managed Disks with Server-Side Encryption (SSE) using Customer-Managed Keys (CMK) in Azure Key Vault.

## 3. Critical Security Boundaries and Constraints

> [!IMPORTANT]
> **Host volume encryption does NOT encrypt logical backups.**
> Block-level volume encryption (LUKS / EBS KMS) protects data at rest if physical drives or cloud snapshots are detached or exfiltrated. It **does NOT** encrypt logical database dumps produced via `pg_dump`, `pg_dumpall`, or exported document archives. Logical backup encryption requires independent cryptographic protection (e.g., GPG/age encryption pipeline), which is scheduled for subsequent hardening.

> [!WARNING]
> **Deployment Truthfulness**:
> Actual host/cloud disk encryption cannot be truthfully claimed in this repository build because disk encryption is a function of the deployment host environment and operating system block devices, rather than application container configurations.
