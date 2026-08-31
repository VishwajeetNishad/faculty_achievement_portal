# System Integration & End-to-End Testing Documentation

**Institution**: Noida Institute of Engineering and Technology (NIET)  
**Feature**: Step 22 System Integration & E2E Testing

---

## 1. Test Environment Specification

- **Java**: OpenJDK 21 HotSpot
- **Build Tool**: Apache Maven (mvnd 1.0.6)
- **Framework**: Spring Boot 3.3.4 (Spring Web, Spring Security, Spring Data JPA)
- **Database Engine**: MySQL 8.0+ (`faculty_achievement_db`)
- **Schema Management**: `spring.jpa.hibernate.ddl-auto=validate` (Validated against pre-created relational schema)
- **File Upload Storage**: Configured local directory `uploads/achievements` with 10 MB file size limit.

---

## 2. Test User Inventory & Credentials

| Role | User Name | Email Address | Password | Department |
| :--- | :--- | :--- | :--- | :--- |
| **System Admin** | System Administrator | `admin@faculty.edu` | `Password@123` | All / Institutional Scope |
| **Department HOD** | Dr. HOD CSE | `hod.cse@niet.ac.in` | `Password@123` | Computer Science & Engineering (ID: 1) |
| **Faculty Member** | Dr. Security Test | `faculty2@niet.ac.in` | `Password@123` | Computer Science & Engineering (ID: 1) |

---

## 3. Test Suites & Summary Results

Two different things are recorded below, kept apart on purpose. §3.1 is the automated
suite — it runs on every build and anyone can reproduce its numbers. §3.2 is a record
of scenarios walked by hand against a running server. Summing the two into a single
"test coverage" figure would present hand-verification as automation, so they are not
summed.

### 3.1 Automated test suite

Produced by `mvn test` in `backend/`. JUnit 5 + Mockito, no Spring context, no MySQL —
so the counts below need nothing but a clone and a JDK.

| Area | Test classes | Tests | Passed | Failed | Status |
| :--- | :--- | :---: | :---: | :---: | :---: |
| **Security** | `HighlightSecurityTest` 21, `NaacReportSecurityTest` 12, `ShareLinkSecurityTest` 9, `PermissionSecurityTest` 7, `PublicAccessSecurityTest` 2 | 51 | 51 | 0 | PASSED ✅ |
| **Service** | `HighlightImageStorageTest` 21, `NaacReportAggregationTest` 24, `AchievementServiceTest` 9, `NotificationServiceTest` 4, `AuditLogServiceTest` 3 | 61 | 61 | 0 | PASSED ✅ |
| **Controller** | `AchievementControllerTest` | 13 | 13 | 0 | PASSED ✅ |
| **Utility** | `PasswordTest` | 1 | 1 | 0 | PASSED ✅ |
| **TOTAL** | **12 test classes** | **126** | **126** | **0** | **PASSED ✅** |

### 3.2 Manual verification scenarios

Executed by hand against a running backend during development of the steps named.
These are a development record, not a regression suite: they do not run on a build,
and nothing re-checks them when the code changes.

| Verification Pass | Focus Area | Scenarios Run | Passed | Failed | Status |
| :--- | :--- | :---: | :---: | :---: | :---: |
| **Step 17** | Dashboard analytics & math consistency | 27 | 27 | 0 | PASSED ✅ |
| **Step 18** | Search, pagination & CSV export scope | 30 | 30 | 0 | PASSED ✅ |
| **Step 19** | In-app notifications & IDOR security | 20 | 20 | 0 | PASSED ✅ |
| **Step 20** | Append-only audit logging & privacy | 24 | 24 | 0 | PASSED ✅ |
| **Step 21** | Application security hardening & attack defense | 19 | 19 | 0 | PASSED ✅ |
| **Step 22** | End-to-end multi-role workflows & concurrency | 21 | 21 | 0 | PASSED ✅ |
| **SUBTOTAL** | **Manual end-to-end verification** | **141** | **141** | **0** | **PASSED ✅** |

---

## 4. Key Workflows Verified

1. **Complete Faculty Workflow**: Login → JWT retrieval → Profile update → Achievement creation → Initial PENDING status → PDF proof upload → Notification emission → Search & CSV export → Self-approval blocking (`403 Forbidden`).
2. **Complete HOD Workflow**: Login → HOD Dashboard analytics → Verification notification reception → Proof document download → Approval/Rejection with comment → Audit trail logging.
3. **Complete Admin Workflow**: Login → Institutional dashboard analytics → Faculty roster listing → Institution-wide achievement search & CSV export → Audit log inspection.
4. **Concurrency & Double Verification**: Multiple verification attempts on an already-reviewed achievement safely return `400 Bad Request` ("Already reviewed").
5. **Data & Filesystem Consistency**: Direct SQL comparison matches backend dashboard counts. Achievement deletions execute physical PDF file cleanup, leaving zero orphan files or DB records.
