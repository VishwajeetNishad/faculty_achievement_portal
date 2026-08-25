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
- **Secret Management**: The signing key is supplied only via the required `${JWT_SECRET}` environment variable (minimum 256 bits). The application validates it at startup and fails fast if it is missing or too weak — no signing secret is hardcoded or committed to source control.
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
| **Audit Logs** | ✗ | ✗ | All | `ROLE_ADMIN` **or** the `VIEW_AUDIT_LOGS` permission on `/api/audit-logs` |
| **Notifications** | Own | Own | Own | Recipient ID check from JWT |

> The three roles are the baseline. On top of them, an Admin can grant a user individual extra permissions (Track A) — see §4. Grants only ever **add** to what a role can do; a `ROLE_*` authority is never taken away.

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

---

## 4. Fine-Grained Permissions (Track A)

The three roles decide the *baseline* of what a user can do. Track A lets an Admin hand a **single user** a few extra abilities — for example, letting one Head of Department also create faculty accounts — **without** changing that person's role. This is added on top of the existing security; nothing about JWT, roles, or the IDOR checks changes.

### The 15 permission codes

| Code | Plain-language meaning |
| :--- | :--- |
| `CREATE_FACULTY` | Create new faculty accounts |
| `EDIT_FACULTY` | Edit faculty accounts |
| `CREATE_HOD` | Create Head-of-Department accounts |
| `EDIT_HOD` | Edit HOD accounts |
| `CREATE_ADMIN` | Create administrator accounts (**highly restricted**) |
| `MANAGE_USER_STATUS` | Activate / deactivate / suspend accounts |
| `VIEW_ALL_ACHIEVEMENTS` | See achievements across the whole institution |
| `VERIFY_ACHIEVEMENT` | Approve or reject submitted achievements |
| `EDIT_ACHIEVEMENT` | (seeded, **not wired** — achievements stay owner-only) |
| `DELETE_ACHIEVEMENT` | (seeded, **not wired** — achievements stay owner-only) |
| `VIEW_REPORTS` | View institutional reports |
| `EXPORT_REPORTS` | Export reports to CSV |
| `MANAGE_DEPARTMENTS` | Add / edit / remove departments |
| `VIEW_AUDIT_LOGS` | Read the audit trail |
| `MANAGE_PERMISSIONS` | Grant / revoke permissions for other users (**highly restricted**) |

### How permissions are enforced

- **Admins hold all 15 implicitly.** A `ROLE_ADMIN` user is *computed* to have every permission — there are no rows for them in `user_permissions`. This is a deliberate safety choice: an admin can never be locked out by a half-filled grant table, and there is no bootstrap deadlock.
- **Loaded fresh on every request.** The JWT is unchanged and carries no permission list. `CustomUserDetailsService` re-reads the user (and their granted permissions) from the database on each request, so a grant or a revoke takes effect on the user's **very next call** — no re-login, no waiting for a token to expire.
- **Additive, never a replacement.** Where an endpoint used to require `hasRole('ADMIN')`, it now reads `hasRole('ADMIN') or hasAuthority('SOME_PERMISSION')`. The role still works exactly as before; the permission is only an *extra* door in.
- **A deactivated account is locked out immediately.** `enabled` is now tied to `status == ACTIVE`, so setting a user to `INACTIVE`/`SUSPENDED` makes their next request fail authentication (401). (This closed a real gap where a deactivated user could still log in.)
- **UI hints are not security.** The frontend `can(code)` helper only shows or hides buttons. Every actual check happens on the server.

### The five escalation guards (in `PermissionServiceImpl`)

The person doing the granting is **always** taken from the JWT (`SecurityContextHolder`), never from the request body. A permission change is refused when:

| # | Rule | Result |
| :--- | :--- | :--- |
| 1 | You try to change **your own** permissions | `403 Forbidden` |
| 2 | The target user is an **administrator** (they already have everything) | `400 Bad Request` |
| 3 | An **unknown** permission code is sent (never silently ignored) | `400 Bad Request` |
| 4 | A **non-admin** tries to grant `MANAGE_PERMISSIONS` or `CREATE_ADMIN` | `403 Forbidden` |
| 5 | You try to grant a permission you **do not hold yourself** (no amplification) | `403 Forbidden` |

