/**
 * My Research & Shared Resources — the owner's view of every share link.
 *
 * Backed by four real endpoints. Nothing on this page is invented:
 *   GET    /api/achievements/shared        → every link I created, newest first
 *   PATCH  /api/achievements/{id}/share    → change the expiry / proof setting
 *   DELETE /api/achievements/{id}/share    → revoke, effective immediately
 *   GET    /api/auth/me                    → the sidebar identity widget
 *
 * One thing worth being clear about: the ACTIVE / EXPIRED / REVOKED state shown
 * here is the state the *server* reported when the list was fetched. The server
 * re-checks expiry on every single anonymous request, so this table is a report,
 * not a gatekeeper. If a link expires while this page sits open, the countdown
 * below reaches zero and we relabel the row — but the link had already stopped
 * working at the server the moment its time ran out, with or without this page.
 */

let ALL_SHARE_LINKS = [];   // exactly what the server returned, untouched
let COUNTDOWN_TIMER = null; // one interval for the whole table
let MANAGING_ACHIEVEMENT_ID = null;
let REVOKING_ACHIEVEMENT_ID = null;

document.addEventListener('DOMContentLoaded', () => {
  if (!sessionStorage.getItem('accessToken')) {
    window.location.href = 'login.html';
    return;
  }

  loadIdentityWidget();
  wireControls();
  loadShareLinks();
});

/* ────────────────────────────────────────────────────────────────────────
   Identity — same shape as dashboard.js so the sidebar matches every other
   faculty page. Real values only; if the call fails the widget keeps its
   placeholder rather than showing somebody else's name.
   ──────────────────────────────────────────────────────────────────────── */
async function loadIdentityWidget() {
  const res = await ApiClient.get('/auth/me');
  if (!res.success || !res.data) return;

  const user = res.data;
  const names = (user.fullName || 'User').split(' ').filter(Boolean);
  const initials = names.length >= 2
    ? (names[0][0] + names[names.length - 1][0]).toUpperCase()
    : (user.fullName || 'US').substring(0, 2).toUpperCase();

  const headerAvatar = document.getElementById('headerAvatar');
  const sidebarAvatar = document.getElementById('sidebarAvatar');
  const sidebarName = document.getElementById('sidebarUserName');
  const sidebarRole = document.getElementById('sidebarUserRole');

  if (headerAvatar) headerAvatar.textContent = initials;
  if (sidebarAvatar) sidebarAvatar.textContent = initials;
  if (sidebarName) sidebarName.textContent = user.fullName || '';
  if (sidebarRole && (user.designation || user.departmentCode)) {
    sidebarRole.textContent = `${user.designation || 'Faculty'} • ${user.departmentCode || ''}`;
  }
}

function wireControls() {
  const search = document.getElementById('sharedSearchInput');
  const stateFilter = document.getElementById('sharedStateFilter');
  if (search) search.addEventListener('input', renderTable);
  if (stateFilter) stateFilter.addEventListener('change', renderTable);

  const historyToggle = document.getElementById('showHistoryToggle');
  if (historyToggle) historyToggle.addEventListener('change', renderTable);

  // The custom date box only makes sense when "Custom" is chosen.
  const durationSel = document.getElementById('manageShareDuration');
  const customWrap = document.getElementById('manageCustomWrap');
  if (durationSel && customWrap) {
    durationSel.addEventListener('change', () => {
      customWrap.style.display = durationSel.value === 'CUSTOM' ? 'block' : 'none';
    });
  }

  const saveBtn = document.getElementById('saveShareChangesBtn');
  if (saveBtn) saveBtn.addEventListener('click', saveShareChanges);

  const revokeBtn = document.getElementById('confirmRevokeBtn');
  if (revokeBtn) revokeBtn.addEventListener('click', confirmRevoke);
}

/* ────────────────────────────────────────────────────────────────────────
   Load
   ──────────────────────────────────────────────────────────────────────── */
