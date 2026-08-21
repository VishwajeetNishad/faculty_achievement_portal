# Production Deployment Guide (Docker + Caddy, one VM)

**Institution**: Noida Institute of Engineering and Technology (NIET)

This guide deploys the whole portal — database, backend API, and static frontend —
onto **one Linux server** using **Docker Compose**, with **automatic, browser-trusted
HTTPS** from Let's Encrypt via **Caddy**. You run *one command* and the stack comes up.

Plain-language glossary (used throughout):

- **VM (virtual machine)** — a rented Linux server in the cloud (any provider).
- **Container** — a self-contained box holding an app plus everything it needs, so it
  runs the same everywhere. **Docker Compose** describes several containers in one file
  and starts them together.
- **Reverse proxy** — a front-door web server that receives all traffic and routes it
  (static files vs. `/api`). It's also where HTTPS lives. Here that's **Caddy**.
- **Let's Encrypt** — a free certificate authority. Caddy gets and auto-renews the HTTPS
  certificate for you, so faculty see a padlock and no security warning.

---

## 1. What runs where

```
  Faculty browser
        │  https://your-domain            (encrypted padlock)
        ▼
  ┌──────────────────────── ONE LINUX VM (Docker) ────────────────────────┐
  │  caddy    ports 80/443  → auto HTTPS, serves frontend, proxies /api    │
  │     │                                                                  │
  │     ├── (static files)  ────────────────►  ./frontend  (mounted)      │
  │     └── /api/*  ──────────────►  backend :8080 (private network only)  │
  │                                      │                                 │
  │                                      ▼                                 │
  │                                 db  (MySQL 8, private network only)    │
  │                                                                        │
  │  Persistent volumes: mysql-data (DB), uploads (PDFs), logs, caddy-data │
  └────────────────────────────────────────────────────────────────────────┘
```

- The **database is never exposed to the internet** — it has no published port and is
  reachable only by the backend over Docker's private network.
- **PDF proofs** and **DB data** live on named **volumes** (disk areas Docker keeps even
  when containers are recreated), so nothing is lost on restart or redeploy.

---

## 2. Prerequisites

1. **A VM** running a recent Linux (Ubuntu 22.04/24.04 LTS is easiest). 1 vCPU / 2 GB RAM
   is enough to start; 2 vCPU / 4 GB is comfortable.
2. **A registered domain name** (e.g. `portal-niet.example`). A cheap one is fine.
3. **Docker Engine + the Compose plugin** installed on the VM. On Ubuntu:
   ```bash
   curl -fsSL https://get.docker.com | sudo sh
   ```
   Verify: `docker --version` and `docker compose version` both print a version.

---

## 3. Point your domain at the VM (DNS)

In your domain registrar's DNS settings, create an **A record** (a mapping from a name to
an IPv4 address) for your domain pointing at the VM's public IP:

| Type | Name | Value            |
| :--- | :--- | :--------------- |
| A    | `@`  | `<your VM's IP>` |

Caddy can only obtain an HTTPS certificate **after** DNS resolves to this server, so do
this first and give it a few minutes to propagate. (Check with `ping your-domain`.)

Also open ports **80** and **443** in the VM's firewall / cloud security group. Port 80 is
needed for the initial certificate challenge; 443 serves the live site. Do **not** open the
MySQL port (3306) — it stays internal.

---

## 4. Get the code and configure secrets

```bash
# 1. Copy the project onto the VM (git clone, scp, etc.), then enter it:
cd faculty-achievement-portal

# 2. Create your private environment file from the committed template:
cp .env.example .env

# 3. Generate a strong random JWT signing key and paste it into .env as JWT_SECRET:
openssl rand -base64 48
```

Edit `.env` and set **every** value (see `.env.example` for the full list with comments):

- `DOMAIN` — your real domain (no `http://`).
- `ACME_EMAIL` — an email for Let's Encrypt certificate-expiry notices.
- `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` — the app's **dedicated, non-root** MySQL user
  (the database container creates it automatically on first start).
- `MYSQL_ROOT_PASSWORD` — a *separate* password for the MySQL admin account.
- `JWT_SECRET` — the 48-byte random string you just generated (signs login tokens).
- `ADMIN_EMAIL` / `ADMIN_PASSWORD` — the first administrator account, created **once** on
  first boot. Choose a strong password. (Leave both blank to skip auto-creation.)

