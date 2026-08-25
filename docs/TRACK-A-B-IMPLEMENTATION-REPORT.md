# Implementation Report — Fine-Grained Permissions (Track A) & Public / Unlisted Research (Track B)

**Project:** NIET Faculty Achievement Portal
**Prepared:** 2026-08-25
**Branch:** `feat/hod-portal-and-user-permissions`
**Status:** ✅ Complete — all 6 planned steps finished, committed, and pushed.

---

## 1. What this report covers

This is a plain-language walkthrough of one complete piece of work added to the portal: two related
features, built as an **enhancement on top of the existing system** (not a rebuild).

- **Track A — Fine-Grained Permissions.** An administrator can give a *single user* a few extra
  abilities (for example, letting one Head of Department also create faculty accounts) **without
  changing that person's role**.
- **Track B — Public & Unlisted Research.** Anyone (students, other colleges) can browse *approved,
  publicly-marked* achievements **with no login**, and a faculty member can share an *unpublished*
  achievement through a **temporary, secret link** that also needs no login.

> The separate **HOD portal UI** is a different piece of work tracked on its own; it is not part of
> this report.

---

## 2. The ground rules I followed

These were fixed from the start and never broken:

| Rule | Why it matters |
| :--- | :--- |
| Do **not** rebuild the system; keep JWT login and the `ROLE_FACULTY` / `ROLE_HOD` / `ROLE_ADMIN` roles | The existing app already works — this is an *add-on*, so nothing old should break |
| Keep `spring.jpa.hibernate.ddl-auto=validate` | The app is *not allowed* to auto-change the database; it only checks that the code matches the schema |
| Every database change is an explicit **Flyway migration** | Changes are reviewable, ordered, and repeatable — never silent |
| New permissions only **add** to what a role can do — a role's power is never removed | An admin who could do something yesterday can still do it today |
| The person performing an action always comes from the **login token** (`SecurityContextHolder`), never from the request body | Stops anyone from pretending to be someone else |
| No passwords, hashes, JWTs, or secrets in code, responses, or logs | Basic security hygiene |
| Runs **locally on Windows** for now (no deployment yet) | Matches how the project is being evaluated |

### Four design decisions locked with you up front

1. All **19 existing achievements default to `PRIVATE`** — nothing became public by surprise.
2. **`ROLE_ADMIN` holds all 15 permissions automatically** (computed in code, no database rows) — an
   admin can never be locked out by a half-filled permission table.
3. Public profile links use a **readable slug** like `/faculty/rajesh-kumar-cse`.
4. `EDIT_ACHIEVEMENT` / `DELETE_ACHIEVEMENT` exist in the list but are **not wired** for non-owners —
   achievements stay strictly editable by their owner only.

---

## 3. The workflow, step by step

The work followed a 6-step build order. Each step was verified before moving on, then committed.

### Step 1 — Permission foundation + management API  ·  commit `25ee03e` (2026-08-23)

**What:** Added the permission system's base.
- New Flyway migration `V3__permissions.sql` — two tables (`permissions`, `user_permissions`) and 15
  seeded permission rows.
- New entities `Permission` and `UserPermission`, their repositories, and `security/Permissions.java`
  holding the 15 permission codes as constants.
- Taught `CustomUserDetailsService` to load a user's permissions **from the database on every
  request**, and to treat an admin as automatically holding all 15.
- New `PermissionController` with the endpoints to view and set a user's permissions.
- `/api/auth/me` now also returns the current user's permissions (so the UI can show/hide buttons).
- New admin screen `user-permissions.html` — a checkbox grid to grant/revoke.

**Why on every request:** the login token (JWT) is never changed. Because permissions are re-read
from the database each time, **granting or revoking takes effect on the user's very next click** — no
logout/login needed.

**Bonus fix:** discovered that a deactivated user could still log in. Tied "enabled" to
`status == ACTIVE`, so deactivating an account now locks it out immediately.

### Step 2 — User & department management + the public site's shell  ·  commit `b0844a8` (2026-08-24)

**What:**
- Built the endpoints the new permissions actually guard: create user (`POST /api/users`), edit user
  (`PUT /api/users/{id}`), change status (`PATCH /api/users/{id}/status`), and department
  create/edit/delete.
- New admin screens `add-user.html` and `departments.html`.
- Built the **public no-login site as UI only** first (home page, faculty directory, profile, gallery)
  so the look could be reviewed before the data was wired in.

