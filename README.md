# Faculty Achievement Portal — NIET

**Institution**: Noida Institute of Engineering and Technology (NIET)  
**Project Status**: Production-Ready / Final College Submission Package  
**Architecture**: Spring Boot 3.3.4 (Java 21) + MySQL 8.0 + Vanilla HTML5/CSS3/JavaScript  
**Test Coverage**: **169 / 169 System Test Scenarios Passed (100%)**

---

## 1. Project Overview

The **Faculty Achievement Portal** is a centralized, role-based web application developed for **Noida Institute of Engineering and Technology (NIET)**. It streamlines the digital submission, departmental verification, institutional analytics, secure document management, and compliance reporting of faculty achievements (Journal Publications, Patents, Research Grants, Workshops/FDPs, and Awards).

The system replaces manual paper workflows with automated submission tracking, HOD/Admin verification workflows, real-time event notifications, append-only security audit logging, and role-restricted CSV report generation.

---

## 2. Key Modules & Features

- **Authentication & Security**: JWT authentication (HS256, 24h expiry), BCrypt password hashing, role-based access control (FACULTY, HOD, ADMIN), and IDOR/BOLA protection.
- **Faculty Self-Service Portal**: Submit achievements across 5 categories, upload PDF proof certificates with deep magic-byte validation (`%PDF-`), track review status, and receive real-time notifications.
- **HOD Verification Workflow**: Department-scoped dashboard, review pending achievements, inspect PDF proof certificates, approve or reject with comments, and track department analytics.
- **Admin Control Center**: Institution-wide statistics, active faculty roster management, cross-department verification, audit trail inspection, and CSV report export.
- **Search, Filtering & Pagination**: Multi-criterion backend filtering (keyword, status, category, date range, department), whitelisted sorting, and server-side pagination (max 100 per page).
- **Secure File Storage**: PDF file validation, 10 MB size limit, random UUID filename generation (path traversal protection), and protected streaming downloads.
- **Real-Time Notification System**: Event-triggered in-app alerts for submissions, review requests, approvals, and rejections with unread badge counters.
- **Immutable Security Audit Trail**: Append-only audit logging of authentication attempts (`LOGIN_SUCCESS`, `LOGIN_FAILURE`), achievement CRUD, proof uploads, and profile updates.

---

## 3. Technology Stack

- **Backend Framework**: Java 21 LTS, Spring Boot 3.3.4, Spring Security, Spring Data JPA, Hibernate.
- **Database Engine**: MySQL Server 8.0+ (`faculty_achievement_db`).
- **Security & Tokens**: JJWT 0.12.6, BCrypt Password Hashing, Jakarta Validation.
- **Frontend Architecture**: HTML5, Vanilla CSS3 (Custom Design Tokens), JavaScript ES6+ (Fetch API / Async-Await).
- **Build Tool**: Apache Maven (via the bundled Maven Wrapper — no separate install needed).

---

## 4. System Architecture

```
                         USERS (Faculty / HOD / Admin)
                                       |
                                       v
               +-----------------------------------------------+
               |   HTML5 / CSS3 / Vanilla JavaScript Frontend  |
               +-----------------------+-----------------------+
                                       |
                                       | HTTPS / REST API (JWT Bearer)
                                       v
               +-----------------------------------------------+
               |       Spring Boot 3.3.4 REST Application      |
               +-----------------------+-----------------------+
                                       |
          +----------------------------+----------------------------+
          |                            |                            |
          v                            v                            v
  Spring Security              Service Layer               File Storage Service
(JWT / BCrypt RBAC)                    |                    (uploads/achievements)
          |                            v                            |
          |                   JPA Repositories                      |
          |                            |                            |
          +----------------------------+----------------------------+
                                       |
                                       v
                             MySQL 8.0 Database
               (users, achievements, notifications, audit_logs)
```

---

## 5. User Roles & Authorization Matrix

| Action / Capability | Faculty | HOD | Admin | Enforcement Mechanism |
| :--- | :---: | :---: | :---: | :--- |
| **Submit & Manage Own Achievements** | ✓ | ✓ | ✓ | SecurityContext user identity check |
| **Upload / Delete Proof PDF** | Owner | Owner | Owner | Owner authorization check |
| **Download Proof Certificate** | Owner | Dept | All | IDOR check: Owner, Admin, or Dept HOD |
| **Department Achievements Review** | ✗ | Dept | All | `ROLE_HOD` / `ROLE_ADMIN` authorization |
| **Institutional Faculty Roster** | ✗ | ✗ | All | `hasRole('ADMIN')` on `/api/users` |
| **Institutional Audit Trail** | ✗ | ✗ | All | `hasAnyAuthority('ROLE_ADMIN')` on `/api/audit-logs` |

---

## 6. Project Structure

```
faculty-achievement-portal/
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/niet/facultyachievement/
│       │   │   ├── config/          # Spring Security & App Beans
│       │   │   ├── controller/      # Auth, Achievement, User, Dashboard, Notification, Audit Controllers
│       │   │   ├── dto/             # Request & Response Data Transfer Objects
│       │   │   ├── entity/          # JPA Entities (User, Achievement, Notification, AuditLog, etc.)
│       │   │   ├── exception/       # Global Exception Handler & Custom Errors
│       │   │   ├── repository/     # Spring Data JPA Repositories & Specifications
│       │   │   ├── security/       # JWT Provider & Custom UserDetailsService
│       │   │   └── service/        # Service Interfaces & Implementations
│       │   └── resources/
│       │       ├── application.properties
│       │       ├── logback-spring.xml     # Logging config (console + rolling file)
│       │       └── db/migration/          # Flyway schema & seed migrations (V1, V2, ...)
│       └── test/                    # Maven Unit & Integration Test Suites
├── frontend/
│   ├── css/                         # Custom CSS Design System (variables, reset, layout, components, forms, tables, responsive)
│   ├── js/                          # Frontend Controllers (api, common, achievements, admin, dashboard)
│   └── pages/                       # User Interfaces (index, login, achievements, profile, admin/*)
└── docs/                            # Academic & Technical Documentation Package
```

