# Admin UI Design Prompt — NIET Faculty Achievement Portal

**How to use this file**

- **Whole thing at once** → paste into Stitch / v0 / Figma AI / another Claude session when you want the full admin console designed as one consistent set.
- **One page at a time** → paste *Part 1 + Part 2 + one page from Part 3*. This gives better results, because the tool has less to hold in its head and every page still shares the same shell and colours.

Everything in Part 1 is **already built** in `frontend/css/`. Do not let a design tool invent new colours, fonts or spacing — it must reuse these, or the generated screen will not match the seven pages that already exist.

---

## PART 1 — The design system (fixed, do not change)

```
Design a screen for an internal university admin console.
Visual style: clean, dense, professional SaaS. Light background, white cards,
one strong accent colour, a dark navy sidebar. No gradients, no glassmorphism,
no drop shadows heavier than a soft 1px-blur card lift, no rounded-full buttons,
no emoji, no stock photos, no illustrations.

COLOURS — use these exact values and nothing else:
  Accent (primary)        #E11D48   (rose / crimson — NIET brand)
  Accent hover            #BE123C
  Accent dark             #9F1239
  Accent tint background  rgba(225,29,72,0.08)
  Accent muted border     #FDA4AF
  Secondary / warning     #F59E0B

  Sidebar background      #0B1120   (near-black navy)
  Sidebar border          #1E293B
  Sidebar text (idle)     #94A3B8
  Sidebar active item     #E11D48 background, white text

  Page background         #F8FAFC
  Card surface            #FFFFFF
  Hover surface           #F1F5F9
  Border                  #E2E8F0
  Text primary            #0F172A
  Text secondary          #64748B
  Text tertiary           #94A3B8

  Success  #10B981    Warning #F59E0B    Danger #EF4444    Info #0284C7
  Status pill "Approved" = green tint bg + green text + green dot
  Status pill "Pending"  = amber tint bg + amber text + amber dot
  Status pill "Rejected" = red tint bg + red text + red dot

TYPE — system sans-serif stack. Sizes only from this scale:
  12px, 13px, 14px (body default), 15px, 16px, 18px, 21.6px, 28px
  Weights only: 400, 500, 600, 700, 800
  Body text is 14px. Table cells 13-14px. Page title 21.6px/700.

SHAPE & SPACE
  Corner radius: 4px (chips), 6px (inputs/buttons), 8px (small cards),
                 12px (cards), 16px (large panels), pill only for status dots.
  Spacing scale: 4, 8, 12, 16, 20, 24, 32px. Nothing in between.
  Card padding 20px. Table cell padding 12px 16px.
  Sidebar width 240px. Header height 64px. Content max-width 1240px.

ICONS
  Outline style only, 1.5-2px stroke, 24x24 viewBox, currentColor stroke,
  no fill. (Heroicons outline set.) Nav icons 20px, inline icons 15-18px.

BUTTONS
  Primary  = #E11D48 fill, white text, 6px radius, 600 weight
  Outline  = white fill, #E2E8F0 border, #0F172A text
  Ghost    = transparent, no border, #64748B text
  Danger   = #EF4444 fill, white text
  Small variant = 13px text, 6px 12px padding.
  Never stack more than 3 buttons in a row; wrap on narrow screens.
```

---

## PART 2 — The shell every admin page shares

```
LAYOUT
  Two columns. Fixed 240px dark sidebar on the left, flexible main area right.
  Main area = a 64px top header (page title left, actions right), then a
  scrolling content region with 24px padding and a 1240px max-width centred.

SIDEBAR (identical on every admin page)
  Top: circular NIET logo badge + two lines of text —
       "Admin Console" (600 weight, white) / "Control Center" (12px, #94A3B8).
  Small uppercase 11px section label: "ADMINISTRATION".
  Nav items in this exact order, each an outline icon + label:
    1. Admin Dashboard      (home icon)
    2. Verification Queue   (clipboard-check icon)
    3. Faculty Roster       (users icon)
    4. Departments          (office-building icon)
    5. User Permissions     (lock-closed icon)
    6. Audit Trail          (shield-check icon)
  The current page's item has an #E11D48 background and white text.
  Bottom, pinned: "Sign Out" with a logout icon, in light red #FCA5A5.
  On screens under 768px the sidebar becomes an off-canvas drawer opened by a
  hamburger button that sits to the left of the page title.

HEADER
  Left: hamburger (mobile only) + page title, 21.6px/700, #0F172A.
  Right: the page's primary action button, and a bell icon with a small red
  unread dot when there are unread notifications.

EVERY PAGE MUST ALSO SHOW
  - A loading state: a centred spinner with the text "Loading …" while data
    is being fetched. Never a blank screen.
  - An empty state: a centred outline icon, a bold one-line title, and one
    sentence of plain-English explanation of what to do next.
  - An error state: a red-tinted alert bar at the top of the content area with
    a bold lead-in sentence, then the reason and what to do about it.
  - A toast for the result of any action, top-right, auto-dismissing.
```

