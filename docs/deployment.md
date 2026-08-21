# Production Deployment & Environment Setup Guide

**Institution**: Noida Institute of Engineering and Technology (NIET)  
**Feature**: Step 23 Production Deployment Specification

---

## 1. System Prerequisites

Before deploying the Faculty Achievement Portal to a production environment, ensure the following infrastructure components are installed and configured:

1. **Java Runtime**: OpenJDK 21 or Eclipse Adoptium JDK 21+ HotSpot.
2. **Database Server**: MySQL Server 8.0 or higher.
3. **Web Server / Reverse Proxy**: Nginx or Apache HTTP Server with SSL/TLS termination enabled.
4. **SSL/TLS Certificate**: Valid X.509 certificate (e.g., Let's Encrypt Certbot or institutional SSL certificate).
5. **Operating System**: Linux (Ubuntu 22.04 LTS / RHEL 9 recommended) or Windows Server 2022.

---

## 2. Dedicated Database User Setup

Never run the application using the MySQL `root` account in production. Create a dedicated least-privilege database user:

```sql
-- Create dedicated database
CREATE DATABASE IF NOT EXISTS `faculty_achievement_db` 
  CHARACTER SET utf8mb4 
  COLLATE utf8mb4_unicode_ci;

-- Create dedicated application user
CREATE USER 'niet_app_user'@'localhost' IDENTIFIED BY 'STRONG_PRODUCTION_PASSWORD_HERE';

-- Grant required DML permissions (SELECT, INSERT, UPDATE, DELETE) only on application database
GRANT SELECT, INSERT, UPDATE, DELETE ON `faculty_achievement_db`.* TO 'niet_app_user'@'localhost';

FLUSH PRIVILEGES;
```

---

## 3. Environment Variables Specification

The production application externalizes all sensitive credentials and server settings via environment variables.

| Environment Variable | Description | Production Example / Guidance | Default Fallback |
| :--- | :--- | :--- | :--- |
| `DB_HOST` | MySQL Server Hostname | `127.0.0.1` or `db.internal.niet.ac.in` | `localhost` |
| `DB_PORT` | MySQL Server Port | `3306` | `3306` |
| `DB_NAME` | MySQL Database Name | `faculty_achievement_db` | `faculty_achievement_db` |
| `DB_USERNAME` | MySQL Application User | `niet_app_user` | `root` |
| `DB_PASSWORD` | MySQL Application Password | `STRONG_PRODUCTION_PASSWORD_HERE` | _(none — required)_ |
| `DB_POOL_MAX_SIZE` | HikariCP Max Pool Size | `20` | `10` |
| `DB_POOL_MIN_IDLE` | HikariCP Min Idle Connections | `5` | `5` |
| `JWT_SECRET` | 256-bit Secret Key for signing JWTs | `a1b2c3d4e5f6...` *(Must be 256+ bits long)* | _(none — required)_ |
| `JWT_EXPIRATION_MS` | JWT expiration time in milliseconds | `86400000` *(24 Hours)* | `86400000` |
| `APP_FILE_STORAGE_UPLOAD_DIR` | Directory path for PDF proofs | `/var/niet/uploads/achievements` | `uploads/achievements` |
| `FRONTEND_ALLOWED_ORIGINS` | Whitelisted CORS origins | `https://portal.niet.ac.in` | Local dev origins |
| `PORT` | Embedded Tomcat Server Port | `8080` | `8080` |

---

## 4. Backend Build & Systemd Service Configuration

### Building Production Executable Archive
Build the production JAR archive using Maven:
```bash
mvn clean package -DskipTests=false
```
The output executable package will be located at:
`backend/target/faculty-achievement-portal-0.0.1-SNAPSHOT.jar`

### Systemd Service Setup (`/etc/systemd/system/niet-faculty.service`)
```ini
[Unit]
Description=NIET Faculty Achievement Portal Backend Service
After=syslog.target network.target mysql.service

[Service]
User=nietapp
Group=nietapp
WorkingDirectory=/opt/niet-faculty-portal
ExecStart=/usr/bin/java -Xms512m -Xmx1024m -jar /opt/niet-faculty-portal/faculty-achievement-portal-0.0.1-SNAPSHOT.jar

Environment=PORT=8080
Environment=DB_HOST=127.0.0.1
Environment=DB_PORT=3306
Environment=DB_NAME=faculty_achievement_db
Environment=DB_USERNAME=niet_app_user
Environment=DB_PASSWORD=STRONG_PRODUCTION_PASSWORD_HERE
Environment=JWT_SECRET=PROD_JWT_SECRET_256_BITS_MINIMUM_HEX_KEY
Environment=APP_FILE_STORAGE_UPLOAD_DIR=/var/niet/uploads/achievements
Environment=FRONTEND_ALLOWED_ORIGINS=https://portal.niet.ac.in

SuccessExitStatus=143
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

Enable and start the service:
```bash
sudo systemctl daemon-reload
sudo systemctl enable niet-faculty
sudo systemctl start niet-faculty
```

---

## 5. Nginx Reverse Proxy & HTTPS Setup

Configure Nginx as a reverse proxy to terminate SSL/TLS and forward API requests securely to Spring Boot:

```nginx
server {
    listen 80;
    server_name portal.niet.ac.in;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name portal.niet.ac.in;

    ssl_certificate /etc/letsencrypt/live/portal.niet.ac.in/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/portal.niet.ac.in/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;

    # Static Frontend Web Files
    root /var/www/niet-faculty-portal/frontend;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    # Reverse Proxy to Spring Boot Backend API
    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        client_max_body_size 10M;
    }
}
```

---

## 6. Database & PDF Storage Backup Strategy

### 1. Database Backup (Daily `mysqldump` Cron Job)
```bash
mysqldump -u niet_app_user -p'STRONG_PRODUCTION_PASSWORD_HERE' \
  --single-transaction --quick --lock-tables=false \
  faculty_achievement_db | gzip > /var/backups/niet_db_$(date +\%Y\%m\%d_\%H\%M\%S).sql.gz
```

### 2. File Upload Storage Backup
Perform daily `rsync` or tar backups of the uploaded PDF proof documents directory:
```bash
tar -czf /var/backups/niet_pdf_uploads_$(date +\%Y\%m\%d).tar.gz /var/niet/uploads/achievements
```

---

## 7. Health Check & Diagnostics

The production backend exposes a minimal health check endpoint:
- **URL**: `GET /api/health`
- **Expected Response**: `200 OK` `{"status":"UP"}`