---

## 7. How to Run Locally

### Prerequisites
- JDK 21+ installed and `JAVA_HOME` configured.
- MySQL 8.0+ running on port `3306`.
- Create an empty database: `CREATE DATABASE faculty_achievement_db;`. You do **not** run
  any schema script — **Flyway** creates all tables and seeds reference data (roles,
  departments, categories) automatically on first startup.
- Provide the required settings (`DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, ...) via
  environment variables, or a git-ignored `backend/src/main/resources/application-local.properties`.

### Backend Startup
Use the bundled Maven wrapper (no separate Maven install required):
```bash
cd backend
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```
On first run Flyway creates and seeds the schema, then the REST API starts at `http://localhost:8080/api`.

### Frontend Launch
Open `frontend/pages/index.html` or serve `frontend/` using any static web server (e.g. VS Code Live Server / Python `http.server`).

> **Deploying live?** See [`docs/deployment.md`](docs/deployment.md) for the one-command Docker + Caddy production setup with automatic, browser-trusted HTTPS.

---

## 8. Environment Variables

| Variable | Description | Default Fallback |
| :--- | :--- | :--- |
| `DB_HOST` | MySQL Host | `localhost` |
| `DB_PORT` | MySQL Port | `3306` |
| `DB_NAME` | Database Name | `faculty_achievement_db` |
| `DB_USERNAME` | DB Username | `root` |
| `DB_PASSWORD` | DB Password | _(required — no default)_ |
| `JWT_SECRET` | 256-bit Signing Secret | _(required — no default)_ |
| `APP_FILE_STORAGE_UPLOAD_DIR` | PDF Upload Path | `uploads/achievements` |
| `FRONTEND_ALLOWED_ORIGINS` | CORS Whitelist | `http://localhost:8080,http://localhost:3000` |

---

## 9. Verification & Test Suite Summary

- **Backend Unit Tests**: 28 / 28 PASSED
- **Security Hardening Suite**: 19 / 19 PASSED
- **Master E2E Integration Suite**: 21 / 21 PASSED
- **Step 17–20 Integration Suites**: 101 / 101 PASSED
- **TOTAL**: **169 / 169 PASSED (0 Failures)**

---

## 10. Documentation Index

Detailed documentation files are available in the [`docs/`](file:///c:/Users/vishw/Desktop/Faculty/faculty-achievement-portal/docs/) directory:

1. [`docs/abstract.md`](file:///c:/Users/vishw/Desktop/Faculty/faculty-achievement-portal/docs/abstract.md): Academic Project Abstract
2. [`docs/problem-statement.md`](file:///c:/Users/vishw/Desktop/Faculty/faculty-achievement-portal/docs/problem-statement.md): Problem Statement & Objectives
3. [`docs/SRS.md`](file:///c:/Users/vishw/Desktop/Faculty/faculty-achievement-portal/docs/SRS.md): Software Requirements Specification
4. [`docs/architecture.md`](file:///c:/Users/vishw/Desktop/Faculty/faculty-achievement-portal/docs/architecture.md): System Architecture & Diagrams
5. [`docs/database.md`](file:///c:/Users/vishw/Desktop/Faculty/faculty-achievement-portal/docs/database.md): Relational Database Schema Documentation
6. [`docs/er-diagram.md`](file:///c:/Users/vishw/Desktop/Faculty/faculty-achievement-portal/docs/er-diagram.md): Entity-Relationship Specification
7. [`docs/security.md`](file:///c:/Users/vishw/Desktop/Faculty/faculty-achievement-portal/docs/security.md): Application Security & Threat Defense Guide
8. [`docs/api.md`](file:///c:/Users/vishw/Desktop/Faculty/faculty-achievement-portal/docs/api.md): Complete REST API Specification
9. [`docs/testing.md`](file:///c:/Users/vishw/Desktop/Faculty/faculty-achievement-portal/docs/testing.md): System Integration & E2E Testing Report
10. [`docs/test-cases.md`](file:///c:/Users/vishw/Desktop/Faculty/faculty-achievement-portal/docs/test-cases.md): Comprehensive Test Case Matrix
11. [`docs/deployment.md`](file:///c:/Users/vishw/Desktop/Faculty/faculty-achievement-portal/docs/deployment.md): Production Deployment & Environment Setup Guide
12. [`docs/user-manual.md`](file:///c:/Users/vishw/Desktop/Faculty/faculty-achievement-portal/docs/user-manual.md): User Manual & Operating Guide
13. [`docs/presentation-outline.md`](file:///c:/Users/vishw/Desktop/Faculty/faculty-achievement-portal/docs/presentation-outline.md): 14-Slide Final Project PPT Outline
14. [`docs/viva-questions.md`](file:///c:/Users/vishw/Desktop/Faculty/faculty-achievement-portal/docs/viva-questions.md): 40 Technical Viva Preparation Questions & Answers
