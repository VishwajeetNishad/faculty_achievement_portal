/**
 * HOD Portal — shared foundation, loaded on every /pages/hod/* page.
 *
 * Responsibilities
 *  1. Client-side role guard (UX only — real authorization is enforced server-side by
 *     Spring Security @PreAuthorize + department scoping; the frontend never decides access).
 *  2. Fetch GET /auth/me ONCE, fill the top-bar user + sidebar department brand, and expose
 *     the user as `window.HOD.ready` (a Promise) so per-page controllers can await the same
 *     object instead of calling /auth/me again.
 *  3. Own the mobile sidebar drawer using DISTINCT ids (#hodSidebar / #hodMenuToggle + an
 *     injected .hod-drawer-backdrop) so common.js's #appSidebar drawer never conflicts.
 *  4. Highlight the active nav link by pathname.
 *  5. Shared render helpers: hodInitials / hodFormatDate / hodFormatDateTime /
 *     hodStatusBadge / hodCategoryChip.
 *  6. Shared review dialog: openHodReviewModal(id, onDone) — details, proof, approve /
 *     reject-with-feedback, with success/rejection confirmation states.
 *
 * Load order on HOD pages: config.js → api.js → common.js → hod-common.js → hod-<page>.js
 * (escapeHtml, showToast, openModal, closeModal, refreshUnreadBadge come from common.js.)
 */

window.HOD = window.HOD || {};
let _hodReadyResolve;
/** Resolves to the HOD user object, or null if the guard failed (not HOD / load error). */
window.HOD.ready = new Promise((resolve) => { _hodReadyResolve = resolve; });

document.addEventListener('DOMContentLoaded', () => {
  hodInitDrawer();
  hodInitActiveNav();
  hodBootstrap();
});

/* ── Role guard + identity ─────────────────────────────────────────────────── */
async function hodBootstrap() {
  if (!sessionStorage.getItem('accessToken')) {
    window.location.href = '../login.html';
    return;
  }

  const res = await ApiClient.get('/auth/me');
  if (!res.success) {
    // 401 → api.js already redirects to login. Any other failure = show a shell error.
    if (res.status !== 401) hodShowShellError(res.message);
    _hodReadyResolve(null);
    return;
  }

  const me = res.data;
  window.HOD.me = me;
  // The fine-grained permissions this HOD was granted by an Admin (Track A).
  // /auth/me returns them fresh on every load, so a grant/revoke shows up on the next visit.
  window.HOD.permissions = Array.isArray(me.permissions) ? me.permissions : [];
  hodFillIdentity(me);

  const role = String(me.role || '').toUpperCase();
  const isHod = role.includes('HOD');
  if (!isHod) {
    hodRenderAccessDenied();
    _hodReadyResolve(null);
    return;
  }

  // Reveal any permission-gated sidebar links now that we know what this HOD holds.
  hodApplyPermissionNav();

  _hodReadyResolve(me);
}

function hodFillIdentity(me) {
  const set = (id, text) => { const el = document.getElementById(id); if (el) el.textContent = text; };
  set('hodTopUserName', me.fullName || 'Head of Department');
  set('hodTopUserRole', `HOD, ${me.departmentCode || 'Dept'}`);
  set('hodTopAvatar', hodInitials(me.fullName));
  set('hodBrandTitle', `${me.departmentCode || 'Department'} Department`);
  set('hodBrandSub', me.departmentName || 'Faculty Achievement Portal');
}

function hodShowShellError(msg) {
  const content = document.querySelector('.hod-content');
  if (!content) return;
  content.innerHTML = `
    <div class="hod-container">
      <div class="hod-access-denied">
        <span class="material-symbols-outlined" style="color:var(--hod-pending);">cloud_off</span>
        <h2 class="hod-state-title">Couldn’t load your profile</h2>
        <p class="hod-state-text">${escapeHtml(msg || 'Unable to reach the server. Please check your connection and try again.')}</p>
        <div style="margin-top:16px;"><button class="hod-btn hod-btn-primary" onclick="window.location.reload()">Retry</button></div>
      </div>
    </div>`;
}