---

## PART 3 — The seven pages

Each page below lists **only fields the backend really returns**. Do not add a column or a stat card that is not in the field list — there is no API behind it.

### 3.1 Admin Dashboard

```
Purpose: the institution at a glance.

Row of 4 stat cards, each = rounded-square tinted icon tile on the left, then
a small grey uppercase label above a large 28px/800 number:
  Total Faculty · Active Faculty · Departments · Total Achievements

Row of 3 status cards using the status colours:
  Pending Review (amber) · Approved (green) · Rejected (red)

Then two panels side by side (stacking under 1024px):
  LEFT  "Department Comparison" — a horizontal bar per department showing its
        achievement count, department code as the label, sorted highest first.
  RIGHT "Category Distribution" — a simple donut or a bar list of achievement
        categories with counts.

Then a full-width panel "Achievements by Academic Year" — bar chart, one bar
per academic year like "2025-2026".

Header action: an "Export CSV" outline button.
```

### 3.2 Verification Queue

```
Purpose: approve or reject faculty submissions.

Filter card at the top: a search box, a Status dropdown (All / Pending /
Approved / Rejected), a Department dropdown, a Category dropdown, and a
date-range pair. Below the filters, a thin grey line of result counts.

Table, columns exactly:
  Achievement (bold title, with faculty name + employee ID as 12px grey
              sub-text underneath)
  Faculty Email
  Department (as a small grey outline chip showing the department code)
  Category
  Date
  Status (status pill with a dot)
  Actions

Actions cell: a "Review" primary-small button, plus a "Proof" outline-small
button shown only when a proof document exists.

Pagination bar under the table: "Showing 1–10 of 47" on the left,
Previous / page numbers / Next on the right.

Clicking Review opens a centred modal, max 640px wide:
  - Title bar with the achievement title and an X close button.
  - Body: a two-column definition list of every field, then an embedded
    PDF preview link if a proof exists, then a textarea labelled
    "Verification Feedback / Rejection Reason".
  - Footer: a red "Reject Achievement" button and a primary
    "Approve Achievement" button.
  - Show an amber inline note: "A decision can only be made once, and a
    rejection must include a reason."
```

### 3.3 Faculty Roster

```
Purpose: find any account and act on it.

Header action: a primary "Add User" button with a plus icon.

Filter card: Search (name, employee ID or email), Department dropdown,
Role dropdown (All / Faculty / Head of Department / Administrator),
Status dropdown (All / Active / Inactive / Suspended).
Under the filters, a grey counts line: "Total: 42   Active: 39".

Table, columns exactly:
  Employee ID & Name (bold full name, 12px grey employee ID underneath)
  Email
  Department (grey outline chip with the department code)
  Designation
  Role (a small chip: Faculty grey, Head of Department blue tint,
        Administrator rose tint)
  Status (status pill with a dot)
  Actions

Actions cell: up to three small buttons, in this order —
  "Edit" (outline), "Manage Permissions" (outline),
  and one of "Deactivate" (danger-outline) or "Activate" (outline).
When the signed-in admin has no right to an action, that button is simply
absent — never shown greyed out and never shown then refused.
Show an em-dash "—" when no action at all is available.
```

### 3.4 Add / Edit User

