# FinSight — VPS Migration & Production Deployment Guide

**Version:** 1.0  
**Target OS:** Ubuntu 22.04 / 24.04 LTS or Debian 12 LTS  
**Baseline:** PR #4 Integration (`40fcd5abe1c7c50187d23fa362f078387748fc97`, Migrations V1–V52)  
**Author:** Antigravity — Financial Systems Release Coordinator  

---

## 1. Overview & Architecture

This guide explains how to transition the local FinSight demonstration deployment onto a remote Linux Virtual Private Server (VPS) with a custom domain, automated SSL/TLS certificates, hardened database role separation, and persistent storage.

```
                      +---------------------------------------+
                      |          Client Web Browsers          |
                      |   (Desktop, Mobile, Tablet, RTL/UR)   |
                      +-------------------+-------------------+
                                          | HTTPS (443)
                                          v
                      +-------------------+-------------------+
                      |      Nginx Reverse Proxy / SSL       |
                      |      (Let's Encrypt / Cloudflare)     |
                      +---------+-------------------+---------+
                                |                   |
                 Static/SSR     |                   |  /api/*
                 Traffic (:3000)|                   |  Traffic (:8080)
                                v                   v
               +--------------------+   +-----------------------+
               | Next.js Standalone |   | Spring Boot Backend   |
               | Frontend Service   |   | (Java 21 LTS)         |
               +--------------------+   +-----------+-----------+
                                                    |
                                                    | JDBC (5432)
                                                    v
                                        +-----------+-----------+
                                        | PostgreSQL 17 LTS     |
                                        | - finsight_migrator   |
                                        | - finsight_app (DML)  |
                                        | - finsight_backup     |
                                        +-----------------------+
```

---

## 2. Recommended VPS Hardware Specifications

| Workload Tier | vCPU | RAM | NVMe SSD | Cost Estimate | Recommended Providers |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Minimum Demonstration** | 2 vCPU | 4 GB | 40 GB | ~$5 - $10/mo | Hetzner Cloud, DigitalOcean, Linode |
| **Production SME Fleet** | 4 vCPU | 8 GB | 80 GB | ~$20 - $35/mo | Hetzner Cloud (CPX31), Vultr, AWS Lightsail |

---

## 3. Initial Server Provisioning

### 3.1 Base OS Hardening
Log into your fresh VPS via SSH as `root`:

```bash
# Update package indices
sudo apt-get update && sudo apt-get upgrade -y

# Install essential tools
sudo apt-get install -y curl wget git ufw fail2ban jq unzip htop

# Configure UFW Firewall
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp     # SSH
sudo ufw allow 80/tcp     # HTTP (ACME challenge)
sudo ufw allow 443/tcp    # HTTPS
sudo ufw enable
```

### 3.2 Install Docker Engine & Compose Plugin
```bash
# Install Docker repository and GPG key
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# Enable and start Docker service
sudo systemctl enable --now docker
```

---

## 4. Directory Structure & Secret Configuration

Create a dedicated application user and directory:

```bash
sudo useradd -m -s /bin/bash finsight
sudo usermod -aG docker finsight
sudo su - finsight

mkdir -p ~/finsight/{backend,frontend,database,backups,config,nginx}
cd ~/finsight
```

### 4.1 Master Secrets Configuration (`~/finsight/config/.env.production`)
Create a production environment file with high-entropy cryptographic keys:

```bash
cat << 'EOF' > ~/finsight/config/.env.production
# FinSight Production Environment Secrets
# DO NOT COMMIT THIS FILE TO VERSION CONTROL

# Server Domain
DOMAIN_NAME=app.yourfinsightdomain.com

# PostgreSQL Configuration
POSTGRES_HOST=postgres
POSTGRES_PORT=5432
POSTGRES_DB=finsight_production
POSTGRES_SUPERUSER_PASSWORD=GENERATED_STRONG_SUPERUSER_PASSWORD_HERE
POSTGRES_MIGRATOR_PASSWORD=GENERATED_STRONG_MIGRATOR_PASSWORD_HERE
POSTGRES_APP_PASSWORD=GENERATED_STRONG_APP_PASSWORD_HERE
POSTGRES_BACKUP_PASSWORD=GENERATED_STRONG_BACKUP_PASSWORD_HERE

# Cryptographic Keys (AES-256-GCM - exactly 32 raw bytes base64 encoded)
# Generate via: openssl rand -base64 32
FINSIGHT_CRYPTO_KEY_K1=YOUR_BASE64_AES256_KEY_HERE

# Session & Security
INTERNAL_SERVICE_SECRET=GENERATED_STRONG_INTERNAL_SECRET_HERE
SESSION_COOKIE_SECURE=true

# Operational
NEXT_PUBLIC_DATA_MODE=live
SERVER_PORT=8080
EOF
chmod 600 ~/finsight/config/.env.production
```