function hodRenderAccessDenied() {
  const content = document.querySelector('.hod-content');
  if (!content) return;
  content.innerHTML = `
    <div class="hod-container">
      <div class="hod-access-denied">
        <span class="material-symbols-outlined">lock</span>
        <h2 class="hod-state-title">HOD access required</h2>
        <p class="hod-state-text">This area is restricted to Heads of Department. Your account does not have HOD privileges.</p>
        <div style="margin-top:16px;"><a class="hod-btn hod-btn-primary" href="../dashboard.html">Go to my dashboard</a></div>
      </div>
    </div>`;
}

/* ── Mobile drawer (distinct ids so common.js ignores it) ──────────────────── */
function hodInitDrawer() {
  const toggle = document.getElementById('hodMenuToggle');
  const sidebar = document.getElementById('hodSidebar');
  if (!toggle || !sidebar) return;

  let backdrop = document.querySelector('.hod-drawer-backdrop');
  if (!backdrop) {
    backdrop = document.createElement('div');
    backdrop.className = 'hod-drawer-backdrop';
    document.body.appendChild(backdrop);
  }

  const close = () => { sidebar.classList.remove('active'); backdrop.classList.remove('active'); };
  const open = () => { sidebar.classList.add('active'); backdrop.classList.add('active'); };

  toggle.addEventListener('click', (e) => {
    e.stopPropagation();
    sidebar.classList.contains('active') ? close() : open();
  });
  backdrop.addEventListener('click', close);
  sidebar.querySelectorAll('.hod-nav-link').forEach((link) => {
    link.addEventListener('click', () => { if (window.innerWidth <= 1024) close(); });
  });
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape') close(); });
  window.addEventListener('resize', () => { if (window.innerWidth > 1024) close(); });
}

/* ── Active nav by pathname ────────────────────────────────────────────────── */
function hodInitActiveNav() {
  const path = window.location.pathname;
  const file = path.substring(path.lastIndexOf('/') + 1) || 'dashboard.html';
  const links = document.querySelectorAll('.hod-nav-link');
  let matched = false;

  links.forEach((link) => {
    const href = link.getAttribute('href');
    if (!href || href.startsWith('#')) return;
    const linkFile = href.substring(href.lastIndexOf('/') + 1);
    if (linkFile === file) { link.classList.add('active'); matched = true; }
  });

  // faculty-profile.html has no dedicated nav entry → keep "Faculty Directory" active.
  if (!matched && file === 'faculty-profile.html') {
    links.forEach((link) => {
      if ((link.getAttribute('href') || '').endsWith('faculty.html')) link.classList.add('active');
    });
  }
}

/* ── Permission-aware UI (UX only — the server still enforces every check) ──── */
/**
 * True when the signed-in HOD holds the given fine-grained permission code
 * (from GET /auth/me). This ONLY decides whether to show a menu item or button;
 * the backend re-checks the permission on every request, so hiding a link is never
 * the security boundary — it just avoids showing a door that would return 403 anyway.
 */
function hodCan(code) {
  return Array.isArray(window.HOD.permissions) && window.HOD.permissions.includes(code);
}

/**
 * Reveal sidebar links shipped hidden (`data-perm="CODE"` + inline display:none)
 * when this HOD actually holds the matching permission. Links the HOD does not
 * hold simply stay hidden. Setting display back to '' reverts to the stylesheet's flex.
 */
function hodApplyPermissionNav() {
  document.querySelectorAll('.hod-nav-link[data-perm]').forEach((link) => {
    if (hodCan(link.getAttribute('data-perm'))) link.style.display = '';
  });
}

/* ── Render helpers (shared by every controller) ───────────────────────────── */
function hodInitials(name) {
  if (!name) return 'U';
  const parts = String(name).trim().split(/\s+/).filter(Boolean);
  if (!parts.length) return 'U';
  const honorifics = new Set(['dr', 'dr.', 'mr', 'mr.', 'mrs', 'mrs.', 'ms', 'ms.', 'prof', 'prof.']);
  const useful = parts.filter((p) => !honorifics.has(p.toLowerCase()));
  const src = useful.length ? useful : parts;
  const first = src[0][0] || '';
  const last = src.length > 1 ? src[src.length - 1][0] : '';
  return (first + last).toUpperCase();
}

