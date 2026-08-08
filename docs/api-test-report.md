# Step 8 — Backend API Test & Verification Report

**Institution**: NIET – Noida Institute of Engineering and Technology  
**Project**: Faculty Achievement Portal  
**Execution Date**: August 8, 2026  
**Server Environment**: Spring Boot 3.3.4 (JDK 21 LTS) on `http://localhost:8080`  
**Database**: MySQL 8.0 (`faculty_achievement_db`)  

---

## 📊 Executive Summary

- **Total Test Cases Executed**: 27
  - **Automated Unit & Controller MockMvc Tests**: 15 / 15 **PASSED**
  - **Live HTTP Integration Tests**: 11 / 11 **PASSED**
  - **Direct Database SQL Integrity Check**: 1 / 1 **PASSED**
- **Maven Build Status**: **SUCCESS** (`BUILD SUCCESS` in 11.661s)
- **Database Schema Modification**: **NONE** (`spring.jpa.hibernate.ddl-auto=validate` active and verified)
- **Security Baseline**: Passwords and credentials 100% excluded from API response objects.

---

## 🧪 Live REST API Integration Test Results

| Test # | Test Name | Method | Endpoint | Expected HTTP | Actual HTTP | Result |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **1** | Create Achievement | `POST` | `/api/achievements?userId=1` | `201 Created` | `201 Created` | **PASS** |
| **2** | Get Achievement by ID | `GET` | `/api/achievements/5` | `200 OK` | `200 OK` | **PASS** |
| **3** | Get Achievements by User | `GET` | `/api/achievements/user/1` | `200 OK` | `200 OK` | **PASS** |
| **4** | Get Achievements by Status | `GET` | `/api/achievements/status/PENDING` | `200 OK` | `200 OK` | **PASS** |
| **5** | Get Achievements by Department | `GET` | `/api/achievements/department/1` | `200 OK` | `200 OK` | **PASS** |
| **6** | Update Achievement | `PUT` | `/api/achievements/5?userId=1` | `200 OK` | `200 OK` | **PASS** |
| **7** | Create Temporary Record | `POST` | `/api/achievements?userId=1` | `201 Created` | `201 Created` | **PASS** |
| **8** | Delete Achievement | `DELETE` | `/api/achievements/6?userId=1` | `204 No Content` | `204 No Content` | **PASS** |
| **9** | DTO Validation (Blank Title) | `POST` | `/api/achievements?userId=1` | `400 Bad Request` | `400 Bad Request` | **PASS** |
| **10** | Resource Not Found | `GET` | `/api/achievements/99999` | `404 Not Found` | `404 Not Found` | **PASS** |
| **11** | Invalid Enum Status | `GET` | `/api/achievements/status/INVALID_STATUS` | `400 Bad Request` | `400 Bad Request` | **PASS** |
| **12** | Ownership Mismatch Check | `PUT` | `/api/achievements/5?userId=99` | `400 Bad Request` | `400 Bad Request` | **PASS** |

---

## 🗄️ Database Integrity & Persistence Verification

A direct SQL query was executed against MySQL `faculty_achievement_db`:
```sql
SELECT id, user_id, category_id, title, status, created_at, updated_at FROM achievements;
```

**Verification Findings**:
1. **Creation**: Inserted records (`#1`, `#3`, `#4`, `#5`) were accurately persisted in table `achievements`.
2. **Update**: Record `#5` title was updated to `"AI in Medical Image Segmentation - Final Revised Edition"` without altering `created_at` timestamp.
3. **Deletion**: Temporary record `#6` was cleanly deleted from MySQL without foreign key violations.
4. **Schema Safety**: Zero structural database modifications occurred (`spring.jpa.hibernate.ddl-auto=validate` maintained).

---

## 🛡️ Security & Privacy Check

1. **Password Exposure Check**: `User` objects serialized in `AchievementResponse` DTO expose only `userId`, `facultyName`, `facultyEmail`, `employeeId`, and `departmentCode`. **Password hash attributes are strictly hidden**.
2. **Authorization Limitations**: Authentication (Spring Security / JWT) is not active in Step 8. The explicit `userId` parameter is currently used for development request scoping and will be replaced by `@AuthenticationPrincipal` context in future steps.

---

## 📦 Automated Test Suite Summary (JUnit 5 + MockMvc)

- **`AchievementControllerTest`**: 10 tests passed (100%)
- **`AchievementServiceTest`**: 5 tests passed (100%)
- **Total Tests Run**: 15, Failures: 0, Errors: 0, Skipped: 0