async function loadShareLinks() {
  const tbody = document.getElementById('sharedTableBody');
  if (tbody) {
    tbody.innerHTML = `<tr><td colspan="7"><div class="loading-spinner"><div class="spinner"></div><span>Loading your shared research…</span></div></td></tr>`;
  }

  const res = await ApiClient.get('/achievements/shared');

  if (!res.success) {
    if (tbody) {
      tbody.innerHTML = `<tr><td colspan="7"><div class="empty-state">
        <span class="empty-state-title">Could not load your share links</span>
        <p class="empty-state-text">${escapeHtml(res.message || 'Please check your connection and try again.')}</p>
      </div></td></tr>`;
    }
    return;
  }

  ALL_SHARE_LINKS = Array.isArray(res.data) ? res.data : [];
  updateCounters();
  renderTable();
  startCountdown();
}

function updateCounters() {
  const current = currentLinks();
  const counts = { ACTIVE: 0, EXPIRED: 0, REVOKED: 0 };
  let views = 0;

  // The stat cards describe the link that is in force for each achievement
  // right now — "2 links are live" is the useful fact, not "127 links have
  // existed at some point".
  current.forEach(link => {
    const state = effectiveState(link);
    if (counts[state] !== undefined) counts[state]++;
  });

  // Views are cumulative and every one of them really happened, so this total
  // counts every link ever issued, superseded ones included.
  ALL_SHARE_LINKS.forEach(link => { views += Number(link.accessCount || 0); });

  setText('statActive', counts.ACTIVE);
  setText('statExpired', counts.EXPIRED);
  setText('statRevoked', counts.REVOKED);
  setText('statViews', views);

  const olderCount = ALL_SHARE_LINKS.length - current.length;
  setText('sharedTotalCount',
    `${current.length} achievement${current.length === 1 ? '' : 's'} shared` +
    (olderCount > 0 ? ` · ${olderCount} earlier link${olderCount === 1 ? '' : 's'} on record` : ''));

  // Only offer the history toggle when there is actually history to show.
  const wrap = document.getElementById('historyToggleWrap');
  if (wrap) {
    wrap.style.display = olderCount > 0 ? 'flex' : 'none';
    setText('historyToggleLabel',
      `Show ${olderCount} earlier revoked link${olderCount === 1 ? '' : 's'} ` +
      `(replaced when the achievement was re-shared)`);
  }
}

/**
 * The link that is in force for each achievement.
 *
 * The server keeps every link ever issued, because creating a new share link
 * revokes the previous one rather than editing it — so the history is real and
 * worth keeping. But it means one achievement re-shared ten times has ten rows,
 * nine of them dead. Showing only the newest per achievement makes the table
 * answer the question people actually have: "how is this shared right now?"
 *
 * Newest wins, by createdAt. Ties fall back to the higher position in the
 * server's list, which is already sorted newest-first.
 */
function currentLinks() {
  const newestByAchievement = new Map();

  ALL_SHARE_LINKS.forEach(link => {
    const key = link.achievementId;
    const existing = newestByAchievement.get(key);
    if (!existing) {
      newestByAchievement.set(key, link);
      return;
    }
    const a = new Date(link.createdAt).getTime();
    const b = new Date(existing.createdAt).getTime();
    if (!isNaN(a) && (isNaN(b) || a > b)) newestByAchievement.set(key, link);
  });

  return Array.from(newestByAchievement.values());
}

function setText(id, value) {
  const el = document.getElementById(id);
  if (el) el.textContent = value;
}

/**
 * The state to display.
 *
 * The server sends `state`, and that is the authority. The only thing added
 * here is the case where a link was ACTIVE when we fetched the list and its
 * expiry has since passed while the page stayed open — relabelling it keeps the
 * screen honest. A revoked link is never re-labelled: revoked is final.
 */
function effectiveState(link) {
  const serverState = String(link.state || '').toUpperCase();
  if (serverState === 'REVOKED') return 'REVOKED';
  if (serverState === 'EXPIRED') return 'EXPIRED';
  if (!link.permanent && link.expiresAt && new Date(link.expiresAt).getTime() <= Date.now()) {
    return 'EXPIRED';
  }
  return 'ACTIVE';
}

/* ────────────────────────────────────────────────────────────────────────
   Render
   ──────────────────────────────────────────────────────────────────────── */
