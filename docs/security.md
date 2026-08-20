# Security Architecture & Hardening Specification

**Institution**: Noida Institute of Engineering and Technology (NIET)  
**Feature**: Step 21 Complete Application Security Hardening

---

## 1. Executive Summary

This document provides a comprehensive security review and architectural specification of the Faculty Achievement Portal. The application is built using Spring Boot 3.3.4, Spring Security, standard JPA, MySQL 8.0+, and Vanilla JavaScript.

All 19 security hardening test scenarios covering Authentication, Authorization, IDOR/BOLA Defense, Mass Assignment Prevention, Input Validation, SQL Injection Defense, Sort Injection Defense, Pagination & Search Abuse Mitigation, PDF File Upload/Download Security, CORS Restrictions, CSRF Architecture, Error Leakage Prevention, and Audit Access Control have passed successfully.

---

## 2. Security Inventory & Controls

### Authentication & Token Management
- **Token Mechanism**: Stateless JSON Web Tokens (JWT) passed in the `Authorization: Bearer <token>` header.
- **Signing Algorithm**: HS256 (HMAC-SHA256).
- **Secret Management**: Configured via the required `${JWT_SECRET}` environment variable (a 256-bit key). The production signing secret must never be committed to source control.
- **Token Expiration**: Enforced at 24 hours (`86,400,000` ms) via claim expiration checking.
- **Password Hashing**: BCrypt (`BCryptPasswordEncoder` with default strength 10). Plaintext passwords, hashes, and JWT tokens are NEVER logged or exposed in audit trails.

### Authorization Matrix

| Resource / Action | Faculty | HOD | Admin | Enforcement Mechanism |
| :--- | :---: | :---: | :---: | :--- |
| **Own Profile Update** | ✓ | ✓ | ✓ | SecurityContext user identity (`PUT /api/users/me`) |
| **Own Achievements** | ✓ | ✓ | ✓ | User ID match from JWT (`/api/achievements`) |
| **Department Achievements** | ✗ | Department | All | Role & Department ID validation |
| **Achievement Verification** | ✗ | Department | All | `ROLE_HOD` / `ROLE_ADMIN` authorization check |
| **Proof Document Upload** | Owner | Policy | All | Owner validation (`POST /api/achievements/{id}/proof`) |
| **Proof Document Download** | Owner | Department | All | IDOR check: Owner, Admin, or Dept HOD |
| **Dashboard Analytics** | Own | Department | All | Endpoint routing (`/api/dashboard/{faculty/hod/admin}`) |
| **Faculty Roster** | ✗ | Department | All | `hasRole('ADMIN')` on `/api/users` |
| **Audit Logs** | ✗ | ✗ | All | `hasAnyAuthority('ROLE_ADMIN')` on `/api/audit-logs` |
| **Notifications** | Own | Own | Own | Recipient ID check from JWT |

---

## 3. Threat Defense & Hardening Details

### IDOR / BOLA Prevention
- Every endpoint accepting an `{id}` path parameter verifies requesting user identity from `SecurityContextHolder`.
- Direct ID references (e.g., `/achievements/{id}/verification`, `/notifications/{id}/read`, `/achievements/{id}/proof`) perform authorization checks against DB records before performing operations.

### Mass Assignment & Actor Spoofing Defense
- Dedicated request DTOs (`UserProfileUpdateRequest`, `AchievementCreateRequest`, `AchievementUpdateRequest`) restrict user inputs to editable fields only.
- Injected fields (`role`, `status`, `departmentId`, `password_hash`, `recipientId`, `actorUserId`) are ignored.

### SQL & Sort Injection Defense
- Database queries use Spring Data JPA parameter bindings and `JpaSpecificationExecutor`.
- Sort parameters (`sortBy`) are strictly validated against whitelisted entity property names (`createdAt`, `id`, `title`, `achievementDate`, `status`, `academicYear`). Invalid or suspicious sort fields trigger an immediate `400 Bad Request`.

### Pagination & Search Abuse Mitigation
- Page size (`size`) is capped at a maximum of **100** items per page. Negative page numbers or zero page sizes return `400 Bad Request`.
- Search keywords are capped at a maximum of **255** characters to prevent regular expression or memory denial of service.

### Secure File Upload & Download Policy
- Proof documents are limited to **PDF files** (`.pdf`) under **10 MB**.
- **Deep Magic Byte Verification**: Uploaded file headers are verified against PDF magic header bytes (`%PDF` / `0x25, 0x50, 0x44, 0x46`).
- Safe unique UUID filenames (`UUID.randomUUID().toString() + ".pdf"`) eliminate path traversal attacks (`../../evil.pdf`).
- Proof downloads require authentication and authorization. Direct static directory listing of `uploads/achievements` is disabled.

### CORS & Security Headers
- CORS is restricted to whitelisted origins (`http://localhost:8080`, `http://localhost:3000`, etc.). Wildcard `*` is disabled for authenticated endpoints.
- Response headers include `X-Frame-Options: DENY` and `X-Content-Type-Options: nosniff`.
- Stateless REST API architecture uses JWT tokens; cookie-based CSRF attacks are not applicable.

### Error Leakage & Sensitive Data Protection
- Generic unhandled exceptions return a sanitized error payload without revealing raw SQL, stack traces, or internal server configurations.
- Actuator endpoints are disabled by default (`management.endpoints.enabled-by-default=false`), exposing only a minimal health check endpoint.
