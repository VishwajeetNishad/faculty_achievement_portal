# Faculty Achievement Portal

## Institution
**NIET – Noida Institute of Engineering and Technology**

---

## Description
A comprehensive web-based application designed to record, track, manage, verify, and report academic achievements, research publications, patents, research grants, workshops/FDPs, and awards of faculty members.

---

## Tech Stack
- **Backend Framework**: Java 21 LTS, Spring Boot 3.3.4, Maven
- **Database**: MySQL 8.0 Relational Database (`faculty_achievement_db`)
- **ORM**: Spring Data JPA / Hibernate (`spring.jpa.hibernate.ddl-auto=validate`)
- **Architecture**: Layered RESTful Architecture (Controller -> Service -> Repository -> Entity -> MySQL)
- **Frontend**: Vanilla HTML5, CSS3, JavaScript (To be connected)

---

## Project Structure
```text
faculty-achievement-portal/
├── backend/                  # Spring Boot 3.3.4 REST API
│   ├── src/
│   │   ├── main/java/com/niet/facultyachievement/
│   │   │   ├── FacultyAchievementApplication.java
│   │   │   ├── controller/
│   │   │   │   └── AchievementController.java
│   │   │   ├── service/
│   │   │   │   ├── AchievementService.java
│   │   │   │   └── AchievementServiceImpl.java
│   │   │   ├── repository/
│   │   │   │   ├── AchievementRepository.java
│   │   │   │   ├── UserRepository.java
│   │   │   │   ├── DepartmentRepository.java
│   │   │   │   ├── AchievementCategoryRepository.java
│   │   │   │   └── [Specialized Extension Repositories...]
│   │   │   ├── dto/
│   │   │   │   ├── AchievementCreateRequest.java
│   │   │   │   ├── AchievementUpdateRequest.java
│   │   │   │   ├── AchievementResponse.java
│   │   │   │   └── ErrorResponse.java
│   │   │   ├── entity/
│   │   │   │   └── [10 Entities + 10 Enums]
│   │   │   └── exception/
│   │   │       ├── ResourceNotFoundException.java
│   │   │       ├── BadRequestException.java
│   │   │       └── GlobalExceptionHandler.java
│   │   └── test/java/com/niet/facultyachievement/
│   │       ├── controller/AchievementControllerTest.java
│   │       └── service/AchievementServiceTest.java
│   └── pom.xml
├── docs/                     # Documentation & Database Schema DDL
│   ├── schema.sql            # MySQL DDL Script (10 Normalized Tables)
│   ├── seed.sql              # Database Seed Data Script
│   ├── database-design.md    # ER Diagram & Data Dictionary Specification
│   ├── api.md                # REST API Specification Document
│   └── api-test-report.md    # Step 8 Verification & Testing Report
├── .gitignore                # Git ignored files configuration
└── README.md                 # Project progress log & documentation
```

---

## 📌 Complete Development Log (Steps 1 – 8)

### Step 1 — Project Structure & Environment Setup
- Established root project layout (`backend/`, `frontend/`, `docs/`).
- Installed Eclipse Temurin OpenJDK 21 LTS (`jdk-21.0.12.8-hotspot`) and configured `JAVA_HOME`.
- Configured `.gitignore` to protect sensitive local developer files, credentials, and build outputs.
- Initialized local Git repository on branch `main` and linked remote `https://github.com/VishwajeetNishad/faculty_achievement_portal.git`.

