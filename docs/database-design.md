# Faculty Achievement Portal — Database Architecture & Specification

## 1. Overview
The database layer for the **Faculty Achievement Portal** is designed using MySQL 8.0+. It employs a hybrid **Table-Per-Subclass / Normalized Entity** pattern:

- **`achievements` (Master Table)**: Stores common metadata across all achievement types (faculty ID, title, date, academic year, status, proof URL, verification notes).
- **Sub-tables (`publications`, `patents`, `research_grants`, `workshops_fdps`, `awards`)**: Maintain 1-to-1 extension relationships with `achievements` to hold category-specific fields without bloating the core schema with nullable fields.

---

## 2. Entity Relationship Diagram (Conceptual Map)

```mermaid
erDiagram
    DEPARTMENTS ||--o{ USERS : "belongs to"
    ROLES ||--o{ USERS : "assigned to"
    USERS ||--o{ ACHIEVEMENTS : "submits"
    USERS ||--o{ ACHIEVEMENTS : "verifies"
    ACHIEVEMENT_CATEGORIES ||--o{ ACHIEVEMENTS : "categorized as"
    
    ACHIEVEMENTS ||--o| PUBLICATIONS : "extends"
    ACHIEVEMENTS ||--o| PATENTS : "extends"
    ACHIEVEMENTS ||--o| RESEARCH_GRANTS : "extends"
    ACHIEVEMENTS ||--o| WORKSHOPS_FDPS : "extends"
    ACHIEVEMENTS ||--o| AWARDS : "extends"

    USERS ||--o{ USER_PERMISSIONS : "granted (Track A)"
    PERMISSIONS ||--o{ USER_PERMISSIONS : "referenced by"
    USERS ||--o{ SHARE_LINKS : "creates (Track B)"
    ACHIEVEMENTS ||--o{ SHARE_LINKS : "shared via"
```

---

## 3. Data Dictionary & Table Definitions

### 3.1 `departments`
Stores academic department classifications.
| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | PRIMARY KEY, AUTO_INCREMENT | Unique department ID |
| `code` | `VARCHAR(20)` | UNIQUE, NOT NULL | Short code (e.g. `CSE`, `ECE`) |
| `name` | `VARCHAR(100)` | NOT NULL | Full department name |
| `description` | `VARCHAR(255)` | NULL | Optional description |
| `created_at` | `DATETIME` | DEFAULT CURRENT_TIMESTAMP | Record creation timestamp |

### 3.2 `roles`
Defines access control security roles.
| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | PRIMARY KEY, AUTO_INCREMENT | Role ID |
| `name` | `VARCHAR(50)` | UNIQUE, NOT NULL | Role identifier (`ROLE_ADMIN`, `ROLE_HOD`, `ROLE_FACULTY`) |
| `description` | `VARCHAR(255)` | NULL | Role permissions summary |

### 3.3 `users`
Faculty, HOD, and Administrator user accounts.
| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | PRIMARY KEY, AUTO_INCREMENT | Unique user ID |
| `employee_id` | `VARCHAR(50)` | UNIQUE, NOT NULL | Staff / Employee Registration Code |
| `full_name` | `VARCHAR(100)` | NOT NULL | User's full name |
| `email` | `VARCHAR(100)` | UNIQUE, NOT NULL | Institutional email address |
| `public_slug` | `VARCHAR(120)` | UNIQUE, NULL | Readable id for the public profile URL, e.g. `rajesh-kumar-cse` (Track B) |
| `password_hash` | `VARCHAR(255)` | NOT NULL | BCrypt hashed password |
| `designation` | `VARCHAR(100)` | NOT NULL | Assistant Professor, Professor, HOD, etc. |
| `department_id` | `BIGINT` | FK -> `departments.id` | Associated department |
| `role_id` | `BIGINT` | FK -> `roles.id` | Assigned security role |
| `phone` | `VARCHAR(20)` | NULL | Contact telephone number |
| `status` | `ENUM` | `ACTIVE`, `INACTIVE`, `SUSPENDED` | User account lifecycle status |

### 3.4 `achievements` (Master Entity)
Central registry for all submitted faculty achievements.
| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | PRIMARY KEY, AUTO_INCREMENT | Achievement Record ID |
| `user_id` | `BIGINT` | FK -> `users.id` | Faculty member who submitted |
| `category_id` | `BIGINT` | FK -> `achievement_categories.id` | Achievement category |
| `title` | `VARCHAR(255)` | NOT NULL | Achievement title / subject |
| `description` | `TEXT` | NULL | Detailed narrative or summary |
| `keywords` | `VARCHAR(500)` | NULL | Comma-separated keywords for public search (Track B) |
| `achievement_date` | `DATE` | NOT NULL | Date of achievement / event |
| `academic_year` | `VARCHAR(20)` | NOT NULL | Academic session (e.g. `2024-2025`) |
| `status` | `ENUM` | `PENDING`, `APPROVED`, `REJECTED` | Verification lifecycle state |
| `visibility` | `ENUM` | `PUBLIC`, `UNLISTED`, `PRIVATE` (NOT NULL, DEFAULT `PRIVATE`) | Who may see it (Track B). Public site needs `APPROVED` + `PUBLIC` together |
| `verification_comment` | `TEXT` | NULL | Notes from HOD/Admin reviewer |
| `verified_by_user_id` | `BIGINT` | FK -> `users.id` | HOD or Admin user who verified |
| `verified_at` | `DATETIME` | NULL | Verification timestamp |
| `proof_document_url` | `VARCHAR(500)` | NULL | Link/path to certificate or proof file |