---

## 5. PostgreSQL Container & Role Setup

### 5.1 Docker Compose Configuration (`~/finsight/docker-compose.yml`)
```yaml
version: '3.8'

services:
  postgres:
    image: postgres:17-alpine
    container_name: finsight-postgres
    restart: always
    environment:
      POSTGRES_DB: finsight_production
      POSTGRES_PASSWORD: ${POSTGRES_SUPERUSER_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ~/finsight/backups:/backups
    networks:
      - finsight-network
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 5s
      timeout: 5s
      retries: 5

  backend:
    image: eclipse-temurin:21-jre-alpine
    container_name: finsight-backend
    restart: always
    working_dir: /app
    volumes:
      - ./backend/sme-health-backend-0.0.1-SNAPSHOT.jar:/app/backend.jar
      - ./storage:/app/storage
    environment:
      POSTGRES_HOST: postgres
      POSTGRES_PORT: 5432
      POSTGRES_DB: finsight_production
      POSTGRES_APP_USER: finsight_app
      POSTGRES_APP_PASSWORD: ${POSTGRES_APP_PASSWORD}
      FINSIGHT_CRYPTO_KEY_K1: ${FINSIGHT_CRYPTO_KEY_K1}
      APP_SECURITY_INTERNAL_SERVICE_SECRET: ${INTERNAL_SERVICE_SECRET}
      SERVER_PORT: 8080
      SESSION_COOKIE_SECURE: "true"
    depends_on:
      postgres:
        condition: service_healthy
    networks:
      - finsight-network

  frontend:
    image: node:20-alpine
    container_name: finsight-frontend
    restart: always
    working_dir: /app
    volumes:
      - ./frontend:/app
    environment:
      PORT: 3000
      HOSTNAME: 0.0.0.0
      BACKEND_API_URL: http://backend:8080
      NEXT_PUBLIC_DATA_MODE: live
    command: ["node", ".next/standalone/server.js"]
    depends_on:
      - backend
    networks:
      - finsight-network

  nginx:
    image: nginx:alpine
    container_name: finsight-nginx
    restart: always
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - /etc/letsencrypt:/etc/letsencrypt:ro
    depends_on:
      - frontend
      - backend
    networks:
      - finsight-network

volumes:
  postgres_data:

networks:
  finsight-network:
    driver: bridge
```

---

## 6. Database Migration from Demonstration Dump

### 6.1 Transferring Dump from Local PC to VPS
From your Windows PC:
```powershell
scp C:\Users\PC\Projects\sme-financial-webapp-pr4-review\backups\finsight_demo_pristine_baseline.dump finsight@<VPS_IP>:~/finsight/backups/
```

### 6.2 Restoring Baseline on VPS
On the VPS:
```bash
# Start PostgreSQL container
docker compose --env-file config/.env.production up -d postgres

# Provision authoritative database roles
docker exec -i finsight-postgres psql -U postgres -d finsight_production << 'EOF'
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'finsight_migrator') THEN
        CREATE ROLE finsight_migrator WITH LOGIN PASSWORD 'YOUR_MIGRATOR_PW';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'finsight_app') THEN
        CREATE ROLE finsight_app WITH LOGIN PASSWORD 'YOUR_APP_PW';
    END IF;
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'finsight_backup') THEN
        CREATE ROLE finsight_backup WITH LOGIN PASSWORD 'YOUR_BACKUP_PW';
    END IF;
END $$;
EOF

# Restore clean dump
docker exec -i finsight-postgres pg_restore -U postgres -d finsight_production \
  --clean --if-exists --no-owner --no-privileges /backups/finsight_demo_pristine_baseline.dump

# Ensure finsight_app has full runtime DML permissions
docker exec -i finsight-postgres psql -U postgres -d finsight_production << 'EOF'
GRANT CONNECT ON DATABASE finsight_production TO finsight_app;
GRANT USAGE ON SCHEMA public TO finsight_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO finsight_app;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO finsight_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO finsight_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO finsight_app;
EOF
```