function hodFormatDate(dateStr) {
  if (!dateStr) return '—';
  const d = new Date(dateStr);
  if (isNaN(d.getTime())) return '—';
  return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
}

function hodFormatDateTime(dateStr) {
  if (!dateStr) return '—';
  const d = new Date(dateStr);
  if (isNaN(d.getTime())) return '—';
  return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' })
    + ', ' + d.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
}

function hodStatusBadge(status) {
  const s = String(status || '').toUpperCase();
  if (s === 'APPROVED') return `<span class="hod-badge hod-badge-approved"><span class="material-symbols-outlined">check_circle</span>Approved</span>`;
  if (s === 'REJECTED') return `<span class="hod-badge hod-badge-rejected"><span class="material-symbols-outlined">cancel</span>Rejected</span>`;
  return `<span class="hod-badge hod-badge-pending"><span class="material-symbols-outlined">schedule</span>Pending</span>`;
}

function hodCategoryChip(categoryName, categoryCode) {
  const code = String(categoryCode || '').toUpperCase();
  const map = {
    PUBLICATION:    { cls: 'hod-chip-tertiary',  icon: 'menu_book' },
    PATENT:         { cls: 'hod-chip-secondary', icon: 'workspace_premium' },
    RESEARCH_GRANT: { cls: 'hod-chip-approved',  icon: 'payments' },
    WORKSHOP_FDP:   { cls: 'hod-chip-muted',     icon: 'school' },
    AWARD:          { cls: 'hod-chip-secondary', icon: 'emoji_events' }
  };
  const m = map[code] || { cls: 'hod-chip-muted', icon: 'category' };
  const label = categoryName || code || 'Achievement';
  return `<span class="hod-chip ${m.cls}"><span class="material-symbols-outlined">${m.icon}</span>${escapeHtml(label)}</span>`;
}

/* ── Shared review modal ───────────────────────────────────────────────────── */
function hodEnsureReviewModal() {
  if (document.getElementById('hodReviewModal')) return;
  const html = `
    <div class="modal-backdrop" id="hodReviewModal" aria-hidden="true" role="dialog" aria-labelledby="hodReviewTitle">
      <div class="modal-container" style="max-width: 640px;">
        <div class="modal-header">
          <h3 class="modal-title" id="hodReviewTitle">Achievement Review</h3>
          <button class="modal-close-btn" onclick="closeModal('hodReviewModal')" aria-label="Close">&times;</button>
        </div>
        <div class="modal-body" id="hodReviewBody" style="padding: 1.25rem 1.5rem;"></div>
      </div>
    </div>`;
  document.body.insertAdjacentHTML('beforeend', html);
  // common.js binds backdrop-click only to nodes present at DOMContentLoaded; this one is
  // injected later, so bind its backdrop-click here. (ESC is handled globally by common.js.)
  const modal = document.getElementById('hodReviewModal');
  modal.addEventListener('click', (e) => { if (e.target === modal) closeModal('hodReviewModal'); });
}

/**
 * Open the review dialog for an achievement.
 * @param {number|string} achievementId
 * @param {Function} [onDone] called with the updated AchievementResponse after a successful decision
 */
async function openHodReviewModal(achievementId, onDone) {
  hodEnsureReviewModal();
  const body = document.getElementById('hodReviewBody');
  document.getElementById('hodReviewTitle').textContent = 'Achievement Review';
  body.innerHTML = `<div style="padding:44px 0;"><div class="hod-spinner"></div><p style="text-align:center;margin-top:14px;color:var(--hod-on-surface-variant);">Loading achievement…</p></div>`;
  openModal('hodReviewModal');

  const res = await ApiClient.get(`/achievements/${achievementId}`);
  if (!res.success) {
    body.innerHTML = `
      <div class="hod-state">
        <span class="material-symbols-outlined">error</span>
        <div class="hod-state-title">Unable to load</div>
        <p class="hod-state-text">${escapeHtml(res.message || 'Failed to load this achievement record.')}</p>
      </div>`;
    return;
  }
  hodRenderReviewDetail(res.data, onDone);
}

