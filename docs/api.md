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
| **GET** | `/api/audit-logs` | `ROLE_ADMIN` | Server-side paginated search for institutional audit records | `200 OK` / `403 Forbidden` |

### Audit Logging Filters & Security
- Query params: `action`, `entityType`, `actorUserId`, `fromDate`, `toDate`, `page`, `size`, `sortBy`, `sortDir`.
- Access restricted strictly to **`ROLE_ADMIN`**. Faculty members and HODs receive `403 Forbidden`.
- Audit logs are append-only. Passwords, hashes, JWTs, and file binary contents are NEVER recorded.
