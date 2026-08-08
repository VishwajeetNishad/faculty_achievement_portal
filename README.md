# Faculty Achievement Portal

## Description
A comprehensive web-based application designed to record, track, manage, and report academic achievements, research publications, patents, research grants, workshops/FDPs, and awards of faculty members.

## Planned Technology Stack
- **Frontend**: HTML5, CSS3, Vanilla JavaScript
- **Backend**: Java 21 LTS, Spring Boot 3.3.x, Maven
- **Database**: MySQL 8.0+
- **ORM**: Spring Data JPA / Hibernate
- **Architecture**: RESTful API

---

## Project Structure
```text
faculty-achievement-portal/
├── backend/                  # Spring Boot 3.3.x Java 21 REST API
│   ├── src/
│   │   ├── main/java/com/faculty/portal/
│   │   └── main/resources/
│   ├── mvnw & mvnw.cmd       # Maven Wrapper
│   └── pom.xml               # Dependencies configuration
├── frontend/                 # HTML5, CSS3, Vanilla JavaScript UI
├── docs/                     # Documentation & Database Schema DDL
│   ├── schema.sql            # MySQL DDL Script
│   └── database-design.md    # ER Diagram & Data Dictionary
├── .gitignore                # Git ignored files configuration
└── README.md                 # Project progress log & documentation
```

---

## Development Progress Log (Chronological Order)

### Step 1 — Project Structure & Git Initialization
- Established project directory hierarchy: `backend/`, `frontend/`, `docs/`.
- Configured `.gitignore` covering Java, Maven, IntelliJ, VS Code, Windows OS, environment files, and build outputs.
- Initialized Git repository on branch `master`.

### System Health & Development Environment Configuration
- Inspected development system and detected existing JDK 24 and MySQL 8.0.
- Installed **Eclipse Temurin OpenJDK 21 LTS** (`jdk-21.0.12.8-hotspot`) for Spring Boot compatibility and set `JAVA_HOME`.
- Added MySQL binary path (`C:\Program Files\MySQL\MySQL Server 8.0\bin`) to User `PATH`.
- Verified Git, MySQL Server (`MySQL80` service active on port 3306), and MySQL Workbench installations.

### Step 2 — Database Design & Schema Architecture
- Designed normalized relational database model (`faculty_achievement_db`).
- Created [`docs/schema.sql`](file:///c:/Users/vishw/Desktop/Faculty/faculty-achievement-portal/docs/schema.sql) containing DDL for 10 tables:
  - `departments`, `roles`, `users`, `achievement_categories`
  - `achievements` (master table)
  - `publications`, `patents`, `research_grants`, `workshops_fdps`, `awards` (extension tables)
- Documented ER Diagram, data dictionary, enums, and indexes in [`docs/database-design.md`](file:///c:/Users/vishw/Desktop/Faculty/faculty-achievement-portal/docs/database-design.md).
- Executed and verified database schema creation in local MySQL Workbench instance.

### Step 3 — Spring Boot Backend Initialization (Current Step)
- Initializing Java 21 Spring Boot REST API inside `backend/`.
- Setting up Maven `pom.xml` with Spring Data JPA, Web, MySQL Driver, Lombok.
- Configuring `application.properties` for MySQL connection.
- Adding Maven Wrapper (`mvnw`, `mvnw.cmd`).
- Creating base application class `FacultyAchievementPortalApplication.java` and `/api/health` REST endpoint controller.