function renderTable() {
  const tbody = document.getElementById('sharedTableBody');
  if (!tbody) return;

  const term = (document.getElementById('sharedSearchInput')?.value || '').trim().toLowerCase();
  const wantedState = document.getElementById('sharedStateFilter')?.value || '';
  const showHistory = !!document.getElementById('showHistoryToggle')?.checked;

  // Default to one row per achievement. Ticking the box widens it to every
  // link ever issued, superseded ones included.
  const source = showHistory ? ALL_SHARE_LINKS : currentLinks();

  const rows = source.filter(link => {
    const matchesTerm = !term || String(link.achievementTitle || '').toLowerCase().includes(term);
    const matchesState = !wantedState || effectiveState(link) === wantedState;
    return matchesTerm && matchesState;
  });

  if (ALL_SHARE_LINKS.length === 0) {
    tbody.innerHTML = `<tr><td colspan="7"><div class="empty-state">
      <span class="empty-state-title">You have not shared anything yet</span>
      <p class="empty-state-text">
        When you add an achievement, choose <strong>Unlisted share link</strong> to create a
        temporary link you can send to a reviewer or a collaborator. It will appear here.
      </p>
      <a href="add-achievement.html" class="btn btn-primary btn-sm" style="margin-top: 1rem;">+ Add Achievement</a>
    </div></td></tr>`;
    return;
  }

  if (rows.length === 0) {
    // Say which control is hiding things, so the fix is obvious.
    const hint = (!showHistory && wantedState === 'REVOKED')
      ? 'Revoked links from earlier re-shares are hidden — tick “Show earlier revoked links” above to see them.'
      : 'Try a different search word, or set the state filter back to “All link states”.';
    tbody.innerHTML = `<tr><td colspan="7"><div class="empty-state">
      <span class="empty-state-title">No links match your filters</span>
      <p class="empty-state-text">${hint}</p>
    </div></td></tr>`;
    return;
  }

  tbody.innerHTML = rows.map(renderRow).join('');
  wireRowActions();
}

function renderRow(link) {
  const state = effectiveState(link);
  const isActive = state === 'ACTIVE';
  const id = link.achievementId;

  return `
    <tr data-achievement-id="${id}">
      <td data-label="Research">
        <div style="font-weight: 600; color: var(--text-primary);">${escapeHtml(link.achievementTitle || 'Untitled')}</div>
        <div style="font-size: 0.72rem; color: var(--text-tertiary); margin-top: 0.15rem;">
          ${escapeHtml(link.categoryName || link.categoryCode || '')}
          ${link.includeProofDocument ? ' · <span style="color:#B45309;">proof document included</span>' : ''}
        </div>
        ${isActive ? `<div class="share-url-cell" style="margin-top: 0.3rem;">${escapeHtml(link.shareUrl || '')}</div>` : ''}
      </td>
      <td data-label="Visibility">${visibilityBadge(link.visibility)}</td>
      <td data-label="Link state">${stateBadge(state)}</td>
      <td data-label="Expires">${expiryCell(link, state)}</td>
      <td data-label="Created">${escapeHtml(formatDate(link.createdAt))}</td>
      <td data-label="Views">${Number(link.accessCount || 0)}</td>
      <td data-label="Actions" style="text-align: right;">
        <div style="display: inline-flex; gap: 0.35rem; flex-wrap: wrap; justify-content: flex-end;">
          ${isActive ? `<button class="btn btn-outline btn-sm js-open-link" data-url="${escapeHtml(link.shareUrl || '')}">View</button>` : ''}
          ${isActive ? `<button class="btn btn-outline btn-sm js-copy-link" data-url="${escapeHtml(link.shareUrl || '')}">Copy Link</button>` : ''}
          ${state !== 'REVOKED' ? `<button class="btn btn-outline btn-sm js-manage-link" data-id="${id}">Manage</button>` : ''}
          ${state !== 'REVOKED' ? `<button class="btn btn-danger btn-sm js-revoke-link" data-id="${id}">Revoke</button>` : ''}
          ${state === 'REVOKED' ? `<span style="font-size: 0.72rem; color: var(--text-tertiary);">No actions</span>` : ''}
        </div>
      </td>
    </tr>`;
}

function visibilityBadge(visibility) {
  const v = String(visibility || 'PRIVATE').toUpperCase();
  const label = v === 'PUBLIC' ? 'Public' : v === 'UNLISTED' ? 'Unlisted' : 'Private';
  return `<span class="badge-visibility ${v.toLowerCase()}">${label}</span>`;
}

function stateBadge(state) {
  const label = state.charAt(0) + state.slice(1).toLowerCase();
  return `<span class="badge-share-state ${state.toLowerCase()}">${label}</span>`;
}