function hodRenderReviewDetail(a, onDone) {
  const body = document.getElementById('hodReviewBody');
  const isPending = String(a.status || '').toUpperCase() === 'PENDING';

  const proofBlock = a.proofDocumentUrl
    ? `<button class="hod-btn hod-btn-outline hod-btn-sm" id="hodProofBtn"><span class="material-symbols-outlined">picture_as_pdf</span>View proof document</button>`
    : `<span class="hod-muted" style="font-size:13px;display:inline-flex;align-items:center;gap:6px;"><span class="material-symbols-outlined" style="font-size:18px;">block</span>No proof document attached</span>`;

  let actionBlock;
  if (isPending) {
    actionBlock = `
      <div class="hod-field hod-mt-24">
        <label class="hod-field-label" for="hodReviewComment">Review feedback <span style="text-transform:none;font-weight:400;">(required to reject)</span></label>
        <textarea class="hod-textarea" id="hodReviewComment" placeholder="Add a note for the faculty member…"></textarea>
      </div>
      <div class="hod-flex hod-gap-12" style="justify-content:flex-end;margin-top:16px;flex-wrap:wrap;">
        <button class="hod-btn hod-btn-danger" id="hodRejectBtn"><span class="material-symbols-outlined">close</span>Reject with Feedback</button>
        <button class="hod-btn hod-btn-primary" id="hodApproveBtn"><span class="material-symbols-outlined">check</span>Approve</button>
      </div>`;
  } else {
    const quote = a.verificationComment
      ? `<p class="hod-feedback-quote">${escapeHtml(a.verificationComment)}</p>`
      : `<p class="hod-muted" style="margin:0;">No feedback was recorded.</p>`;
    actionBlock = `
      <div class="hod-detail-desc hod-mt-24">
        <div class="hod-info-label" style="margin-bottom:6px;">Review decision</div>
        <div style="display:flex;flex-wrap:wrap;gap:16px 32px;margin-bottom:10px;">
          <div><div class="hod-info-label">Reviewed by</div><div class="hod-info-value">${escapeHtml(a.verifiedByName || '—')}</div></div>
          <div><div class="hod-info-label">Reviewed on</div><div class="hod-info-value">${hodFormatDateTime(a.verifiedAt)}</div></div>
        </div>
        ${quote}
      </div>
      <div class="hod-flex" style="justify-content:flex-end;margin-top:16px;">
        <button class="hod-btn hod-btn-ghost" onclick="closeModal('hodReviewModal')">Close</button>
      </div>`;
  }

  body.innerHTML = `
    <div class="hod-modal-detail">
      <div>
        <h4 class="hod-headline-md" style="margin-bottom:6px;">${escapeHtml(a.title || 'Achievement')}</h4>
        <div class="hod-flex hod-gap-8" style="flex-wrap:wrap;">${hodCategoryChip(a.categoryName, a.categoryCode)} ${hodStatusBadge(a.status)}</div>
      </div>
      <div class="hod-detail-grid">
        <div class="hod-detail-item"><div class="hod-info-label">Faculty</div><div class="hod-info-value">${escapeHtml(a.facultyName || '—')}</div></div>
        <div class="hod-detail-item"><div class="hod-info-label">Employee ID</div><div class="hod-info-value">${escapeHtml(a.employeeId || '—')}</div></div>
        <div class="hod-detail-item"><div class="hod-info-label">Academic Year</div><div class="hod-info-value">${escapeHtml(a.academicYear || '—')}</div></div>
        <div class="hod-detail-item"><div class="hod-info-label">Achievement Date</div><div class="hod-info-value">${hodFormatDate(a.achievementDate)}</div></div>
        <div class="hod-detail-item"><div class="hod-info-label">Submitted</div><div class="hod-info-value">${hodFormatDateTime(a.createdAt)}</div></div>
        <div class="hod-detail-item"><div class="hod-info-label">Department</div><div class="hod-info-value">${escapeHtml(a.departmentName || a.departmentCode || '—')}</div></div>
      </div>
      <div>
        <div class="hod-info-label" style="margin-bottom:6px;">Description</div>
        <div class="hod-detail-desc">${a.description ? escapeHtml(a.description) : '<span class="hod-muted">No description provided.</span>'}</div>
      </div>
      <div>${proofBlock}</div>
      ${actionBlock}
    </div>`;

  const proofBtn = document.getElementById('hodProofBtn');
  if (proofBtn) proofBtn.addEventListener('click', (e) => hodViewProof(a.id, e.currentTarget));
  const approveBtn = document.getElementById('hodApproveBtn');
  if (approveBtn) approveBtn.addEventListener('click', () => hodSubmitVerification(a, 'APPROVED', onDone));
  const rejectBtn = document.getElementById('hodRejectBtn');
  if (rejectBtn) rejectBtn.addEventListener('click', () => hodSubmitVerification(a, 'REJECTED', onDone));
}

