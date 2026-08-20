# Master Test Case Matrix

**Institution**: Noida Institute of Engineering and Technology (NIET)  
**Total Verified Test Cases**: 18 Master Scenarios (Sub-verified across 169 system assertions)  

---

| Test ID | Module | Scenario | Expected Result | Actual Result | Status |
| :--- | :--- | :--- | :--- | :--- | :---: |
| **TC-001** | Authentication | Login with valid email & password | Issues valid 24h JWT token and user profile | JWT issued, user profile returned | **PASS** |
| **TC-002** | Authentication | Login with invalid password | Returns HTTP 401 Unauthorized / Bad Credentials | HTTP 401 Unauthorized | **PASS** |
| **TC-003** | Achievements | Faculty creates achievement | Achievement stored with status `PENDING` | Achievement stored, status PENDING | **PASS** |
| **TC-004** | Verification | HOD approves department achievement | Status updated to `APPROVED`, `verifiedBy` & timestamp recorded | Status APPROVED, audit & notification created | **PASS** |
| **TC-005** | Verification | HOD rejects achievement with comment | Status updated to `REJECTED`, feedback comment saved | Status REJECTED, comment saved | **PASS** |
| **TC-006** | File Storage | Upload valid PDF certificate | PDF magic bytes verified, UUID filename stored | HTTP 200 OK, proof URL updated | **PASS** |
| **TC-007** | File Storage | Upload fake PDF (non-PDF header) | Rejects upload with HTTP 400 Bad Request | HTTP 400 Bad Request | **PASS** |
| **TC-008** | Security | Faculty attempts self-verification | Rejects with HTTP 403 Forbidden | HTTP 403 Forbidden | **PASS** |
| **TC-009** | Security | HOD attempts cross-department review | Rejects with HTTP 403 Forbidden | HTTP 403 Forbidden | **PASS** |
| **TC-010** | Dashboard | Admin loads institutional dashboard | Displays department comparisons and total metrics | Correct JSON analytics returned | **PASS** |
| **TC-011** | Search | Multi-criterion backend search | Filters matching records using JPA specifications | Filtered PagedResponse returned | **PASS** |
| **TC-012** | Pagination | Page size capping check | Caps page size to maximum 100 | Page size capped at <=100 | **PASS** |
| **TC-013** | CSV Export | Export records to CSV file | Downloads UTF-8 BOM CSV respecting user role scope | CSV downloaded with header row | **PASS** |
| **TC-014** | Notifications | Event triggers on submission/review | Notification created for owner and HOD | Notification record generated | **PASS** |
| **TC-015** | Audit Logs | Log action on login & CRUD | Record added to `audit_logs` table | Audit log stored with actor details | **PASS** |
| **TC-016** | Profile | Faculty updates profile details | Persistence verified; tamper fields ignored | Profile updated, role untouched | **PASS** |
| **TC-017** | Security | API request with expired/tampered JWT | Rejects request with HTTP 401/403 | HTTP 401/403 Forbidden | **PASS** |
| **TC-018** | Security | CORS unauthorized origin preflight | Handled safely according to origin whitelist | Preflight handled, credentials protected | **PASS** |