```
Purpose: one form that both creates a new account and edits an existing one.
Content max-width 940px, narrower than the other pages.

At the top, a ribbon of four small pill "step chips", each a numbered rose
circle plus a plain-English label:
  1 Who they are · 2 Their role · 3 Department & job title · 4 Sign-in details

Then a stack of section cards. Each card has a header strip on a very light
grey background containing: a 36px rounded-square rose-tinted icon tile, a
bold section title, and one line of plain-English explanation underneath.

  CARD 1 — "1. Who they are"
    Two-column grid of inputs: Full Name*, Employee ID*, Official Email*,
    Phone (optional). Each field has room for a small red error message
    underneath. Employee ID has the hint "Can also be used to sign in."

  CARD 2 — "2. Their role"
    Three selectable radio *cards* side by side (stacking under 640px), each
    with a radio dot, a bold role name, and two lines of description:
      Faculty — records their own achievements and submits them for review.
      Head of Department — verifies faculty in their own department only.
      Administrator — full control of the portal.
    The selected card gets a rose border, rose-tinted background and a soft
    rose focus ring. A role the signed-in admin may not assign is dimmed to
    60% opacity with a tiny red uppercase "NOT PERMITTED" tag beside its name.
    Choosing Administrator reveals an amber warning bar underneath.

  CARD 3 — "3. Department & job title"
    Department* dropdown (options read as "CSE — Computer Science &
    Engineering") and Designation* input with a suggestions datalist.

  CARD 4 — "4. Sign-in details"
    In edit mode only, first a checkbox card: "Set a new password for this
    user" / "Leave this unticked and their current password stays as it is."
    Then the Password field with an eye-icon show/hide button inside the right
    edge of the input, a 4-segment strength meter bar directly beneath it, and
    a 12px caption that changes with strength.
    Finish with a blue info bar: the password is never shown again and never
    appears in the audit trail.

  CARD 5 — "5. Account status" (edit mode only)
    A Status dropdown whose options explain themselves:
      "Active — can sign in", "Inactive — cannot sign in",
      "Suspended — cannot sign in".
    When the value changes, reveal an optional "Reason" text input with the
    hint that it is recorded in the audit trail.

At the very bottom, a sticky action bar that stays visible while scrolling:
a white rounded card with a soft shadow, holding a small grey note on the
left ("All fields marked * are required.") and, on the right, a "Cancel"
outline button plus a primary "Create Account" / "Save Changes" button.
Under 560px the buttons go full-width and stack.
```

### 3.5 Departments

```
Purpose: add, rename and remove departments.

Header action: a primary "Add Department" button.

A blue info bar first: a department decides which faculty a Head of Department
can see and verify, so renaming one changes who has authority over whom.

Two stat cards: Total Departments · Accounts Assigned.

A search box, then a table, columns exactly:
  Code (bold, monospace-ish uppercase chip)
  Name
  Description (truncated to one line, grey)
  Accounts (a count chip — grey when 0, rose-tinted when above 0)
  Actions → "Edit" outline-small, "Delete" danger-outline-small

Delete is disabled-looking-but-absent when the count is above 0; instead show
the count chip with the tooltip "Move these accounts to another department
first." If Delete is pressed and the server refuses, show a red alert saying
accounts still belong to this department.

Add / Edit opens a modal, max 520px:
  Code* (max 20 chars, letters/numbers/dash/underscore only, with that rule
  spelled out as a hint), Name*, Description (optional, max 255).
  Footer: Cancel outline + primary "Save Department".

Delete opens a small confirmation modal with the department name in bold and
a red "Delete Department" button.
```

### 3.6 User Permissions

```
Purpose: give one person extra abilities without changing their role.

A blue info bar: a role sets the default abilities; these switches add extra
ones on top, and they take effect immediately without the person signing in
again.

Card "1. Select a User" — a searchable dropdown of accounts showing
"Full Name — employee ID (Department)". Once chosen, show a compact summary
strip: avatar circle with initials, full name, email, role chip, status pill.

Card "2. Manage Permissions" — the 15 permissions as labelled toggle rows,
grouped under small uppercase group headings. Each row = the human label in
600 weight, one line of plain-English explanation beneath in 13px grey, and a
switch on the right.

  USER MANAGEMENT
    Create Faculty · Edit Faculty · Create HOD · Edit HOD ·
    Create Admin · Manage User Status
  ACHIEVEMENTS
    View All Achievements · Verify Achievement ·
    Edit Achievement · Delete Achievement
  REPORTS
    View Reports · Export Reports
  SYSTEM
    Manage Departments · View Audit Logs · Manage Permissions

Mark "Create Admin" and "Manage Permissions" with a small red "HIGH RISK"
tag, and put an amber warning bar above the System group.
When the selected user is an Administrator, show every switch on and locked,
with the note "An administrator already holds every permission."

Sticky footer bar: a grey note counting how many are on ("6 of 15 granted"),
a "Reset" ghost button, and a primary "Save Permissions" button.

Before any user is chosen, show the empty state instead of the second card.
```

### 3.7 Audit Trail