async function hodSubmitVerification(a, decision, onDone) {
  const commentEl = document.getElementById('hodReviewComment');
  const comment = commentEl ? commentEl.value.trim() : '';

  if (decision === 'REJECTED' && !comment) {
    showToast('Please add feedback explaining why this achievement is rejected.', 'error');
    if (commentEl) commentEl.focus();
    return;
  }

  const approveBtn = document.getElementById('hodApproveBtn');
  const rejectBtn = document.getElementById('hodRejectBtn');
  [approveBtn, rejectBtn].forEach((b) => { if (b) b.disabled = true; });

  const res = await ApiClient.patch(`/achievements/${a.id}/verification`, {
    status: decision,
    verificationComment: comment || null
  });

  if (!res.success) {
    showToast(res.message || 'Verification failed. Please try again.', 'error');
    [approveBtn, rejectBtn].forEach((b) => { if (b) b.disabled = false; });
    return;
  }

  hodRenderReviewConfirm(decision, res.data || a);
  if (typeof onDone === 'function') onDone(res.data || a);
  if (typeof refreshUnreadBadge === 'function') refreshUnreadBadge();
}

function hodRenderReviewConfirm(decision, a) {
  const approved = String(decision).toUpperCase() === 'APPROVED';
  const body = document.getElementById('hodReviewBody');
  const title = a && a.title ? a.title : 'The achievement';
  body.innerHTML = `
    <div class="hod-confirm">
      <div class="hod-confirm-ico ${approved ? 'ok' : 'warn'}">
        <span class="material-symbols-outlined">${approved ? 'check_circle' : 'cancel'}</span>
      </div>
      <div class="hod-confirm-title">${approved ? 'Achievement Approved' : 'Achievement Rejected'}</div>
      <p class="hod-confirm-msg">${escapeHtml(
        approved
          ? `“${title}” has been approved and the faculty member has been notified.`
          : `“${title}” has been rejected. Your feedback has been shared with the faculty member.`
      )}</p>
      <button class="hod-btn hod-btn-primary" id="hodConfirmDone">Done</button>
    </div>`;
  const done = document.getElementById('hodConfirmDone');
  if (done) done.addEventListener('click', () => closeModal('hodReviewModal'));
}

async function hodViewProof(achievementId, btn) {
  const original = btn ? btn.innerHTML : '';
  if (btn) { btn.disabled = true; btn.innerHTML = `<span class="material-symbols-outlined">hourglass_top</span>Opening…`; }

  const res = await ApiClient.downloadBlob(`/achievements/${achievementId}/proof`);

  if (btn) { btn.disabled = false; btn.innerHTML = original; }
  if (!res.success) {
    showToast(res.message || 'Unable to open the proof document.', 'error');
    return;
  }
  window.open(res.objectUrl, '_blank');
}

/* ── Shared list utilities (verification queue + department achievements) ──── */

// There is no /api/categories endpoint — the seeded category set is the source of truth.
const HOD_CATEGORY_OPTIONS = [
  { code: 'PUBLICATION',    label: 'Research Publication' },
  { code: 'PATENT',         label: 'Patent / Intellectual Property' },
  { code: 'RESEARCH_GRANT', label: 'Research & Consultancy Grant' },
  { code: 'WORKSHOP_FDP',   label: 'Workshop / FDP / Certification' },
  { code: 'AWARD',          label: 'Award & Recognition' }
];

