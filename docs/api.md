# Faculty Achievement Portal — REST API Specification

## Base URL
`http://localhost:8080/api/achievements`

---

## Endpoints Summary

| Method | Endpoint | Description | Expected Status |
| :--- | :--- | :--- | :--- |
| **POST** | `/api/achievements` | Create a new achievement | `201 Created` |
| **GET** | `/api/achievements/{id}` | Get achievement details by ID | `200 OK` / `404 Not Found` |
| **GET** | `/api/achievements/user/{userId}` | Get all achievements for a faculty user | `200 OK` |
| **GET** | `/api/achievements/status/{status}` | Filter achievements by status (`PENDING`, `APPROVED`, `REJECTED`) | `200 OK` / `400 Bad Request` |
| **GET** | `/api/achievements/department/{departmentId}` | Filter achievements by department ID | `200 OK` |
| **PUT** | `/api/achievements/{id}` | Update an existing achievement | `200 OK` / `404 Not Found` |
| **DELETE** | `/api/achievements/{id}` | Delete an achievement | `204 No Content` / `404 Not Found` |

---

## Detailed Endpoint Specifications

### 1. Create Achievement
- **HTTP Method**: `POST`
- **Path**: `/api/achievements?userId=1`
- **Query Parameters**:
  - `userId` (required, `Long`): ID of the faculty user submitting the achievement.
- **Request Headers**: `Content-Type: application/json`
- **Request Body**:
```json
{
  "categoryId": 1,
  "title": "Deep Learning in Healthcare Systems",
  "description": "Published research paper in IEEE Transactions",
  "achievementDate": "2025-05-10",
  "academicYear": "2025-2026",
  "proofDocumentUrl": "https://example.com/certificates/dl-paper.pdf"
}
```
- **Response Body (`201 Created`)**:
```json
{
  "id": 1,
  "userId": 1,
  "facultyName": "System Administrator",
  "facultyEmail": "admin@faculty.edu",
  "employeeId": "EMP001",
  "departmentCode": "CSE",
  "departmentName": "Computer Science & Engineering",
  "categoryId": 1,
  "categoryCode": "PUBLICATION",
  "categoryName": "Research Publication",
  "title": "Deep Learning in Healthcare Systems",
  "description": "Published research paper in IEEE Transactions",
  "achievementDate": "2025-05-10",
  "academicYear": "2025-2026",
  "status": "PENDING",
  "verificationComment": null,
  "verifiedByUserId": null,
  "verifiedByName": null,
  "verifiedAt": null,
  "proofDocumentUrl": "https://example.com/certificates/dl-paper.pdf",
  "createdAt": "2026-08-08T15:10:00",
  "updatedAt": "2026-08-08T15:10:00"
}
```

---

### 2. Get Achievement by ID
- **HTTP Method**: `GET`
- **Path**: `/api/achievements/{id}`
- **Response Body (`200 OK`)**: Standard `AchievementResponse` object.
- **Error Response (`404 Not Found`)**:
```json
{
  "timestamp": "2026-08-08T15:10:00",
  "status": 404,
  "error": "Not Found",
  "message": "Achievement not found with id: 999",
  "path": "/api/achievements/999"
}
```

---

### 3. Get Achievements by User
- **HTTP Method**: `GET`
- **Path**: `/api/achievements/user/{userId}`
- **Response Body (`200 OK`)**: Array of `AchievementResponse` objects.

---

### 4. Get Achievements by Status
- **HTTP Method**: `GET`
- **Path**: `/api/achievements/status/{status}` (e.g. `/api/achievements/status/PENDING`)
- **Response Body (`200 OK`)**: Array of `AchievementResponse` objects matching status.
- **Error Response (`400 Bad Request`)** if status string is invalid:
```json
{
  "timestamp": "2026-08-08T15:10:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid achievement status: INVALID. Allowed values: PENDING, APPROVED, REJECTED.",
  "path": "/api/achievements/status/INVALID"
}
```

---

### 5. Get Achievements by Department
- **HTTP Method**: `GET`
- **Path**: `/api/achievements/department/{departmentId}`
- **Response Body (`200 OK`)**: Array of `AchievementResponse` objects.

---

### 6. Update Achievement
- **HTTP Method**: `PUT`
- **Path**: `/api/achievements/{id}?userId=1`
- **Query Parameters**:
  - `userId` (required, `Long`): Owner user ID.
- **Request Body**: `AchievementUpdateRequest` payload.
- **Response Body (`200 OK`)**: Updated `AchievementResponse` object.

---

### 7. Delete Achievement
- **HTTP Method**: `DELETE`
- **Path**: `/api/achievements/{id}?userId=1`
- **Response (`204 No Content`)**: Empty body.

---

## Temporary Authentication Note
Currently, authentication (Spring Security / JWT) is not active. The `userId` query parameter is used temporarily for development-level request scoping and will be replaced by Spring Security's `@AuthenticationPrincipal` context in future steps.
