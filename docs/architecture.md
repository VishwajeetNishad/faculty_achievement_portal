# System Architecture Specification

**Institution**: Noida Institute of Engineering and Technology (NIET)  
**Project**: Faculty Achievement Portal  

---

## 1. Architectural Overview

The Faculty Achievement Portal follows a modern **three-tier client-server architecture** with clear separation of concerns between presentation, application logic, and data persistence layers.

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

## 2. Layered Component Breakdown

### 2.1 Presentation Layer (Frontend)
- Built using **HTML5, Vanilla CSS3, and JavaScript (ES6+)**.
- Communicates with the backend via stateless HTTP REST requests (`fetch` API).
- Manages user sessions by storing JWT tokens in `sessionStorage`.
- Uses custom CSS tokens (Variables, Glassmorphism, Micro-animations) ensuring responsive rendering across all screen viewports.

### 2.2 Security & Authentication Filter Layer
- **Spring Security Filter Chain**: Intercepts every HTTP request.
- **JwtAuthenticationFilter**: Extracts and parses the `Authorization: Bearer <token>` header, validates HS256 signature and expiration, and populates `SecurityContextHolder`.
- **Role-Based Access Control (RBAC)**: Configured in `SecurityConfig.java` to enforce method-level and URL-level permissions.

### 2.3 Application & Controller Layer
- REST Controllers (`AuthController`, `AchievementController`, `UserController`, `DashboardController`, `NotificationController`, `AuditLogController`) validate input DTOs using Jakarta Validation and map incoming JSON requests to service calls.

### 2.4 Service & Business Logic Layer
- Contains core domain workflows (`AchievementServiceImpl`, `NotificationServiceImpl`, `AuditLogServiceImpl`, `FileStorageServiceImpl`).
- Enforces ownership checks, verification status state machines, event notifications, audit log generation, and file validation.

### 2.5 Data Access & Persistence Layer
- **Spring Data JPA & Hibernate**: Maps Java entities (`User`, `Achievement`, `Notification`, `AuditLog`) to relational database tables.
- **Criteria API & Specifications**: `AchievementSpecification` dynamically builds parameterized SQL queries, protecting against SQL injection.
- **MySQL 8.0 Database**: Stores structured records with strict foreign key constraints and index optimizations.

### 2.6 Physical Storage Layer
- Uploaded PDF proof documents are stored in the filesystem (`uploads/achievements/`) using UUID filenames (`UUID.randomUUID().toString() + ".pdf"`). Direct file web access is disabled; downloads stream through the protected API (`GET /api/achievements/{id}/proof`).