function hodDebounce(fn, ms = 350) {
  let t;
  return function (...args) { clearTimeout(t); t = setTimeout(() => fn.apply(this, args), ms); };
}

/** Build a query string, dropping empty/null params so filters stay optional. */
function hodBuildQuery(params) {
  return Object.entries(params)
    .filter(([, v]) => v !== null && v !== undefined && v !== '')
    .map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`)
    .join('&');
}

/** Category options come from the seeded set (there is no /api/categories endpoint). */
function hodPopulateCategoryFilter(selectId) {
  const sel = document.getElementById(selectId);
  if (!sel) return;
  HOD_CATEGORY_OPTIONS.forEach((c) => {
    const opt = document.createElement('option');
    opt.value = c.code;
    opt.textContent = c.label;
    sel.appendChild(opt);
  });
}

/** Academic-year options are derived from the real department distribution. */
async function hodPopulateYearFilter(selectId) {
  const sel = document.getElementById(selectId);
  if (!sel) return;
  const res = await ApiClient.get('/dashboard/hod');
  if (!res.success || !res.data || !res.data.academicYearDistribution) return;
  Object.keys(res.data.academicYearDistribution)
    .sort((a, b) => b.localeCompare(a))
    .forEach((year) => {
      const opt = document.createElement('option');
      opt.value = year;
      opt.textContent = year;
      sel.appendChild(opt);
    });
}

/**
 * Render a compact pager into `container` from a PagedResponse
 * ({page, size, totalElements, totalPages, first, last}) and wire page clicks.
 */
function hodRenderPagination(container, paged, onPage) {
  if (!container) return;
  const total = paged.totalElements || 0;
  if (total === 0) { container.innerHTML = ''; return; }

  const size = paged.size || 10;
  const page = paged.page || 0; // zero-based
  const start = page * size + 1;
  const end = Math.min(start + size - 1, total);

  const parts = [];
  parts.push(`<button class="hod-page-btn" data-page="${page - 1}" ${paged.first ? 'disabled' : ''} aria-label="Previous page"><span class="material-symbols-outlined" style="font-size:18px;">chevron_left</span></button>`);
  hodPageWindow(page, paged.totalPages || 0).forEach((p) => {
    if (p === '…') parts.push(`<span style="padding:0 4px;color:var(--hod-on-surface-variant);">…</span>`);
    else parts.push(`<button class="hod-page-btn ${p === page ? 'active' : ''}" data-page="${p}">${p + 1}</button>`);
  });
  parts.push(`<button class="hod-page-btn" data-page="${page + 1}" ${paged.last ? 'disabled' : ''} aria-label="Next page"><span class="material-symbols-outlined" style="font-size:18px;">chevron_right</span></button>`);

  container.innerHTML = `
    <div class="hod-pagination-info">Showing <strong>${start}–${end}</strong> of <strong>${total}</strong></div>
    <div class="hod-pagination-controls">${parts.join('')}</div>`;

  container.querySelectorAll('.hod-page-btn[data-page]').forEach((b) => {
    b.addEventListener('click', () => {
      if (b.disabled) return;
      const p = parseInt(b.getAttribute('data-page'), 10);
      if (!isNaN(p) && p >= 0 && p < (paged.totalPages || 0)) onPage(p);
    });
  });
}

function hodPageWindow(page, totalPages) {
  const pages = [];
  if (totalPages <= 7) { for (let i = 0; i < totalPages; i++) pages.push(i); return pages; }
  let s = Math.max(1, page - 1);
  let e = Math.min(totalPages - 2, page + 1);
  if (page <= 2) { s = 1; e = 3; }
  if (page >= totalPages - 3) { s = totalPages - 4; e = totalPages - 2; }
  pages.push(0);
  if (s > 1) pages.push('…');
  for (let i = s; i <= e; i++) pages.push(i);
  if (e < totalPages - 2) pages.push('…');
  pages.push(totalPages - 1);
  return pages;
}