> **Why `.env`:** it keeps every secret out of the code and out of Git. The real `.env` is
> git-ignored; only the `.env.example` *template* (no real secrets) is committed. Keep a
> secure backup of `.env` — it holds the keys to the system.

---

## 5. Start the whole stack

```bash
docker compose up -d --build
```

- `--build` compiles the backend image (runs the unit tests as a build gate) the first time.
- `-d` runs everything in the background.

**What happens automatically on first boot:**

1. **MySQL** starts and creates the database + the dedicated app user.
2. The backend waits until MySQL reports healthy (`depends_on: service_healthy`), then
   **Flyway** (a database-migration tool — it runs versioned `.sql` files in order) creates
   all tables and seeds reference data (roles, departments, categories). No manual schema
   step is ever needed.
3. If `ADMIN_EMAIL`/`ADMIN_PASSWORD` are set and no admin exists yet, the **first admin
   account is created** (password stored only as a BCrypt hash).
4. **Caddy** obtains the Let's Encrypt certificate for your domain and starts serving HTTPS.

Watch progress with:
```bash
docker compose logs -f
```

---

## 6. Verify it works

```bash
# Backend health (includes a database-connectivity check). Expect {"status":"UP"}:
curl -fsS https://your-domain/actuator/health

# HTTP should redirect to HTTPS, and security headers should be present:
curl -I http://your-domain
```

Then in a browser:

1. Open `https://your-domain` — you should see the **padlock** (trusted certificate).
2. Log in as the bootstrapped admin.
3. Submit an achievement, upload a PDF, and download it back.
4. As an HOD, approve/reject; as admin, view audit logs; confirm the notification bell.

---

## 7. Backups (run daily)

Everything important is on Docker volumes. Back up **both** the database and the uploaded
PDFs. These commands read the credentials from your `.env` file.

```bash
# --- 1) Database dump (consistent snapshot), gzipped ---
mkdir -p /var/backups/niet
set -a; . /path/to/faculty-achievement-portal/.env; set +a
docker compose -f /path/to/faculty-achievement-portal/docker-compose.yml exec -T db \
  mysqldump -u"$DB_USERNAME" -p"$DB_PASSWORD" \
  --single-transaction --quick --lock-tables=false "$DB_NAME" \
  | gzip > /var/backups/niet/db_$(date +\%Y\%m\%d_\%H\%M\%S).sql.gz

# --- 2) Uploaded PDF proofs (copied straight out of the uploads volume) ---
docker run --rm -v faculty-achievement-portal_uploads:/data:ro -v /var/backups/niet:/out \
  busybox tar -czf /out/uploads_$(date +\%Y\%m\%d).tar.gz -C /data .
```

> The volume name is `<project-folder>_uploads`. Confirm the exact name with
> `docker volume ls` if your project folder differs.

Schedule both as a daily **cron job** (a Linux scheduled task). Edit the crontab with
`crontab -e` and add, for example, a 2:30 AM run:

```cron
30 2 * * *  /usr/local/bin/niet-backup.sh >> /var/log/niet-backup.log 2>&1
```

(Put the two commands above into `/usr/local/bin/niet-backup.sh` and `chmod +x` it.)
Periodically copy `/var/backups/niet` off the server (to object storage or another host)
so a single-machine failure can't destroy the backups too.

---

## 8. Day-to-day operations

```bash
docker compose ps                 # what's running and health status
docker compose logs -f backend    # follow the backend logs (also written to the logs volume)
docker compose restart backend    # restart just the backend
docker compose down               # stop everything (volumes/data are kept)
```

**Redeploy after a code change:**
```bash
git pull                          # get the new code
docker compose up -d --build      # rebuild changed images and restart; Flyway applies
                                  # any new migrations automatically on startup
```

Application logs are also written to the persistent `logs` volume (rolling files, daily +
at 10 MB, ~14 days kept, gzip-compressed, total capped at 200 MB) so you can inspect them
even after a container restart.

---

## 9. Health check endpoint

- **URL**: `GET /actuator/health`
- **Expected response**: `200 OK` with `{"status":"UP"}`

This endpoint is provided by Spring Boot Actuator and includes a **database-connectivity**
check, so it reports `DOWN` if MySQL is unreachable. Docker uses it as the backend
container's health check, which also gates startup order (Caddy/monitoring can rely on it).