```
Purpose: an append-only record of who did what.

A blue info bar: entries can never be edited or deleted, and passwords, tokens
and secrets are never recorded.

Filter card: a date-range pair, an Action dropdown, an Entity Type dropdown
(USER, ACHIEVEMENT, DEPARTMENT, PERMISSION, AUTH), and a search box for the
actor's name or email.

Table, columns exactly:
  When (relative time in bold like "2 hours ago", exact timestamp as 12px
        grey sub-text)
  Who (bold actor name, grey email underneath; show "System / Guest" when
       there is no signed-in actor)
  Action (a colour-coded chip: green for created/approved, amber for
          updated/changed, red for deleted/rejected/revoked, grey for
          viewed/exported, blue for login/logout)
  Entity (entity type chip plus "#id")
  Description (the sentence the server supplies, wrapped to two lines max)
  IP Address (13px, grey)

Pagination bar underneath. Newest entry first, always.
Header action: an "Export CSV" outline button.
```

---

## PART 4 — Hard rules the design must respect

```
1. NO INVENTED DATA. Every number, name, department and achievement shown in
   the design must be obviously placeholder ("Faculty Member A", "0", "—") or
   labelled as sample. Do not invent real-sounding faculty names, publication
   titles, or statistics — this portal shows live institutional records.

2. RESPONSIVE, ZERO HORIZONTAL OVERFLOW at every one of these widths:
   320, 375, 480, 768, 1024, 1280, 1366, 1440, 1920.
   Tables scroll horizontally inside their own container — the page itself
   never scrolls sideways. Filter rows wrap. Buttons go full-width under 560px.

3. PERMISSION-AWARE, NOT PERMISSION-ENFORCING. An action the signed-in admin
   cannot perform is hidden, not disabled-and-then-refused. The design must
   never imply the browser decides who is allowed — the server always
   re-checks. Do not design any screen that stores or displays a password,
   a password hash, a JWT, or a share token.

4. PLAIN ENGLISH EVERYWHERE. Every section, every risky switch and every
   error explains itself in one short sentence a non-technical head of
   department would understand. Never show a bare code like
   "MANAGE_USER_STATUS" without its human label beside it.

5. ACCESSIBILITY. Text contrast at least 4.5:1 against its background — note
   that #94A3B8 and #E2E8F0 are for dark surfaces only and must never be used
   for text on a white card. Every icon-only button needs an aria-label. Every
   input needs a real <label>. Focus rings stay visible.

6. REUSE, DON'T REINVENT. The generated markup must use these existing class
   names rather than new ones:
   card, card-header, card-title, card-body, btn, btn-primary, btn-outline,
   btn-ghost, btn-danger, btn-sm, badge, badge-pending, badge-approved,
   badge-rejected, badge-status-dot, alert, alert-info, alert-warning,
   alert-danger, alert-success, stat-card, stats-grid, stat-icon-wrapper,
   stat-label, stat-value, data-table, table-responsive, table-title-cell,
   table-subtext, action-btn-group, pagination-bar, pagination-info,
   pagination-controls, form-group, form-label, form-control, form-hint,
   form-error, form-grid, required-field, password-toggle-btn,
   modal-backdrop, modal-container, modal-header, modal-title,
   modal-close-btn, modal-body, modal-footer, empty-state, empty-state-title,
   empty-state-text, loading-spinner, spinner, avatar-circle,
   user-profile-widget, w-full.
   Any genuinely new component goes in a page-scoped <style> block, not into
   the shared stylesheets.

7. OUTPUT FORMAT: plain semantic HTML + a small page-scoped <style> block.
   No React, no Tailwind, no build step, no CDN font, no external image.
   Icons inline as <svg>.
```

---

## Quick copy — one-paragraph version

If a tool only accepts a short prompt, use this:

> Design a page for an internal university faculty-achievement admin console. Two-column layout: a fixed 240px near-black navy sidebar (#0B1120) with outline icons and a rose (#E11D48) active item, and a light main area (#F8FAFC page, #FFFFFF cards, #E2E8F0 borders, #0F172A text, #64748B secondary text). Rose #E11D48 is the only accent. 14px system-sans body, 21.6px/700 page title, 12px radius on cards and 6px on inputs and buttons, 20px card padding, soft 1px shadows only. Clean dense professional SaaS — no gradients, no glass, no emoji, no illustrations. Include a loading spinner state, an empty state with an icon plus one explanatory sentence, and a red alert bar error state. Status pills are amber Pending, green Approved, red Rejected, each with a leading dot. Plain semantic HTML with one page-scoped style block, inline outline SVG icons, and no horizontal overflow from 320px to 1920px. Use placeholder data only — never invent real-sounding faculty names or statistics.