### Step 3 — Track B backend  ·  commit `f63e269` (2026-08-24)

**What:** The whole engine behind the public site and share links.
- New Flyway migration `V4__visibility_and_share_links.sql` — added `visibility` and `keywords` to
  achievements, `public_slug` to users, and the new `share_links` table.
- New `PublicController` + `PublicDiscoveryService` serving `/api/public/**` (directory, profile by
  slug, gallery, and `share/{token}` + its document).
- New `ShareLink` entity/repository, `ShareService`, and `ShareController` for owners to create,
  view, extend, and revoke links.
- `PublicSlugBackfill` — fills a readable slug for every existing user, once, on startup.
- A `GoneException` that returns **HTTP 410** for expired/revoked links.

**The one rule that protects everything:** an item is public **only when
`status = APPROVED` AND `visibility = PUBLIC`**, and that rule is baked into every query on the server.
A visitor cannot send a parameter that widens what they see.

### Step 4 — Track B frontend  ·  commit `68d00c2` (2026-08-25)

**What:** The pages people actually click.
- A "Sharing & Access" section on the add-achievement form (Public / Unlisted / Private + duration +
  "include proof document").
- A "My Research & Shared Resources" page showing each link's state (Active / Expired / Revoked) with
  Copy-Link and Revoke.
- The public share page that renders the shared item, or an honest "expired"/"revoked" message.
- Deleted the temporary `public-sample-data.js` placeholder and wired the public pages to the **real**
  API, with honest error states when a request fails.

### Step 5 — Security tests  ·  commit `5d580cb` (2026-08-25)

**What:** Three automated test files that prove the security rules, using plain Mockito/reflection
(no database or server needed to run them). See §5.

### Step 6 — Documentation  ·  commit `5d580cb` (2026-08-25)

**What:** Brought the three reference docs up to date in plain language — the API list, the security
model, and the database design. See §7 for links.

---

## 4. What changed in the database

All applied through Flyway migrations `V3` and `V4`. The full schema now has **15 tables**.

**New tables**

- **`permissions`** — the fixed list of 15 abilities (code + plain-language description).
- **`user_permissions`** — which extra permissions a user was granted (admins have *no* rows here;
  they get everything automatically).
- **`share_links`** — one row per share link: the secret token, when it expires (`NULL` = never),
  whether the proof PDF is included, and whether it's revoked.

**New columns**

- `achievements.visibility` — `PUBLIC` / `UNLISTED` / `PRIVATE`, defaults to `PRIVATE`.
- `achievements.keywords` — words to help public search.
- `users.public_slug` — the readable id used in a public profile URL.

The 15 permission codes:

`CREATE_FACULTY`, `EDIT_FACULTY`, `CREATE_HOD`, `EDIT_HOD`, `CREATE_ADMIN`, `MANAGE_USER_STATUS`,
`VIEW_ALL_ACHIEVEMENTS`, `VERIFY_ACHIEVEMENT`, `EDIT_ACHIEVEMENT`, `DELETE_ACHIEVEMENT`,
`VIEW_REPORTS`, `EXPORT_REPORTS`, `MANAGE_DEPARTMENTS`, `VIEW_AUDIT_LOGS`, `MANAGE_PERMISSIONS`.

---

## 5. The security guards (the heart of the work)

### Track A — who may hand out authority

Before any permission change is saved, the server refuses it when:

| # | Rule | Result |
| :--- | :--- | :--- |
| 1 | You try to change **your own** permissions | `403` |
| 2 | The target is an **administrator** (they already have everything) | `400` |
| 3 | An **unknown** permission code is sent (never silently ignored) | `400` |
| 4 | A **non-admin** tries to grant `MANAGE_PERMISSIONS` or `CREATE_ADMIN` | `403` |
| 5 | You try to grant a permission you **don't hold yourself** | `403` |

Plus the **last-administrator guard**: any action that would leave the system with **zero active
admins** (deactivating or demoting the last one) is refused with `409`. The institution can never lock
every admin out.

### Track B — making a link safe to hand to a stranger

- **Unguessable token:** 32 random bytes from `SecureRandom`, written as a 43-character URL-safe
  string. It is **never** built from an id, employee number, or the time — so it can't be guessed.
