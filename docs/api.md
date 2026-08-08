# Faculty Achievement Portal — REST API Specification

## Base URL
`http://localhost:8080/api`

---

## Auth & User Endpoints Summary

| Method | Endpoint | Access | Description | Expected Status |
| :--- | :--- | :--- | :--- | :--- |
| **POST** | `/api/auth/login` | Public | Authenticate user & get JWT token | `200 OK` / `401 Unauthorized` |
| **GET** | `/api/auth/me` | Authenticated | Get current user's profile | `200 OK` / `401 Unauthorized` |
| **POST** | `/api/auth/logout` | Authenticated | Invalidate current session context | `200 OK` |
| **PUT** | `/api/users/me` | Authenticated | Update faculty profile (fullName, designation, phone) | `200 OK` / `400 Bad Request` |
| **GET** | `/api/users` | `ROLE_ADMIN` | Get institutional faculty roster | `200 OK` / `403 Forbidden` |
| **GET** | `/api/users/{id}` | `ROLE_ADMIN` | Get faculty user detail by ID | `200 OK` / `404 Not Found` |
| **GET** | `/api/users/department` | `ROLE_HOD` | Get faculty in HOD's department | `200 OK` / `403 Forbidden` |
| **GET** | `/api/departments` | Authenticated | Get list of departments for filters | `200 OK` |

---

## Achievement Endpoints Summary

| Method | Endpoint | Access | Description | Expected Status |
| :--- | :--- | :--- | :--- | :--- |
| **POST** | `/api/achievements` | Authenticated | Create a new achievement | `201 Created` |
| **GET** | `/api/achievements/{id}` | Authenticated | Get achievement details by ID | `200 OK` / `404 Not Found` |
| **GET** | `/api/achievements/user/{userId}` | Authenticated | Get achievements for a user | `200 OK` |
| **GET** | `/api/achievements/status/{status}` | Authenticated | Filter achievements by status | `200 OK` / `400 Bad Request` |
| **GET** | `/api/achievements/department/{id}` | Authenticated | Filter achievements by department | `200 OK` |
| **PUT** | `/api/achievements/{id}` | Owner | Update an achievement | `200 OK` / `403 Forbidden` |
| **PATCH** | `/api/achievements/{id}/verification` | `ROLE_HOD` / `ROLE_ADMIN` | Approve or reject achievement | `200 OK` / `403 Forbidden` |
| **POST** | `/api/achievements/{id}/proof` | Owner | Upload PDF proof document | `200 OK` / `400 Bad Request` |
| **GET** | `/api/achievements/{id}/proof` | Authorized | Stream protected PDF document | `200 OK` / `403 Forbidden` |
| **DELETE** | `/api/achievements/{id}` | Owner | Delete an achievement | `204 No Content` / `403 Forbidden` |

---

## Profile Update Specification (`PUT /api/users/me`)
- **HTTP Method**: `PUT`
- **Path**: `/api/users/me`
- **Authorization**: Bearer JWT
- **Allowed Request Body Fields**:
```json
{
  "fullName": "Dr. Sharma",
  "designation": "Associate Professor",
  "phone": "9876543210"
}
```
- **Protected Fields**: `id`, `employeeId`, `email`, `role`, `department`, `status`, `passwordHash` (server ignores any attempts to supply these).
