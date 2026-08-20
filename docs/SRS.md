# Software Requirements Specification (SRS)

**Project Title**: Faculty Achievement Portal  
**Institution**: Noida Institute of Engineering and Technology (NIET)  
**Document Version**: 1.0 (Final)  

---

## 1. Introduction

### 1.1 Purpose
This Software Requirements Specification (SRS) document details the functional, non-functional, security, and architectural requirements for the **Faculty Achievement Portal** developed for NIET.

### 1.2 Scope
The system handles faculty achievement submissions, departmental verification by HODs, institutional oversight by Admins, PDF proof document storage, event notifications, audit logging, and accreditation reporting.

### 1.3 Intended Audience
This document is intended for academic evaluators, project supervisors, software developers, system administrators, and quality assurance testers.

---

## 2. Overall Description

### 2.1 User Roles
1. **FACULTY**: Submits achievements, uploads PDF proofs, tracks status, updates personal profile, exports personal records to CSV, and receives notifications.
2. **HOD (Head of Department)**: Inspects department-wide submissions, downloads proof certificates, approves/rejects achievements with feedback, views department analytics, and receives alerts.
3. **ADMIN (System Administrator)**: Manages institutional faculty roster, verifies achievements across all departments, inspects system audit logs, views institutional analytics, and exports institutional reports.

### 2.2 System Constraints
- Must run on Java 21 LTS runtime.
- Database must be MySQL 8.0+ with `spring.jpa.hibernate.ddl-auto=validate`.
- File upload is strictly restricted to valid PDF documents up to 10 MB.
- Architecture must be stateless REST API using JWT tokens for session management.

---

## 3. Functional Requirements

### 3.1 Authentication & Profile Management
- **FR-AUTH-01**: The system shall authenticate users via email/employee ID and password, issuing a signed 24-hour JWT token.
- **FR-AUTH-02**: Passwords must be hashed using BCrypt before storage.
- **FR-USER-01**: Faculty members shall update their self-profile details (fullName, designation, phone) via `PUT /api/users/me`. Critical security fields (`role`, `departmentId`, `status`) must remain tamper-proof.

### 3.2 Achievement Submission & Management
- **FR-ACH-01**: Faculty shall create achievements under 5 categories: Journal/Conference Publications, Patents, Research Grants, Workshops/FDPs, and Awards.
- **FR-ACH-02**: Initial achievement status must be `PENDING`.
- **FR-ACH-03**: Faculty shall upload a PDF proof certificate (`POST /api/achievements/{id}/proof`).
- **FR-ACH-04**: Achievements shall be editable or deletable by the owner only when in `PENDING` or `REJECTED` status. Approved achievements are read-only.

### 3.3 Verification Workflow
- **FR-VER-01**: HODs shall approve or reject achievements belonging to faculty in their department.
- **FR-VER-02**: Admins shall approve or reject achievements across all departments.
- **FR-VER-03**: Rejection requires mandatory comment feedback explaining the reason.
- **FR-VER-04**: Upon verification, `verifiedBy`, `verifiedAt`, and `status` fields shall be updated atomically.

### 3.4 Search, Filter & CSV Export
- **FR-SRCH-01**: The system shall provide multi-criterion backend search filtering by keyword, status, category, date range, academic year, and department.
- **FR-SRCH-02**: Search results shall support server-side pagination (max page size 100) and whitelisted sorting.
- **FR-EXP-01**: The system shall generate downloadable UTF-8 BOM CSV reports obeying role authorization scope.

### 3.5 Notifications & Audit Logging
- **FR-NOTIF-01**: Event triggers shall generate notifications upon submission, review assignment, approval, and rejection.
- **FR-AUDIT-01**: The system shall maintain an append-only audit trail recording user logins, achievement CRUD operations, proof document actions, and profile updates.

---

## 4. Non-Functional Requirements

### 4.1 Security & Data Protection
- **NFR-SEC-01**: All protected REST API requests must require a valid JWT token in the `Authorization` header.
- **NFR-SEC-02**: Insecure Direct Object Reference (IDOR) attacks must be blocked by validating resource ownership and role scope server-side.
- **NFR-SEC-03**: PDF uploads must undergo deep magic-byte validation (`%PDF-`) and UUID filename generation to eliminate path traversal.

### 4.2 Performance & Scalability
- **NFR-PERF-01**: API response times for authentication, search, and dashboard queries shall not exceed 200 ms under normal load.
- **NFR-PERF-02**: The database connection pool (HikariCP) shall efficiently manage concurrent database requests.

### 4.3 Usability & Responsiveness
- **NFR-USA-01**: The user interface shall be fully responsive across mobile (320px+), tablet, laptop, and desktop viewports without horizontal overflow.
