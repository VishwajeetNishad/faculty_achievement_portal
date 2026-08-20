# Database Schema & Relational Structure Documentation

**Institution**: Noida Institute of Engineering and Technology (NIET)  
**Database Engine**: MySQL 8.0+ (`faculty_achievement_db`)  

---

## 1. Table Inventory & Descriptions

The relational database consists of 12 core tables:

### 1. `roles`
- **Purpose**: Defines system user roles (ADMIN, HOD, FACULTY).
- **Primary Key**: `id` (BIGINT AUTO_INCREMENT)
- **Columns**: `name` (VARCHAR 50, UNIQUE), `description` (VARCHAR 255).

### 2. `departments`
- **Purpose**: Stores institutional academic departments.
- **Primary Key**: `id` (BIGINT AUTO_INCREMENT)
- **Columns**: `code` (VARCHAR 20, UNIQUE), `name` (VARCHAR 100), `description` (TEXT), `created_at`, `updated_at`.

### 3. `users`
- **Purpose**: Stores user profile details and authentication credentials.
- **Primary Key**: `id` (BIGINT AUTO_INCREMENT)
- **Foreign Keys**: `department_id` -> `departments(id)`, `role_id` -> `roles(id)`.
- **Important Columns**: `employee_id` (UNIQUE), `email` (UNIQUE), `password_hash`, `full_name`, `designation`, `phone`, `status`.

### 4. `achievement_categories`
- **Purpose**: Master table defining achievement types.
- **Primary Key**: `id` (BIGINT AUTO_INCREMENT)
- **Columns**: `code` (VARCHAR 50, UNIQUE), `category_name` (VARCHAR 100), `description` (TEXT), `is_active`.

### 5. `achievements`
- **Purpose**: Central table storing faculty achievement records.
- **Primary Key**: `id` (BIGINT AUTO_INCREMENT)
- **Foreign Keys**: `user_id` -> `users(id)`, `category_id` -> `achievement_categories(id)`, `verified_by_user_id` -> `users(id)`.
- **Important Columns**: `title`, `description`, `achievement_date`, `academic_year`, `proof_document_url`, `status` (PENDING, APPROVED, REJECTED), `verification_comment`, `verified_at`.

### 6. Specialized Sub-Domain Tables (`publications`, `patents`, `research_grants`, `workshops_fdps`, `awards`)
- **Purpose**: Store specialized category-specific metadata.
- **Foreign Key**: `achievement_id` -> `achievements(id)` (ON DELETE CASCADE).

### 7. `notifications`
- **Purpose**: Stores persistent user in-app notifications.
- **Primary Key**: `id` (BIGINT AUTO_INCREMENT)
- **Foreign Keys**: `recipient_id` -> `users(id)` (CASCADE), `achievement_id` -> `achievements(id)` (SET NULL).
- **Columns**: `title`, `message`, `notification_type`, `is_read`, `created_at`.

### 8. `audit_logs`
- **Purpose**: Immutable security audit trail recording system operations.
- **Primary Key**: `id` (BIGINT AUTO_INCREMENT)
- **Foreign Key**: `actor_user_id` -> `users(id)` (SET NULL).
- **Columns**: `actor_email`, `action`, `entity_type`, `entity_id`, `description`, `ip_address`, `created_at`.