### 3.5 Specialized Extension Tables

1. **`publications`**: Tracks journals, conference papers, indexing (`SCOPUS`, `WEB_OF_SCIENCE`, `UGC_CARE`), DOI, impact factor.
2. **`patents`**: Tracks patent application status (`FILED`, `PUBLISHED`, `GRANTED`), patent number, filing date, grant date.
3. **`research_grants`**: Tracks funding agencies, project titles, sanction amounts, project type (`RESEARCH`, `CONSULTANCY`, `INFRASTRUCTURE`).
4. **`workshops_fdps`**: Tracks event types (`FDP`, `WORKSHOP`, `SEMINAR`), role (`ATTENDED`, `ORGANIZED`, `RESOURCE_PERSON`), duration.
5. **`awards`**: Tracks honors, awarding bodies, and scope (`NATIONAL`, `INTERNATIONAL`, `STATE`, `INSTITUTIONAL`).

### 3.6 `permissions` (Track A)
The list of fine-grained abilities an Admin can grant on top of a user's role. Seeded with 15 fixed rows (migration `V3`).
| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | PRIMARY KEY, AUTO_INCREMENT | Permission ID |
| `permission_code` | `VARCHAR(50)` | UNIQUE, NOT NULL | Machine code, e.g. `CREATE_FACULTY`, `VIEW_AUDIT_LOGS` |
| `description` | `VARCHAR(255)` | NULL | Plain-language meaning (shown on the Admin screen) |
| `created_at` | `DATETIME` | DEFAULT CURRENT_TIMESTAMP | Record creation timestamp |

### 3.7 `user_permissions` (Track A)
Join table linking a user to each extra permission they have been granted. **Administrators have no rows here** — they hold all 15 permissions implicitly, computed in code.
| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | PRIMARY KEY, AUTO_INCREMENT | Row ID |
| `user_id` | `BIGINT` | FK -> `users.id` `ON DELETE CASCADE` | The user being granted the permission |
| `permission_id` | `BIGINT` | FK -> `permissions.id` `ON DELETE CASCADE` | The granted permission |
| `granted_at` | `DATETIME` | DEFAULT CURRENT_TIMESTAMP | When it was granted |

*Constraints:* `UNIQUE (user_id, permission_id)` (a permission cannot be granted twice); indexes on `user_id` and `permission_id`.

### 3.8 `share_links` (Track B)
A temporary, no-login link that lets a specific person view one faculty member's *unpublished* achievement.
| Column | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | PRIMARY KEY, AUTO_INCREMENT | Share-link ID |
| `achievement_id` | `BIGINT` | FK -> `achievements.id` `ON DELETE CASCADE` | The shared achievement |
| `created_by_user_id` | `BIGINT` | FK -> `users.id` `ON DELETE CASCADE` | The owner who created the link |
| `share_token` | `VARCHAR(64)` | UNIQUE, NOT NULL | 32 bytes of `SecureRandom`, URL-safe Base64 (43 chars) — the bearer credential |
| `expires_at` | `DATETIME` | NULL | Expiry moment; **`NULL` means permanent** |
| `include_proof_document` | `BOOLEAN` | NOT NULL, DEFAULT `FALSE` | Whether the proof PDF is reachable via the link |
| `revoked` | `BOOLEAN` | NOT NULL, DEFAULT `FALSE` | Set `TRUE` to kill the link immediately |
| `revoked_at` | `DATETIME` | NULL | When it was revoked |
| `access_count` | `BIGINT` | DEFAULT 0 | How many times the link has been opened |
| `last_accessed_at` | `DATETIME` | NULL | Last time it was opened |
| `created_at` | `DATETIME` | DEFAULT CURRENT_TIMESTAMP | Record creation timestamp |
| `updated_at` | `DATETIME` | ON UPDATE CURRENT_TIMESTAMP | Last change timestamp |

*Constraints:* indexes on `share_token`, `achievement_id`, `created_by_user_id`, and `expires_at`. Expiry and revocation are always re-checked by the server on each request.

---

## 4. Execution & Setup Instructions

### Option A: Using MySQL Command Line Client (PowerShell)
Execute the DDL script using the `mysql` CLI tool:

```powershell
& "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" -u root -p < docs/schema.sql
```

### Option B: Using MySQL Workbench
1. Open **MySQL Workbench** and connect to your local MySQL instance (`localhost:3306`).
2. Navigate to **File -> Open SQL Script...** and select [`docs/schema.sql`](schema.sql).
3. Click the **Execute (Lightning Bolt)** button to execute the full script.
4. Verify that `faculty_achievement_db` appears under **Schemas** with all 15 tables created.

---

## 5. Status
- The schema is applied through **Flyway migrations** (`V1` initial schema, `V2` reference data, `V3` permissions, `V4` visibility + share links). `docs/schema.sql` is the same schema as a single full-DDL script for reference and fresh manual setup.
- Spring runs with `spring.jpa.hibernate.ddl-auto=validate`, so the running entities are checked against this schema on every startup — the database is never auto-altered by the application. Every change is an explicit migration.
- Current schema covers the base portal plus **Track A** (`permissions`, `user_permissions`) and **Track B** (`achievements.visibility`, `achievements.keywords`, `users.public_slug`, `share_links`).
