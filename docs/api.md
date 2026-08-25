# Faculty Achievement Portal — REST API Specification

## Base URL
`http://localhost:8080/api`

---

## Auth & User Endpoints Summary

| Method | Endpoint | Access | Description | Expected Status |
| :--- | :--- | :--- | :--- | :--- |
| **POST** | `/api/auth/login` | Public | Authenticate user & get JWT token | `200 OK` / `401 Unauthorized` |
| **GET** | `/api/auth/me` | Authenticated | Get current user's profile | `200 OK` / `401 Unauthorized` |
| **POST** | `/api/auth/logout` | Authenticated | Invalidate current session context | `200 OK` |
| **PUT** | `/api/users/me` | Authenticated | Update faculty profile (fullName, designation, phone) | `200 OK` / `400 Bad Request` |
| **GET** | `/api/users` | `ROLE_ADMIN` | Get institutional faculty roster | `200 OK` / `403 Forbidden` |
| **GET** | `/api/users/{id}` | `ROLE_ADMIN` | Get faculty user detail by ID | `200 OK` / `404 Not Found` |
| **GET** | `/api/users/department` | `ROLE_HOD` | Get faculty in HOD's department | `200 OK` / `403 Forbidden` |
| **GET** | `/api/departments` | Authenticated | Get list of departments for filters | `200 OK` |

---

## Dashboard Analytics Endpoints

| Method | Endpoint | Access | Description | Expected Status |
| :--- | :--- | :--- | :--- | :--- |
| **GET** | `/api/dashboard/faculty` | Authenticated | Faculty user's own analytics (total, pending, approved, rejected, category breakdown, year breakdown, recent 5) | `200 OK` / `401 Unauthorized` |
| **GET** | `/api/dashboard/hod` | `ROLE_HOD` | HOD's department analytics (faculty count, total, status counts, category breakdown, year breakdown, recent submissions) | `200 OK` / `403 Forbidden` |
| **GET** | `/api/dashboard/admin` | `ROLE_ADMIN` | Institutional-wide analytics (total faculty, active faculty, total depts, department comparison table, category breakdown, year breakdown) | `200 OK` / `403 Forbidden` |

---

## Achievement Endpoints Summary

| Method | Endpoint | Access | Description | Expected Status |
| :--- | :--- | :--- | :--- | :--- |
| **POST** | `/api/achievements` | Authenticated | Create a new achievement | `201 Created` |
| **GET** | `/api/achievements/{id}` | Authenticated | Get achievement details by ID | `200 OK` / `404 Not Found` |
| **GET** | `/api/achievements/search` | Authenticated | Server-side paginated search with dynamic filters & whitelisted sorting | `200 OK` / `400 Bad Request` |
| **GET** | `/api/achievements/export/csv` | Authenticated | Export matching achievements to UTF-8 BOM CSV file | `200 OK` / `401 Unauthorized` |
| **GET** | `/api/achievements/user/{userId}` | Authenticated | Get achievements for a user | `200 OK` |
| **GET** | `/api/achievements/status/{status}` | Authenticated | Filter achievements by status | `200 OK` / `400 Bad Request` |
| **GET** | `/api/achievements/department/{id}` | Authenticated | Filter achievements by department | `200 OK` |
| **PUT** | `/api/achievements/{id}` | Owner | Update an achievement | `200 OK` / `403 Forbidden` |
| **PATCH** | `/api/achievements/{id}/verification` | `ROLE_HOD` / `ROLE_ADMIN` | Approve or reject achievement | `200 OK` / `403 Forbidden` |
| **POST** | `/api/achievements/{id}/proof` | Owner | Upload PDF proof document | `200 OK` / `400 Bad Request` |
| **GET** | `/api/achievements/{id}/proof` | Authorized | Stream protected PDF document | `200 OK` / `403 Forbidden` |
| **DELETE** | `/api/achievements/{id}` | Owner | Delete an achievement | `204 No Content` / `403 Forbidden` |

---

## Notification Endpoints Summary (Step 19)

| Method | Endpoint | Access | Description | Expected Status |
| :--- | :--- | :--- | :--- | :--- |
| **GET** | `/api/notifications` | Authenticated | Get paginated notifications for current user | `200 OK` / `401 Unauthorized` |
| **GET** | `/api/notifications/unread-count` | Authenticated | Get count of unread notifications for current user | `200 OK` / `401 Unauthorized` |
| **PATCH** | `/api/notifications/{id}/read` | Owner Only | Mark a notification as read (IDOR protected) | `200 OK` / `403 Forbidden` |
| **PATCH** | `/api/notifications/read-all` | Authenticated | Mark all notifications for current user as read | `200 OK` / `401 Unauthorized` |

---

## Audit Logging Endpoints Summary (Step 20)