/**
 * The expiry cell, which is the one part of the table that changes by itself.
 * `data-expires-at` lets the countdown tick update it without re-rendering the
 * whole table and losing the person's scroll position.
 */
function expiryCell(link, state) {
  if (link.permanent) {
    return `<span style="font-size: 0.78rem; color: var(--text-secondary);">Never expires</span>`;
  }
  if (state === 'REVOKED') {
    return `<span class="expiry-gone" style="font-size: 0.78rem;">Revoked ${escapeHtml(formatDate(link.revokedAt))}</span>`;
  }
  if (!link.expiresAt) {
    return `<span class="expiry-gone" style="font-size: 0.78rem;">—</span>`;
  }
  return `
    <div style="font-size: 0.78rem;">
      <div>${escapeHtml(formatDateTime(link.expiresAt))}</div>
      <div class="js-countdown" data-expires-at="${escapeHtml(link.expiresAt)}" style="font-size: 0.7rem; margin-top: 0.1rem;">
        ${escapeHtml(remainingText(link.expiresAt))}
      </div>
    </div>`;
}

/* ────────────────────────────────────────────────────────────────────────
   Countdown — decoration only. See the note at the top of this file.
   ──────────────────────────────────────────────────────────────────────── */
function startCountdown() {
  if (COUNTDOWN_TIMER) clearInterval(COUNTDOWN_TIMER);
  COUNTDOWN_TIMER = setInterval(tickCountdown, 1000);
  tickCountdown();
}

function tickCountdown() {
  let anyJustExpired = false;

  document.querySelectorAll('.js-countdown').forEach(el => {
    const expiresAt = el.getAttribute('data-expires-at');
    const msLeft = new Date(expiresAt).getTime() - Date.now();
    el.textContent = remainingText(expiresAt);
    el.classList.toggle('expiry-soon', msLeft > 0 && msLeft < 60 * 60 * 1000);
    el.classList.toggle('expiry-gone', msLeft <= 0);
    if (msLeft <= 0) anyJustExpired = true;
  });

  // A row crossed its expiry while the page was open. Re-render once so its
  // badge and its buttons match what the server would now say.
  if (anyJustExpired) {
    updateCounters();
    renderTable();
  }
}

function remainingText(expiresAt) {
  const msLeft = new Date(expiresAt).getTime() - Date.now();
  if (isNaN(msLeft)) return '';
  if (msLeft <= 0) return 'Expired';

  const totalSeconds = Math.floor(msLeft / 1000);
  const days = Math.floor(totalSeconds / 86400);
  const hours = Math.floor((totalSeconds % 86400) / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;

  if (days > 0) return `${days}d ${hours}h left`;
  if (hours > 0) return `${hours}h ${minutes}m left`;
  if (minutes > 0) return `${minutes}m ${seconds}s left`;
  return `${seconds}s left`;
}

/* ────────────────────────────────────────────────────────────────────────
   Row actions
   ──────────────────────────────────────────────────────────────────────── */
function wireRowActions() {
  document.querySelectorAll('.js-open-link').forEach(btn => {
    btn.addEventListener('click', () => {
      const url = btn.getAttribute('data-url');
      if (url) window.open(url, '_blank', 'noopener');
    });
  });

  document.querySelectorAll('.js-copy-link').forEach(btn => {
    btn.addEventListener('click', () => copyToClipboard(btn.getAttribute('data-url')));
  });

  document.querySelectorAll('.js-manage-link').forEach(btn => {
    btn.addEventListener('click', () => openManageModal(Number(btn.getAttribute('data-id'))));
  });

  document.querySelectorAll('.js-revoke-link').forEach(btn => {
    btn.addEventListener('click', () => openRevokeModal(Number(btn.getAttribute('data-id'))));
  });
}

/**
 * Copy, with a fallback.
 *
 * navigator.clipboard needs a secure context. That is fine on localhost, but
 * the moment this is demonstrated over a LAN address it is plain http and the
 * modern API is simply absent — hence the old execCommand path, so "Copy Link"
 * never silently does nothing.
 */
async function copyToClipboard(url) {
  if (!url) return;
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(url);
    } else {
      const temp = document.createElement('textarea');
      temp.value = url;
      temp.style.position = 'fixed';
      temp.style.left = '-9999px';
      document.body.appendChild(temp);
      temp.select();
      document.execCommand('copy');
      temp.remove();
    }
    showToast('Link copied to your clipboard.', 'success');
  } catch (e) {
    showToast('Could not copy automatically — select the link and copy it manually.', 'warning');
  }
}