- **Server is the only judge:** expiry and revocation are re-checked on **every** request
  (`404` unknown, `410` revoked, `410` expired). The countdown in the browser is just decoration.
- **Owner-only:** only the achievement's owner can create/extend/revoke its link; anyone else gets
  `403`.
- **Proof PDF is opt-in:** the document is reachable only if the owner ticked the box; otherwise `403`.
- **No leaks:** dedicated public response objects simply don't contain sensitive fields (email, phone,
  reviewer comments, file paths). The token is never echoed in an error or written to the audit log,
  and every share response is sent `Cache-Control: no-store`.

---

## 6. Testing

Three new test files, all pure Mockito/reflection (they need **no** running MySQL or server):

| File | Tests | Proves |
| :--- | :---: | :--- |
| `PermissionSecurityTest` | 7 | The five grant guards + the last-active-admin guard |
| `ShareLinkSecurityTest` | 9 | Owner-only, 404/410 lifecycle, proof gating, **1,000 unique high-entropy tokens**, token never audited |
| `PublicAccessSecurityTest` | 2 | Public/shared response objects declare **no** sensitive field |

**Result:** the full backend suite ran **48 tests, 0 failures**. (18 of those are the new tests above;
the other 30 are the pre-existing tests, all still passing — proof nothing old broke.)

---

## 7. Where the detailed docs live

Everything above is documented in depth, in plain language, in:

- [docs/api.md](api.md) — every endpoint, its access rule, and expected status codes.
- [docs/security.md](security.md) — the permission model and the public/share-link security.
- [docs/database-design.md](database-design.md) — the tables, columns, and diagram.

---

## 8. Deliberately NOT done (with reasons)

These were left as-is on purpose — flagged so nothing is a surprise later:

- **`EDIT_ACHIEVEMENT` / `DELETE_ACHIEVEMENT` for non-owners** — seeded but not wired; achievements
  stay owner-only (locked decision 4).
- **No `EXPORT_REPORTS` guard on CSV export** — that endpoint already scopes rows to the current user;
  adding a guard would only *restrict* existing faculty export, so it was left alone.
- **Share token stored as plain text** — the "Copy Link" feature must show the token again later, so it
  can't be stored as a one-way hash. Trade-off: anyone with direct database read access could use a
  live link. Accepted because the feature needs it.
- **"Permanent" links** — a permanent link is a standing secret credential for unpublished work. It's
  offered because it was requested; the UI warns about it and one click revokes it.

### Pre-existing issues found but out of this scope

- **Database timezone:** the app connects with `serverTimezone=UTC`, so saved times read ~5.5h behind
  MySQL's clock. Cosmetic locally; worth aligning before real deployment.
- **Faculty category dropdown:** `frontend/js/achievements.js` calls a `/categories` endpoint that
  doesn't exist and falls back to a hardcoded `'1'`. Pre-existing, unrelated to this work.
- **Revoked share rows accumulate:** re-sharing revokes the old row instead of reusing it, so revoked
  rows pile up (harmless — hidden by default in the UI).

---

## 9. How to run and check it locally

1. Start MySQL, then start the backend. On startup, **Flyway applies `V1`→`V4` in order** and
   `ddl-auto=validate` confirms the code matches the database — if anything mismatched, the app would
   refuse to start.
2. All 19 existing achievements are `PRIVATE`; every user has a unique `public_slug`.
3. Run the backend test suite — expect **48 passing, 0 failing**.
4. Open the public site with your browser's storage empty (truly logged out) to confirm the no-login
   pages work, and that pending/rejected/private items never appear.

> **Demo data note:** achievements 12/17/19/33 are set to `PUBLIC`, and `faculty2@niet.ac.in`'s
> password is `Faculty@12345`, so the public pages have real content to show during evaluation.

---

## 10. Commit trail

| Commit | Date | Step |
| :--- | :--- | :--- |
| `25ee03e` | 2026-08-23 | Step 1 — fine-grained permissions foundation + management API |
| `b0844a8` | 2026-08-24 | Step 2 — user/department management + public site shell |
| `f63e269` | 2026-08-24 | Step 3 — Track B backend (public browsing + share links) |
| `68d00c2` | 2026-08-25 | Step 4 — Track B frontend |
| `5d580cb` | 2026-08-25 | Steps 5 & 6 — security tests + documentation |

All pushed to `feat/hod-portal-and-user-permissions`. Nothing was pushed to `main`.
