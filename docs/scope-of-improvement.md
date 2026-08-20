# Scope of Improvement — Faculty Achievement Portal (NIET)

This document records areas where the project can be hardened, corrected, or
extended. Items are grouped by priority. Findings marked **[Security]** are
genuine risks, not cosmetic; **[Accuracy]** items concern claims that overstate
or misrepresent the current state; **[Enhancement]** items are future work.

---

## 1. High Priority — Security

### 1.1 Real database password committed to the repo `[Security]`
- **Where**: `README.md` (Environment Variables table) and
  `backend/src/main/resources/application.properties`.
- **Issue**: A real-looking credential (the developer's real MySQL password) is listed as the
  `DB_PASSWORD` default. Committing a working credential leaks it into git
  history permanently.
- **Fix**: Remove the value. Require `DB_PASSWORD` as an env var with **no**
  working default. Rotate the password if it has ever been live. Scrub it from
  git history if the repo is or will be shared.

### 1.2 JWT signing secret has a "safe dev default" fallback `[Security]`
- **Where**: `README.md` (`JWT_SECRET` → "Safe Dev Default Key") and the
  security/JWT provider config.
- **Issue**: A hardcoded fallback signing key means that if `JWT_SECRET` is
  unset in production, anyone who knows the default can forge valid tokens for
  any user/role.
- **Fix**: Fail application startup when `JWT_SECRET` is missing instead of
  falling back to a built-in key. Never ship a default secret.
- **Status (2026-08-20)**: ✅ Resolved — removed the hardcoded `DEFAULT_SECRET`
  from `JwtTokenProvider`; the app now validates `JWT_SECRET` at startup and
  fails fast if it is missing or under 256 bits. The previously-committed key
  remains in git history and must be rotated.

### 1.3 HTTPS is claimed but not configured `[Security]` `[Accuracy]`
- **Where**: Architecture diagram says "HTTPS / REST API (JWT Bearer)"; run
  instructions and CORS whitelist use plain `http://localhost`.
- **Issue**: Bearer tokens over plain HTTP are exposed to interception.
- **Fix**: Either document real TLS setup (reverse proxy / Spring SSL) or stop
  claiming HTTPS until it is actually configured.

---

## 2. Medium Priority — Accuracy & Consistency

### 2.1 "Test Coverage: 169/169" is mislabeled `[Accuracy]`
- **Issue**: 169/169 is a **pass rate over scenarios**, not code coverage
  (percentage of lines/branches exercised).
- **Fix**: Rename to "Test Scenarios Passed". Add real coverage measurement
  (e.g. JaCoCo) if a coverage metric is desired.

### 2.2 Documentation cites steps not yet committed `[Accuracy]`
- **Issue**: README references "Step 17–20 Integration Suites (101 passed)", but
  the latest commit is Step 17. If steps 18–20 are not committed, the counts are
  aspirational.
- **Fix**: Align the reported test counts with what is actually committed, or
  commit the remaining work before citing it.

### 2.3 Frontend `file://` origin conflicts with CORS whitelist `[Accuracy]`
- **Issue**: Opening `index.html` directly yields a `null` origin, which will
  not match the `http://localhost` CORS whitelist, so API calls fail.
- **Fix**: Make "serve `frontend/` over a static server" the default run
  instruction, not an alternative.

---

## 3. Lower Priority — Enhancements

### 3.1 Real test coverage tooling `[Enhancement]`
- Integrate JaCoCo and publish an actual line/branch coverage report.

### 3.2 Secrets management `[Enhancement]`
- Move DB and JWT secrets to a `.env` file (git-ignored) or a secrets manager;
  provide a committed `.env.example` with placeholder values only.

### 3.3 Automated CI `[Enhancement]`
- Add a CI pipeline (e.g. GitHub Actions) to run the Maven test suite on every
  push so the "169/169" claim is continuously verified.

### 3.4 API rate limiting / brute-force protection `[Enhancement]`
- Add rate limiting on the login endpoint to complement the audit logging of
  `LOGIN_FAILURE` events.

---

## Summary Table

| # | Item | Type | Priority |
| :-- | :-- | :-- | :-- |
| 1.1 | Committed DB password | Security | High |
| 1.2 | JWT default secret fallback | Security | High |
| 1.3 | HTTPS claimed, not configured | Security / Accuracy | High |
| 2.1 | "Test Coverage" mislabeled | Accuracy | Medium |
| 2.2 | Steps 18–20 cited but not committed | Accuracy | Medium |
| 2.3 | `file://` origin vs CORS | Accuracy | Medium |
| 3.1 | JaCoCo coverage tooling | Enhancement | Low |
| 3.2 | Secrets management (.env) | Enhancement | Low |
| 3.3 | Automated CI pipeline | Enhancement | Low |
| 3.4 | Login rate limiting | Enhancement | Low |
