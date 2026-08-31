# Viva Preparation Guide — 40 Technical Questions & Answers

**Institution**: Noida Institute of Engineering and Technology (NIET)  
**Project**: Faculty Achievement Portal  

---

### Q1: Why was Java 21 and Spring Boot chosen for this project?
**Ans**: Java 21 LTS provides long-term stability, virtual threads, and enhanced performance. Spring Boot 3.3.4 simplifies enterprise web development with built-in dependency injection, embedded Tomcat, robust Spring Security integration, and Spring Data JPA.

### Q2: How does JWT authentication work in this application?
**Ans**: Upon valid login (`POST /api/auth/login`), the backend generates a digitally signed JSON Web Token (JWT) using HS256 algorithm and a 256-bit key. The client stores the token in `sessionStorage` and attaches it to protected REST requests in the `Authorization: Bearer <token>` header. `JwtAuthenticationFilter` intercepts and verifies the token on every request.

### Q3: Why is BCrypt used for password hashing?
**Ans**: BCrypt incorporates a salt to protect against rainbow table attacks and uses an adaptive work factor (strength 10), making brute-force password cracking computationally infeasible. Plaintext passwords are never stored.

### Q4: Why is `spring.jpa.hibernate.ddl-auto=validate` used in production?
**Ans**: Setting `ddl-auto=validate` ensures Hibernate verifies that JPA entity mappings match the pre-existing relational database schema on application startup without modifying, dropping, or altering database tables.

### Q5: How is Insecure Direct Object Reference (IDOR) prevented?
**Ans**: Every API endpoint handling entity IDs (e.g. `/achievements/{id}/proof`, `/notifications/{id}/read`) derives requesting user identity from `SecurityContextHolder` (JWT). The backend verifies ownership or role privileges before granting access.

### Q6: How does PDF magic-byte validation work?
**Ans**: Rather than relying on easily spoofed file extensions or MIME headers, `FileStorageServiceImpl` inspects the initial 4 bytes of the file stream to ensure they match ASCII `%PDF` (`0x25, 0x50, 0x44, 0x46`).

### Q7: How are Path Traversal attacks prevented during file upload?
**Ans**: Uploaded files are assigned random UUID filenames (e.g., `550e8400-e29b-41d4-a716-446655440000.pdf`). Additionally, `Paths.get(uploadDir).resolve(safeFilename)` checks that the target path remains inside the configured upload directory.

### Q8: How is SQL Injection prevented in search and filter queries?
**Ans**: All dynamic search filters use Spring Data JPA `Specification` and Criteria API parameterized queries. Input values are bound via parameters, eliminating string concatenation.

### Q9: How is Sort Field Injection prevented?
**Ans**: The backend validates `sortBy` parameters against a strict whitelist of allowed entity property names (`createdAt`, `id`, `title`, `achievementDate`, `status`, `academicYear`). Unrecognized field names trigger an immediate `400 Bad Request`.

### Q10: How is pagination abuse controlled?
**Ans**: Page numbers must be non-negative (`page >= 0`), and page size (`size`) is capped at a maximum of **100** items per request.

### Q11: How does the HOD verification workflow restrict access by department?
**Ans**: HOD users have `ROLE_HOD` and belong to a specific department. `AchievementServiceImpl` checks that the achievement owner's `departmentId` matches the HOD's `departmentId`. Cross-department reviews return `403 Forbidden`.

### Q12: How are notifications generated?
**Ans**: Event triggers in `AchievementServiceImpl` call `NotificationService` upon achievement submission, approval, or rejection. Notifications are persisted in the `notifications` table with `recipient_id`.

### Q13: How does the audit logging system maintain immutability?
**Ans**: `AuditLogServiceImpl` provides only append-only `logAction(...)` and search methods. No `UPDATE` or `DELETE` methods or REST endpoints exist for audit records.

### Q14: How are passwords and JWT secrets protected from log exposure?
**Ans**: Audit descriptions and exception handlers sanitize sensitive parameters. Passwords, BCrypt hashes, and JWT signatures are strictly excluded from logs.

### Q15: Why is CSRF protection disabled in Spring Security for this REST API?
**Ans**: The REST API is completely stateless and uses JWT tokens transmitted via `Authorization` headers rather than ambient browser cookies. As a result, cross-site request forgery attacks are not applicable.

### Q16: How is CORS configured?
**Ans**: `SecurityConfig.java` reads allowed origins from environment variable `FRONTEND_ALLOWED_ORIGINS` and applies a strict whitelist (`allowedOrigins`). Wildcards (`*`) are disabled for authenticated APIs.

### Q17: What HTTP Security Headers are configured?
**Ans**: `X-Frame-Options: DENY` (prevents clickjacking) and `X-Content-Type-Options: nosniff` (prevents MIME-sniffing) are configured in Spring Security filters.

### Q18: How does the CSV export feature protect data privacy?
**Ans**: CSV export (`/api/achievements/export/csv`) respects the requesting user's authorization scope (Faculty gets own records, HOD gets department, Admin gets institution) and excludes sensitive credentials.

### Q19: What specialized metadata is stored for Journal Publications?
**Ans**: DOI, Impact Factor, Journal/Conference Name, Indexing (Scopus, SCI, Web of Science), Volume, Issue, Pages, and Publisher.