| Method | Endpoint | Access | Description | Expected Status |
| :--- | :--- | :--- | :--- | :--- |
| **GET** | `/api/audit-logs` | `ROLE_ADMIN` **or** `VIEW_AUDIT_LOGS` | Server-side paginated search for institutional audit records | `200 OK` / `403 Forbidden` |

### Audit Logging Filters & Security
- Query params: `action`, `entityType`, `actorUserId`, `fromDate`, `toDate`, `page`, `size`, `sortBy`, `sortDir`.
- Access is allowed for **`ROLE_ADMIN`** or any user granted the **`VIEW_AUDIT_LOGS`** permission (Track A). The permission is checked against the database on every request, so revoking it takes effect immediately. Everyone else receives `403 Forbidden`.
- Audit logs are append-only. Passwords, hashes, JWTs, and file binary contents are NEVER recorded.

---

## Fine-Grained Permissions (Track A)

On top of the three roles (`ROLE_FACULTY`, `ROLE_HOD`, `ROLE_ADMIN`), an administrator can grant a **single user** extra abilities without changing their role. A Head of Department can keep `ROLE_HOD` and additionally be given `CREATE_FACULTY`, for example.

- There are **15 permission codes** (see [security.md](security.md) for the full list and rules).
- **`ROLE_ADMIN` holds all 15 permissions implicitly** — admins have no rows in `user_permissions`; the authority is computed in code. This means an admin can never be locked out by an incomplete grant table.
- Permissions are loaded from the database **on every request** (the JWT is unchanged and carries no permission list), so a grant or a revoke takes effect on the user's very next call — no re-login.

### Permission Management Endpoints

| Method | Endpoint | Access | Description | Expected Status |
| :--- | :--- | :--- | :--- | :--- |
| **GET** | `/api/permissions` | `MANAGE_PERMISSIONS` | List the 15 grantable permissions (for the checkbox screen) | `200 OK` / `403 Forbidden` |
| **GET** | `/api/users/{userId}/permissions` | `MANAGE_PERMISSIONS` | The permissions a specific user currently holds | `200 OK` / `403 Forbidden` |
| **PUT** | `/api/users/{userId}/permissions` | `MANAGE_PERMISSIONS` | Replace that user's permission set. Body: `{ "permissionCodes": ["CREATE_FACULTY", ...] }` | `200 OK` / `400` / `403` / `409` |

**Who is doing the editing is always taken from the JWT (`Authentication`), never from the request body.** The service refuses the request when:

| Situation | Status |
| :--- | :--- |
| You try to change your **own** permissions | `403 Forbidden` |
| The target user is an **administrator** (admins already have everything) | `400 Bad Request` |
| An **unknown** permission code is sent (never silently ignored) | `400 Bad Request` |
| A **non-admin** tries to grant `MANAGE_PERMISSIONS` or `CREATE_ADMIN` | `403 Forbidden` |
| You try to grant a permission you **do not hold yourself** | `403 Forbidden` |

### User Management Endpoints

The annotation on each endpoint is a coarse "front door" — the exact rule (which role may be created, whose account may be edited) is decided in the service, where the request body and the target account are both known.

| Method | Endpoint | Access | Description | Expected Status |
| :--- | :--- | :--- | :--- | :--- |
| **POST** | `/api/users` | `ROLE_ADMIN` or `CREATE_FACULTY` / `CREATE_HOD` / `CREATE_ADMIN` | Create an account. `CREATE_FACULTY` may create only Faculty; `CREATE_HOD` only HODs; creating an admin also requires the caller to be an admin. Department is mandatory; password is BCrypt-hashed; a unique `public_slug` is assigned. | `201 Created` / `400` / `403` / `409` |
| **PUT** | `/api/users/{id}` | `ROLE_ADMIN` or `EDIT_FACULTY` / `EDIT_HOD` | Edit an account (email, employee id, department, role, password). Cannot change your own role or department. | `200 OK` / `403` / `409` |
| **PATCH** | `/api/users/{id}/status` | `ROLE_ADMIN` or `MANAGE_USER_STATUS` | Activate / deactivate / suspend. Takes effect on the target's next request. Refused with `409` if it would leave **zero active admins**. | `200 OK` / `403` / `409` |
| **GET** | `/api/users` | `ROLE_ADMIN` or any user-management permission | Full institutional roster (additive to the existing admin access) | `200 OK` / `403` |
| **GET** | `/api/users/{id}` | `ROLE_ADMIN` or any user-management permission | One user's detail (loads the edit form) | `200 OK` / `404` |

### Department Management Endpoints

