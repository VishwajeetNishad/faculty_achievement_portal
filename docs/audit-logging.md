# Audit Logging & Activity Tracking Specification

**Institution**: Noida Institute of Engineering and Technology (NIET)  
**Feature**: Step 20 Real Audit Logging & Activity Tracking

---

## 1. Overview & Architectural Guarantees

The Faculty Achievement Portal implements a **secure, append-only institutional audit trail** to monitor critical security and business domain events.

### Core Guarantees:
1. **Append-Only Immutability**: Audit log entries are strictly append-only. No `UPDATE` or `DELETE` endpoints or service methods exist for audit records.
2. **JWT-Derived Actor Identity**: The actor performing an action is determined **strictly from `SecurityContextHolder` / authenticated JWT**. Attempts to inject or spoof `actorUserId` via request parameters are ignored.
3. **Data Privacy & Secret Protection**: Audit logs **NEVER** record passwords, BCrypt password hashes, JWT tokens, Authorization headers, or raw PDF document byte contents.
4. **Role-Based Privilege Boundaries**: Access to institutional audit logs (`GET /api/audit-logs`) is restricted to **`ROLE_ADMIN` ONLY**. Access attempts by Faculty members or HODs return an immediate `403 Forbidden`.

---

## 2. Audited Actions & Event Matrix

| Audit Action | Entity Type | Trigger Condition | Description Recorded |
| :--- | :--- | :--- | :--- |
| `LOGIN_SUCCESS` | `AUTH` | User authenticates via `/api/auth/login` | `"User signed in successfully: {email}"` |
| `LOGIN_FAILURE` | `AUTH` | Login attempt fails due to invalid credentials | `"Failed login attempt for user: {email}"` *(No password recorded)* |
| `ACHIEVEMENT_CREATED` | `ACHIEVEMENT` | Faculty creates a new achievement | `"Created achievement record: '{title}'"` |
| `ACHIEVEMENT_UPDATED` | `ACHIEVEMENT` | Faculty updates an achievement record | `"Updated achievement record: '{title}'"` |
| `ACHIEVEMENT_DELETED` | `ACHIEVEMENT` | Faculty deletes an achievement record | `"Deleted achievement record id: {id}"` |
| `ACHIEVEMENT_APPROVED` | `ACHIEVEMENT` | HOD/Admin approves achievement record | `"Verified and APPROVED achievement: '{title}'"` |
| `ACHIEVEMENT_REJECTED` | `ACHIEVEMENT` | HOD/Admin rejects achievement record | `"Rejected achievement: '{title}'. Feedback: {comment}"` |
| `PROOF_UPLOADED` | `PROOF` | Faculty uploads PDF proof document | `"Uploaded proof PDF document for achievement id: {id}"` |
| `PROOF_DELETED` | `PROOF` | Faculty deletes proof document | `"Deleted proof PDF document for achievement id: {id}"` |
| `PROFILE_UPDATED` | `USER` | User updates profile details | `"Faculty member updated profile information"` |

---

## 3. What is Intentionally NOT Audited

To protect privacy and minimize noise:
- Read-only data queries (`GET` requests on public resources, categories, departments) are not audited.
- Raw file binary contents or absolute server filesystem paths are never recorded.
- Authentication secrets, passwords, or session tokens are strictly excluded.

---

## 4. Query & Filter Specification (`GET /api/audit-logs`)

- **Authorization**: `ROLE_ADMIN`
- **Query Parameters**:
  - `action`: Filter by `AuditAction` enum
  - `entityType`: Filter by entity name (`ACHIEVEMENT`, `PROOF`, `AUTH`, `USER`)
  - `actorUserId`: Filter by specific user ID
  - `fromDate` & `toDate`: Date range filtering on `createdAt`
  - `page` & `size`: Server-side pagination (max page size 100)
  - `sortBy` & `sortDir`: Whitelisted sorting (`createdAt`, `id`, `action`, `entityType`). Invalid fields return `400 Bad Request`.
