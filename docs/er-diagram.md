# Entity-Relationship (ER) Diagram Specification

**Institution**: Noida Institute of Engineering and Technology (NIET)  

---

## 1. Entity-Relationship Conceptual Model

```mermaid
erDiagram
    ROLES ||--o{ USERS : "assigned to"
    DEPARTMENTS ||--o{ USERS : "belongs to"
    USERS ||--o{ ACHIEVEMENTS : "submits"
    USERS ||--o{ ACHIEVEMENTS : "verifies"
    ACHIEVEMENT_CATEGORIES ||--o{ ACHIEVEMENTS : "categorizes"
    
    ACHIEVEMENTS ||--o| PUBLICATIONS : "details"
    ACHIEVEMENTS ||--o| PATENTS : "details"
    ACHIEVEMENTS ||--o| RESEARCH_GRANTS : "details"
    ACHIEVEMENTS ||--o| WORKSHOPS_FDPS : "details"
    ACHIEVEMENTS ||--o| AWARDS : "details"
    
    USERS ||--o{ NOTIFICATIONS : "receives"
    ACHIEVEMENTS ||--o{ NOTIFICATIONS : "references"
    USERS ||--o{ AUDIT_LOGS : "performs action"

    USERS {
        bigint id PK
        string email
        string employee_id
        string password_hash
        string full_name
        bigint role_id FK
        bigint department_id FK
    }

    ACHIEVEMENTS {
        bigint id PK
        bigint user_id FK
        bigint category_id FK
        string title
        string status
        bigint verified_by_user_id FK
        datetime verified_at
    }

    NOTIFICATIONS {
        bigint id PK
        bigint recipient_id FK
        bigint achievement_id FK
        string notification_type
        boolean is_read
    }

    AUDIT_LOGS {
        bigint id PK
        bigint actor_user_id FK
        string action
        string entity_type
        bigint entity_id
        datetime created_at
    }
```

---

## 2. Cardinality Rules

1. **User to Role**: Many-to-One (`USERS.role_id` -> `ROLES.id`). Each user has exactly one role.
2. **User to Department**: Many-to-One (`USERS.department_id` -> `DEPARTMENTS.id`).
3. **User to Achievements**: One-to-Many (`ACHIEVEMENTS.user_id` -> `USERS.id`). A faculty member can submit multiple achievements.
4. **Achievement to Verification**: Many-to-One (`ACHIEVEMENTS.verified_by_user_id` -> `USERS.id`).
5. **Achievement to Sub-Category Details**: One-to-One optional mapping with `publications`, `patents`, `research_grants`, `workshops_fdps`, and `awards`.
6. **User to Notifications**: One-to-Many (`NOTIFICATIONS.recipient_id` -> `USERS.id`).
7. **User to Audit Logs**: One-to-Many (`AUDIT_LOGS.actor_user_id` -> `USERS.id`).