| Method | Endpoint | Access | Description | Expected Status |
| :--- | :--- | :--- | :--- | :--- |
| **GET** | `/api/departments` | Authenticated | List departments (for filter dropdowns) — unchanged, still open | `200 OK` |
| **GET** | `/api/departments/summary` | `ROLE_ADMIN` or `MANAGE_DEPARTMENTS` | The list plus how many accounts belong to each department | `200 OK` / `403` |
| **POST** | `/api/departments` | `ROLE_ADMIN` or `MANAGE_DEPARTMENTS` | Add a department | `201 Created` / `403` |
| **PUT** | `/api/departments/{id}` | `ROLE_ADMIN` or `MANAGE_DEPARTMENTS` | Rename / change description | `200 OK` / `403` |
| **DELETE** | `/api/departments/{id}` | `ROLE_ADMIN` or `MANAGE_DEPARTMENTS` | Remove a department — refused with `409` while any account still belongs to it | `204 No Content` / `403` / `409` |

---

## Public Endpoints — No Login (Track B)

Everything under `/api/public` runs with **no authentication at all**. These paths are whitelisted in `SecurityConfig` *before* the `anyRequest().authenticated()` rule. An achievement is visible to the public **only when `status = APPROVED` AND `visibility = PUBLIC`** — that rule is written as a literal inside every query, so a visitor cannot send a parameter that widens what they see.

| Method | Endpoint | Description | Expected Status |
| :--- | :--- | :--- | :--- |
| **GET** | `/api/public/faculty?keyword=&departmentCode=&page=0&size=12` | Public faculty directory. Lists only active people who have at least one public achievement, so it never doubles as a full staff roster. | `200 OK` |
| **GET** | `/api/public/faculty/{slug}` | One person's public profile by readable slug (e.g. `rajesh-kumar-cse`). Unknown slug, inactive account and "nothing published" all return the same `404`. | `200 OK` / `404` |
| **GET** | `/api/public/faculty/{slug}/achievements?categoryCode=&page=0&size=20` | That person's public achievements | `200 OK` / `404` |
| **GET** | `/api/public/achievements?keyword=&categoryCode=&departmentCode=&page=0&size=12` | The public research gallery, across everyone | `200 OK` |
| **GET** | `/api/public/departments` | Department list for the filter dropdown (no user counts) | `200 OK` |
| **GET** | `/api/public/share/{token}` | Open a shared achievement. `200` live, `404` unknown token, `410 {"reason":"EXPIRED"}`, `410 {"reason":"REVOKED"}`. Response is `Cache-Control: no-store`. | `200` / `404` / `410` |
| **GET** | `/api/public/share/{token}/document` | The proof PDF **only if** the owner included it (else `403`). Streamed `inline` through the existing storage layer; the token is re-validated on this request. | `200 OK` / `403` / `404` / `410` |

**Public responses use dedicated DTOs** (`PublicFacultyResponse`, `PublicFacultyProfileResponse`, `PublicAchievementResponse`, `SharedAchievementResponse`). They deliberately omit `verificationComment`, `facultyEmail`, `employeeId`, `phone`, `proofDocumentUrl`, `verifiedBy*`, `userId`, `status` and `visibility`. The shared response exposes a boolean `proofDocumentAvailable` flag instead of any file URL.

---

## Share Link Endpoints — Owner Side (Track B)

Faculty create and manage share links for their own **unlisted** research. The owner is always taken from the JWT; a non-owner gets `403`. At most one active link exists per achievement (creating a new one revokes the previous).

| Method | Endpoint | Access | Description | Expected Status |
| :--- | :--- | :--- | :--- | :--- |
| **POST** | `/api/achievements/{id}/share` | Owner | Create a link, replacing any existing one. Body: `{ "duration": "SEVEN_DAYS", "customExpiresAt": null, "includeProofDocument": false }`. | `201 Created` / `403` |
| **GET** | `/api/achievements/{id}/share` | Owner | The current link, or `204 No Content` when none exists yet | `200 OK` / `204` / `403` |
| **PATCH** | `/api/achievements/{id}/share` | Owner | Extend the expiry / change the proof setting — **keeps the same token** so an already-sent link still works | `200 OK` / `403` |
| **DELETE** | `/api/achievements/{id}/share` | Owner | Revoke the link now (idempotent — revoking a dead link is still `204`) | `204 No Content` / `403` |
| **GET** | `/api/achievements/shared` | Authenticated | Every link *I* created, newest first (powers "My Research & Shared Resources") | `200 OK` |

**Duration options:** `THIRTY_MINUTES`, `ONE_HOUR`, `SIX_HOURS`, `TWELVE_HOURS`, `TWENTY_FOUR_HOURS`, `SEVEN_DAYS`, `PERMANENT` (no expiry), `CUSTOM` (uses `customExpiresAt`). The share token is 32 bytes of `SecureRandom` in URL-safe Base64 (43 characters) — never derived from an id, employee number or timestamp. Expiry and revocation are judged by the server on every request; the browser countdown is only decoration.