### Step 2 — Relational Database Architecture
- Designed normalized 10-table relational schema (`faculty_achievement_db`).
- Authored DDL script [`docs/schema.sql`](file:///c:/Users/vishw/Desktop/Faculty/faculty-achievement-portal/docs/schema.sql) defining tables (`departments`, `roles`, `users`, `achievement_categories`, `achievements`, `publications`, `patents`, `research_grants`, `workshops_fdps`, `awards`).
- Authored seed data script [`docs/seed.sql`](file:///c:/Users/vishw/Desktop/Faculty/faculty-achievement-portal/docs/seed.sql).
- Documented data dictionary and entity-relationship specifications in [`docs/database-design.md`](file:///c:/Users/vishw/Desktop/Faculty/faculty-achievement-portal/docs/database-design.md).

### Step 3 — MySQL Database Implementation
- Executed DDL and DML scripts in MySQL Workbench on local MySQL 8.0 server (`MySQL80` service running on port 3306).
- Verified primary keys, foreign key constraints, default timestamps, and indexes.

### Step 4 — Spring Boot + MySQL Connection Setup
- Created Spring Boot 3.3.4 project under package `com.niet.facultyachievement`.
- Configured `pom.xml` with dependencies (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `mysql-connector-j`, `lombok`).
- Configured `application.properties` with database connection string and enforced `spring.jpa.hibernate.ddl-auto=validate`.
- Successfully verified zero-error backend startup and HikariCP connection pool initialization.

### Step 5 — Java Entity & Enum JPA Mapping
- Mapped all 10 MySQL database tables 1-to-1 to JPA Java Entities (`Department`, `Role`, `User`, `AchievementCategory`, `Achievement`, `Publication`, `Patent`, `ResearchGrant`, `WorkshopFdp`, `Award`).
- Mapped 10 Java Enums matching MySQL `ENUM` types 100% (`UserStatus`, `AchievementStatus`, `PublicationType`, `PublicationIndexing`, `PatentStatus`, `ProjectType`, `GrantStatus`, `EventType`, `EventRole`, `AwardLevel`).
- Established `@ManyToOne` and `@OneToOne(mappedBy = ...)` bidirectional relationships using `FetchType.LAZY` to prevent N+1 queries.

### Step 6 — Repository, Service, DTO & Validation Layer
- Created 10 Spring Data JPA Repository interfaces (`AchievementRepository`, `UserRepository`, `DepartmentRepository`, etc.) extending `JpaRepository<Entity, Long>`.
- Created DTO payload classes (`AchievementCreateRequest`, `AchievementUpdateRequest`, `AchievementResponse`, `ErrorResponse`).
- Implemented Jakarta Bean Validation (`@NotNull`, `@NotBlank`, `@Size`, `@PastOrPresent`) on request DTOs.
- Created `AchievementService` interface and `AchievementServiceImpl` class handling business logic, status control (`PENDING`), and DTO transformations.
- Created custom exceptions (`ResourceNotFoundException`, `BadRequestException`).
- Created unit tests (`AchievementServiceTest`) passing 5/5 tests cleanly.

### Step 7 — REST Controller Layer & API Endpoint Exposure
- Implemented `AchievementController` (`@RestController`, `@RequestMapping("/api/achievements")`) exposing 7 endpoints:
  - `POST /api/achievements`
  - `GET /api/achievements/{id}`
  - `GET /api/achievements/user/{userId}`
  - `GET /api/achievements/status/{status}`
  - `GET /api/achievements/department/{departmentId}`
  - `PUT /api/achievements/{id}`
  - `DELETE /api/achievements/{id}`
- Created `GlobalExceptionHandler` (`@RestControllerAdvice`) producing uniform JSON error payloads.
- Created REST API documentation [`docs/api.md`](file:///c:/Users/vishw/Desktop/Faculty/faculty-achievement-portal/docs/api.md).
- Created MockMvc integration tests (`AchievementControllerTest`) passing 10/10 tests cleanly.

### Step 8 — Backend API Testing & Persistence Verification
- Verified live server startup on port 8080 and database connectivity.
- Executed 15/15 automated unit and controller MockMvc tests (`BUILD SUCCESS`).
- Executed 11/11 live HTTP end-to-end integration tests covering creation, retrieval, filtering, updates, deletion, validation errors, 404 handling, invalid enum handling, and ownership checks (100% PASS).
- Executed direct MySQL SQL query confirming persistence, timestamp generation, and safe record deletion in `faculty_achievement_db`.
- Generated comprehensive test verification report [`docs/api-test-report.md`](file:///c:/Users/vishw/Desktop/Faculty/faculty-achievement-portal/docs/api-test-report.md).
- All changes committed and pushed to GitHub `main` branch (`Everything up-to-date`).