### The last-administrator guard (in `UserManagementServiceImpl`)

Any action that would leave the system with **zero active administrators** — deactivating the last admin, or demoting them — is refused with `409 Conflict`. The institution can never accidentally lock every admin out.

Account and permission changes are written to the append-only audit log (`USER_CREATED`, `USER_UPDATED`, `USER_STATUS_CHANGED`, `ROLE_CHANGED`, `PERMISSIONS_UPDATED`). Only codes and emails are recorded — never a password, hash, JWT, or secret.

---

## 5. Public Access & Share Links (Track B)

Track B opens two doors to people with **no login**: a public site that shows approved, publicly-marked research, and temporary "share links" that let a faculty member show *unpublished* work to a specific person. The security model treats an anonymous visitor as completely untrusted.

### The visibility rule

Each achievement has a `visibility` of `PUBLIC`, `UNLISTED`, or `PRIVATE` (all 19 existing rows defaulted to **`PRIVATE`** — nothing became public by surprise). Visibility is **never** tied to the approval status; both must line up:

> **An achievement is visible to the public only when `status = APPROVED` AND `visibility = PUBLIC`.**

This rule is written as a literal inside every public query at the **service** layer. It is never a filter the client sends, so a visitor cannot craft a request that widens what they see. `PENDING`, `REJECTED`, `PRIVATE`, and `UNLISTED` items are absent from every public response body — not just hidden on the page.

### Dedicated public DTOs — leakage is impossible by shape

The public site never reuses the internal `AchievementResponse` (which carries `verificationComment`, `facultyEmail`, `proofDocumentUrl`). Instead there are separate response objects — `PublicFacultyResponse`, `PublicFacultyProfileResponse`, `PublicAchievementResponse`, `SharedAchievementResponse` — that simply **do not contain** the sensitive fields. A field that isn't on the class can never appear in the JSON, no matter how the service changes later. Omitted: `verificationComment`, `facultyEmail`, `employeeId`, `phone`, `proofDocumentUrl`, `verifiedBy*`, `userId`, `status`, and `visibility`. A structural test (`PublicAccessSecurityTest`) fails the build if anyone ever adds one back.

### Share-link tokens are bearer credentials

- **Unguessable by construction.** A token is **32 bytes from `SecureRandom`**, URL-safe Base64 with no padding (43 characters). It is **never** derived from a database id, employee id, or timestamp — a test generates 1,000 tokens from identical input and asserts all 1,000 are distinct and full-entropy.
- **The server is the only authority on validity.** Expiry and revocation are re-checked **on every request**: unknown token → `404`, revoked → `410 REVOKED`, expired → `410 EXPIRED`. The countdown shown in the browser is decoration only.
- **Owner-only management.** Creating, viewing, extending, and revoking a link all take the owner from the JWT; a non-owner gets `403`. At most one active link exists per achievement.
- **The proof PDF is opt-in.** The document behind a share link is reachable **only** if the owner ticked "include proof document"; otherwise `/api/public/share/{token}/document` returns `403`. It streams through the same validated `FileStorageService` used everywhere else — the existing user-authorised download path is untouched. Copyrighted papers are linked out (e.g. via DOI), never hosted or proxied.
- **The token never leaks.** It is not echoed in a not-found error message, and it is never written into an audit description (`SHARE_CREATED`, `SHARE_UPDATED`, `SHARE_REVOKED`, `SHARE_EXPIRED` record the event, not the secret). Every share response is sent `Cache-Control: no-store`.

### Accepted trade-offs (documented, not hidden)

- The `share_token` is stored in plaintext (not hashed) because the "Copy Link" feature must re-display it later. Anyone with **database read access** could therefore use a live link. Accepted because the required UX needs the raw token again.
- A "Permanent" link is a standing bearer credential for unpublished work. It is offered because the spec asks for it; the UI warns about it, and one click revokes it.