---

## 7. Nginx Reverse Proxy & SSL Configuration

### 7.1 Obtain Let's Encrypt SSL Certificate
```bash
sudo apt-get install -y certbot
sudo certbot certonly --standalone -d app.yourfinsightdomain.com
```

### 7.2 Nginx Production Configuration (`~/finsight/nginx/nginx.conf`)
```nginx
events {
    worker_connections 1024;
}

http {
    include /etc/nginx/mime.types;
    default_type application/octet-stream;
    sendfile on;
    keepalive_timeout 65;

    # Gzip compression
    gzip on;
    gzip_types text/plain text/css application/json application/javascript text/xml application/xml text/javascript;

    # HTTP redirect to HTTPS
    server {
        listen 80;
        server_name app.yourfinsightdomain.com;
        return 301 https://$host$request_uri;
    }

    # HTTPS Server
    server {
        listen 443 ssl http2;
        server_name app.yourfinsightdomain.com;

        ssl_certificate /etc/letsencrypt/live/app.yourfinsightdomain.com/fullchain.pem;
        ssl_certificate_key /etc/letsencrypt/live/app.yourfinsightdomain.com/privkey.pem;
        ssl_protocols TLSv1.2 TLSv1.3;
        ssl_ciphers HIGH:!aNULL:!MD5;
        ssl_prefer_server_ciphers on;

        # Security Headers
        add_header X-Content-Type-Options nosniff always;
        add_header X-Frame-Options SAMEORIGIN always;
        add_header X-XSS-Protection "1; mode=block" always;
        add_header Referrer-Policy strict-origin-when-cross-origin always;

        # Next.js Frontend (Root, Static Assets, SSR)
        location / {
            proxy_pass http://frontend:3000;
            proxy_http_version 1.1;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection 'upgrade';
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto https;
            proxy_cache_bypass $http_upgrade;
        }

        # Spring Boot Backend API (Direct or Rewritten)
        location /api/ {
            proxy_pass http://backend:8080/api/;
            proxy_http_version 1.1;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto https;
            proxy_cookie_path / "/; Secure; SameSite=Lax";
        }
    }
}
```

---

## 8. Automated Daily Backups & Disaster Recovery

Create a daily cron job to run backup under `finsight_backup`:

```bash
cat << 'EOF' > ~/finsight/scripts/daily-backup.sh
#!/bin/bash
set -e
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/home/finsight/finsight/backups"
BACKUP_FILE="${BACKUP_DIR}/finsight_prod_${TIMESTAMP}.dump"

docker exec finsight-postgres pg_dump -U postgres -d finsight_production -Fc -f "/backups/finsight_prod_${TIMESTAMP}.dump"

# Retention: keep last 14 days
find ${BACKUP_DIR} -name "finsight_prod_*.dump" -mtime +14 -delete
echo "[$(date)] Backup completed: ${BACKUP_FILE}" >> ${BACKUP_DIR}/backup.log
EOF
chmod +x ~/finsight/scripts/daily-backup.sh

# Add to crontab: daily at 02:00 AM
(crontab -l 2>/dev/null; echo "0 2 * * * /home/finsight/finsight/scripts/daily-backup.sh") | crontab -
```

---

## 9. Verification Checklist

1. **Database Schema:** Confirm Flyway history shows all 52 migrations applied (`SELECT count(*) FROM flyway_schema_history` = 52).
2. **Health Endpoints:**
   - Frontend: `curl -I https://app.yourfinsightdomain.com` (HTTP 200 OK)
   - Backend Actuator: `curl -s https://app.yourfinsightdomain.com/actuator/health` (`{"status":"UP"}`)
3. **Authentication:** Sign in as `demo.owner@finsight.local`, verify `FINSIGHT_SESSION` cookie is issued with `Secure; HttpOnly; SameSite=Lax`.
4. **Calculations:** Open Business A (Alpha Garments) and verify WAC valuation displays PKR 66,000.00.