### Q20: What specialized metadata is stored for Patents?
**Ans**: Patent Number, Patent Status (Filed, Published, Granted), Filing Date, Grant Date, and Country.

### Q21: What specialized metadata is stored for Research Grants?
**Ans**: Project Title, Funding Agency, Grant Amount, Project Type (Government, Private, Institutional), Duration (Months), and Grant Status.

### Q22: What specialized metadata is stored for Workshops/FDPs?
**Ans**: Event Name, Event Type (Workshop, FDP, STTP, Seminar), Role (Attended, Organized, Resource Person), Organizing Body, Location, and Duration (Days).

### Q23: What specialized metadata is stored for Awards?
**Ans**: Award Name, Awarding Body/Organization, and Award Level (International, National, State, Institutional).

### Q24: How does the frontend handle token expiration?
**Ans**: `api.js` checks API responses for `401 Unauthorized`. If detected, it clears `sessionStorage` and redirects the user to `login.html` with a toast message.

### Q25: How is Mass Assignment prevented on user profile updates?
**Ans**: `UserProfileUpdateRequest` accepts only `fullName`, `designation`, and `phone`. Attempting to inject `role`, `departmentId`, `status`, or `passwordHash` in the request body is ignored.

### Q26: What error response structure is returned by `GlobalExceptionHandler`?
**Ans**: A standard `ErrorResponse` DTO containing `timestamp`, `status`, `error`, `message`, and `path`. Generic 500 errors return a sanitized message without exposing stack traces.

### Q27: How are database transactions managed?
**Ans**: Service methods use Spring's `@Transactional` annotation. Write operations execute within transaction boundaries, ensuring atomic execution (e.g. status update + notification + audit log).

### Q28: How is physical PDF file cleanup handled on achievement deletion?
**Ans**: When an achievement is deleted, `AchievementServiceImpl` extracts the filename from `proofDocumentUrl` and invokes `FileStorageService.deleteFile(filename)`, preventing orphan files on disk.

### Q29: How does the application prevent duplicate verifications under concurrent access?
**Ans**: `verifyAchievement` checks if `achievement.getStatus() != AchievementStatus.PENDING`. If already reviewed, it throws a `BadRequestException` ("Already reviewed"), preventing race conditions.

### Q30: What HikariCP settings are used in production?
**Ans**: `maximum-pool-size=10-20`, `minimum-idle=5`, `idle-timeout=300000` (5 mins), and `max-lifetime=1800000` (30 mins).

### Q31: How is the frontend designed to be responsive?
**Ans**: Modern CSS Flexbox and Grid layouts, CSS custom variables (`:root`), relative units (`rem`, `%`), and `@media` queries for viewports from 320px to 1920px.

### Q32: Why is Vanilla JS used instead of heavy frameworks?
**Ans**: Vanilla JS provides lightweight, fast page load times, zero third-party vulnerability overhead, and full control over DOM rendering and Fetch API calls.

### Q33: How does the notification slide-over panel work?
**Ans**: `common.js` fetches notifications via `GET /api/notifications` and renders a dynamic drawer panel with unread badge counters and mark-as-read controls.

### Q34: What unit testing framework is used in the backend?
**Ans**: JUnit 5 (JUnit Platform) and Mockito 5 for mocking dependencies (`@Mock`, `@InjectMocks`).

### Q35: How many test scenarios were verified in total?
**Ans**: **126 automated tests**, all passing with 0 failures, across 12 test classes:
security suites (51 — `HighlightSecurityTest` 21, `NaacReportSecurityTest` 12,
`ShareLinkSecurityTest` 9, `PermissionSecurityTest` 7, `PublicAccessSecurityTest` 2),
service suites (61 — `HighlightImageStorageTest` 21, `NaacReportAggregationTest` 24,
`AchievementServiceTest` 9, `NotificationServiceTest` 4, `AuditLogServiceTest` 3),
the controller suite (13 — `AchievementControllerTest`), and `PasswordTest` (1).
Run `mvn test` in `backend/` to reproduce the count. Manual end-to-end scenarios
walked by hand against a running server are recorded separately in `docs/testing.md`
and are not counted in the 126.

### Q36: How is the production executable built?
**Ans**: Running `mvn clean package` generates a standalone executable JAR at `backend/target/faculty-achievement-portal-0.0.1-SNAPSHOT.jar`.

### Q37: How is the backend deployed in production?
**Ans**: As a systemd background service (`niet-faculty.service`) managed by `systemctl` on a Linux server.

### Q38: How does Nginx reverse proxy work with Spring Boot?
**Ans**: Nginx terminates HTTPS SSL/TLS on port 443 and proxies API requests (`/api/`) to Spring Boot running on `127.0.0.1:8080`.

### Q39: What is the database backup strategy?
**Ans**: Daily `mysqldump` single-transaction compressed backups (`.sql.gz`) combined with daily tar backups of `/var/niet/uploads/achievements`.

### Q40: What are the main future scope enhancements?
**Ans**: SSO Integration (SAML/OAuth2), Mobile Application (React Native/Flutter), Cloud S3 Object Storage, AI-based OCR document parsing, and real-time WebSockets.
