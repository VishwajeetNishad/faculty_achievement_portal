# Faculty Achievement Portal — Secure File Upload & Storage Architecture

## 1. Storage Architecture
- **Runtime Upload Directory**: `uploads/achievements/` (Configured via `app.file-storage.upload-dir` in `application.properties`).
- **Web Root Isolation**: Stored files are kept strictly **outside** the public web root and static resource handlers. Files are never served as static assets.
- **Database Persistence**: The database column `proof_document_url` stores an application-relative API reference (`/api/achievements/{id}/proof?file=<uuid>.pdf`). Absolute Windows filesystem paths are NEVER stored in MySQL.
- **Git Safety**: The `uploads/` directory is listed in `.gitignore` to prevent physical PDF documents from being committed to source control.

---

## 2. File Validation Pipeline (`FileStorageServiceImpl.java`)
Every uploaded document undergoes 5 mandatory validation checks before being written to disk:

1. **File Extension**: Must end in `.pdf` (case-insensitive).
2. **MIME / Content-Type**: Must equal `application/pdf`.
3. **Magic Bytes Signature**: Reads the first 4 bytes of the input stream and verifies they match `%PDF` (`0x25, 0x50, 0x44, 0x46`). Fake PDFs containing executable code, scripts, or text pretending to be PDFs are rejected with `HTTP 400 Bad Request`.
4. **File Size Limit**: Configured to a maximum limit of **10 MB** (`10,485,760` bytes). Oversized files are rejected.
5. **Filename Sanitization & Path Traversal Protection**: Filenames are generated server-side using random UUIDs (`UUID.randomUUID().toString() + ".pdf"`). Client-supplied filenames (including `../../evil.pdf` attempts) are ignored. Target paths are resolved and verified using `.normalize().startsWith(fileStorageLocation)` to block path traversal vulnerabilities.

---

## 3. API Endpoints

### 3.1 Upload Proof Document
- **Endpoint**: `POST /api/achievements/{id}/proof`
- **Content-Type**: `multipart/form-data`
- **Parameter**: `file` (MultipartFile)
- **Authorization**: Required (Bearer JWT token). Owner of the achievement record (or Admin).
- **Response**: `200 OK` returning updated `AchievementResponse`.

### 3.2 View / Download Protected Proof Document
- **Endpoint**: `GET /api/achievements/{id}/proof`
- **Authorization**: Required (Bearer JWT token). Owner, Admin, or HOD of the matching department. Unauthenticated or unauthorized requests receive `HTTP 403 Forbidden` / `HTTP 401 Unauthorized`.
- **Response Headers**:
  - `Content-Type: application/pdf`
  - `Content-Disposition: inline; filename="achievement_proof_{id}.pdf"`

### 3.3 Delete Proof Document
- **Endpoint**: `DELETE /api/achievements/{id}/proof`
- **Authorization**: Required (Bearer JWT token). Owner of the achievement record.
- **Response**: `204 No Content`. Deletes physical PDF file from disk and clears `proof_document_url` in MySQL.

---

## 4. Security Considerations & Limitations
- **Virus / Malware Scanning**: Uploaded documents undergo MIME and magic byte signature verification to guarantee valid PDF format structure. Antivirus/malware scanning engine integration is not currently enabled for local development.