function openManageModal(achievementId) {
  const link = ALL_SHARE_LINKS.find(l => l.achievementId === achievementId);
  if (!link) return;

  MANAGING_ACHIEVEMENT_ID = achievementId;
  setText('manageShareTitle', link.achievementTitle || 'Untitled');

  const durationSel = document.getElementById('manageShareDuration');
  const customWrap = document.getElementById('manageCustomWrap');
  const includeProof = document.getElementById('manageIncludeProof');

  if (durationSel) durationSel.value = link.permanent ? 'PERMANENT' : 'TWENTY_FOUR_HOURS';
  if (customWrap) customWrap.style.display = 'none';
  if (includeProof) includeProof.checked = !!link.includeProofDocument;

  openModal('manageShareModal');
}

async function saveShareChanges() {
  if (!MANAGING_ACHIEVEMENT_ID) return;

  const durationSel = document.getElementById('manageShareDuration');
  const customInput = document.getElementById('manageShareCustomExpiry');
  const includeProof = document.getElementById('manageIncludeProof');
  const saveBtn = document.getElementById('saveShareChangesBtn');

  const duration = durationSel ? durationSel.value : 'TWENTY_FOUR_HOURS';
  const body = {
    duration,
    includeProofDocument: !!(includeProof && includeProof.checked)
  };

  if (duration === 'CUSTOM') {
    const raw = customInput ? customInput.value : '';
    if (!raw) {
      showToast('Please choose the date and time the link should stop working.', 'warning');
      return;
    }
    if (new Date(raw).getTime() <= Date.now()) {
      showToast('The expiry must be in the future.', 'warning');
      return;
    }
    // datetime-local gives "YYYY-MM-DDTHH:mm", which is exactly what
    // LocalDateTime parses. Do not append a timezone — the server rejects it.
    body.customExpiresAt = raw.length === 16 ? raw + ':00' : raw;
  }

  if (saveBtn) { saveBtn.disabled = true; saveBtn.textContent = 'Saving…'; }

  const res = await ApiClient.patch(`/achievements/${MANAGING_ACHIEVEMENT_ID}/share`, body);

  if (saveBtn) { saveBtn.disabled = false; saveBtn.textContent = 'Save changes'; }

  if (!res.success) {
    showToast(res.message || 'Could not update the link.', 'error');
    return;
  }

  closeModal('manageShareModal');
  showToast('Share link updated.', 'success');
  MANAGING_ACHIEVEMENT_ID = null;
  await loadShareLinks();
}

function openRevokeModal(achievementId) {
  const link = ALL_SHARE_LINKS.find(l => l.achievementId === achievementId);
  if (!link) return;

  REVOKING_ACHIEVEMENT_ID = achievementId;
  setText('revokeShareTitle', link.achievementTitle || 'Untitled');
  openModal('revokeShareModal');
}

async function confirmRevoke() {
  if (!REVOKING_ACHIEVEMENT_ID) return;

  const btn = document.getElementById('confirmRevokeBtn');
  if (btn) { btn.disabled = true; btn.textContent = 'Revoking…'; }

  const res = await ApiClient.delete(`/achievements/${REVOKING_ACHIEVEMENT_ID}/share`);

  if (btn) { btn.disabled = false; btn.textContent = 'Revoke link'; }

  if (!res.success) {
    showToast(res.message || 'Could not revoke the link.', 'error');
    return;
  }

  closeModal('revokeShareModal');
  showToast('Link revoked. It stopped working immediately.', 'success');
  REVOKING_ACHIEVEMENT_ID = null;
  await loadShareLinks();
}

/* ────────────────────────────────────────────────────────────────────────
   Formatting
   ──────────────────────────────────────────────────────────────────────── */
function formatDate(value) {
  if (!value) return '—';
  const d = new Date(value);
  if (isNaN(d.getTime())) return '—';
  return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
}

function formatDateTime(value) {
  if (!value) return '—';
  const d = new Date(value);
  if (isNaN(d.getTime())) return '—';
  return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' })
       + ', ' + d.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
}
