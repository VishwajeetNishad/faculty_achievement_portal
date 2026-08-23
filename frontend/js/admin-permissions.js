/**
 * Admin — Manage User Permissions (Track A / A9)
 *
 * Live endpoints used:
 *   GET  /api/users                        (roster for the user picker)
 *   GET  /api/permissions                  (catalogue of grantable permissions)
 *   GET  /api/users/{userId}/permissions   (what one user currently holds)
 *   PUT  /api/users/{userId}/permissions   (replace that user's set)
 *
 * SECURITY NOTE: everything in this file is presentation. Disabling a checkbox
 * stops an honest mistake, not an attacker — the backend re-checks the acting
 * administrator's own permissions on the PUT and refuses anything they are not
 * allowed to do (including editing their own account). No user id and no actor
 * identity is ever sent in a request body; the user being edited travels in the
 * URL and the administrator is taken from the JWT server-side.
 */

// Grouping is defined here purely for layout. The backend is the source of
// truth for which codes exist; any code it returns that is not listed below
// still renders, under "Other Permissions".
const PERMISSION_GROUPS = [
  {
    title: 'User Management',
    codes: ['CREATE_FACULTY', 'EDIT_FACULTY', 'CREATE_HOD', 'EDIT_HOD', 'CREATE_ADMIN', 'MANAGE_USER_STATUS']
  },
  {
    title: 'Achievement Management',
    codes: ['VIEW_ALL_ACHIEVEMENTS', 'VERIFY_ACHIEVEMENT', 'EDIT_ACHIEVEMENT', 'DELETE_ACHIEVEMENT']
  },
  {
    title: 'Reporting',
    codes: ['VIEW_REPORTS', 'EXPORT_REPORTS']
  },
  {
    title: 'System Administration',
    codes: ['MANAGE_DEPARTMENTS', 'VIEW_AUDIT_LOGS', 'MANAGE_PERMISSIONS']
  }
];

// These two hand out further power, so only a full administrator may change
// them. The backend enforces this; the tag is here so the reason is visible.
const RESTRICTED_CODES = ['CREATE_ADMIN', 'MANAGE_PERMISSIONS'];

// Permissions the spec defines but that nothing enforces yet — achievements
// stay editable and deletable by their owner only. Saying so on screen avoids
// an administrator ticking a box and expecting a behaviour change.
const NOT_YET_ENFORCED_CODES = ['EDIT_ACHIEVEMENT', 'DELETE_ACHIEVEMENT'];

let permissionCatalogue = [];   // [{ id, permissionCode, description }]
let rosterUsers = [];           // [{ id, fullName, employeeId, role, ... }]
let currentTarget = null;       // the UserPermissionsResponse being edited
let originalCodes = [];         // what the server said, for "Reset Changes"
let signedInUserId = null;
let signedInUserRole = null;    // 'ROLE_ADMIN' | 'ROLE_HOD' | 'ROLE_FACULTY'

document.addEventListener('DOMContentLoaded', () => {
  if (!document.getElementById('permissionGroupsContainer')) return;
  initializePermissionsPage();
});

async function initializePermissionsPage() {
  try {
    const stored = JSON.parse(sessionStorage.getItem('currentUser') || 'null');
    signedInUserId = stored && stored.userId ? Number(stored.userId) : null;
    signedInUserRole = stored && stored.role ? String(stored.role) : null;
  } catch (e) {
    signedInUserId = null;
    signedInUserRole = null;
  }

  const select = document.getElementById('userSelect');

  // The checkbox locking below depends on knowing which permissions the
  // signed-in administrator holds, so wait for the real list rather than
  // rendering from a possibly-empty cache.
  await ensurePermissionsLoaded();

  const [usersRes, permsRes] = await Promise.all([
    ApiClient.get('/users'),
    ApiClient.get('/permissions')
  ]);

  if (!permsRes.success) {
    showCatalogueError(permsRes);
    if (select) select.innerHTML = '<option value="">Unavailable</option>';
    return;
  }
  permissionCatalogue = Array.isArray(permsRes.data) ? permsRes.data : [];

  if (!usersRes.success) {
    if (select) select.innerHTML = '<option value="">Unable to load users</option>';
    showToast(usersRes.message || 'Unable to load the user list.', 'error');
    return;
  }
  rosterUsers = Array.isArray(usersRes.data) ? usersRes.data : [];

  populateUserSelect();

  select?.addEventListener('change', () => {
    const value = select.value;
    if (!value) {
      showEmptyState();
      updateUrlUserId(null);
      return;
    }
    updateUrlUserId(value);
    loadUserPermissions(value);
  });

  document.getElementById('savePermissionsBtn')?.addEventListener('click', savePermissions);
  document.getElementById('resetPermissionsBtn')?.addEventListener('click', () => {
    if (!currentTarget) return;
    renderPermissionGroups(originalCodes);
    showToast('Changes reset to the saved values.', 'info');
  });

  // Deep link support: faculty.html links here with ?userId=…
  const params = new URLSearchParams(window.location.search);
  const preselect = params.get('userId');
  if (preselect && select) {
    select.value = preselect;
    if (select.value === preselect) {
      loadUserPermissions(preselect);
    } else {
      showToast('That user is not in the roster.', 'error');
    }
  }
}

function populateUserSelect() {
  const select = document.getElementById('userSelect');
  if (!select) return;

  select.innerHTML = '<option value="">— Select a user —</option>';

  // Heads of Department first: they are the accounts this screen exists for.
  const sorted = rosterUsers.slice().sort((a, b) => {
    const rank = r => (r === 'ROLE_HOD' || r === 'HOD') ? 0 : (r === 'ROLE_FACULTY' || r === 'FACULTY') ? 1 : 2;
    const byRole = rank(a.role) - rank(b.role);
    if (byRole !== 0) return byRole;
    return (a.fullName || '').localeCompare(b.fullName || '');
  });

  sorted.forEach(u => {
    const opt = document.createElement('option');
    opt.value = String(u.id);
    let label = `${u.fullName || 'Unnamed'} — ${roleLabel(u.role)}`;
    if (u.departmentCode) label += ` (${u.departmentCode})`;
    if (signedInUserId !== null && Number(u.id) === signedInUserId) label += ' — you';
    opt.textContent = label;
    select.appendChild(opt);
  });
}

async function loadUserPermissions(userId) {
  showPermissionCard();
  const container = document.getElementById('permissionGroupsContainer');
  if (container) {
    container.innerHTML = `<div style="text-align:center; padding:2rem;"><div class="spinner"></div>
      <p style="margin-top:0.5rem; font-size:var(--font-size-base); color:var(--text-secondary);">Loading this user's permissions…</p></div>`;
  }
  document.getElementById('permissionActions').style.display = 'none';

  const res = await ApiClient.get(`/users/${encodeURIComponent(userId)}/permissions`);

  if (!res.success) {
    currentTarget = null;
    if (container) {
      container.innerHTML = `<div class="empty-state" style="padding:2rem 1rem;">
        <div class="empty-state-title" style="color:var(--color-danger);">Unable to load permissions</div>
        <p class="empty-state-text">${escapeHtml(res.message || 'Please try again.')}</p></div>`;
    }
    document.getElementById('selectedUserCard').style.display = 'none';
    return;
  }

  currentTarget = res.data;
  originalCodes = Array.isArray(res.data.permissionCodes) ? res.data.permissionCodes.slice() : [];

  renderSelectedUser(res.data);
  renderNotice(res.data);
  renderPermissionGroups(originalCodes);
}

function renderSelectedUser(data) {
  const card = document.getElementById('selectedUserCard');
  if (!card) return;
  card.style.display = '';

  const initials = (data.fullName || '?')
    .trim().split(/\s+/).slice(0, 2)
    .map(part => part.charAt(0).toUpperCase()).join('') || '?';

  document.getElementById('selectedUserAvatar').textContent = initials;
  document.getElementById('selectedUserName').textContent = data.fullName || '—';

  const metaParts = [];
  if (data.employeeId) metaParts.push(data.employeeId);
  if (data.email) metaParts.push(data.email);
  if (data.departmentName) metaParts.push(data.departmentName);
  else if (data.departmentCode) metaParts.push(data.departmentCode);
  document.getElementById('selectedUserMeta').textContent = metaParts.join('  ·  ') || '—';

  const badgeClass = (data.role === 'ROLE_ADMIN' || data.role === 'ADMIN') ? 'badge-rejected'
    : (data.role === 'ROLE_HOD' || data.role === 'HOD') ? 'badge-pending'
    : 'badge-approved';
  document.getElementById('selectedUserRoleBadge').innerHTML =
    `<span class="badge ${badgeClass}">${escapeHtml(roleLabel(data.role))}</span>`;
}

/**
 * Explains up front why the boxes may be read-only, rather than letting the
 * administrator fill in a form and only then be told the save is refused.
 */
function renderNotice(data) {
  const notice = document.getElementById('permissionNotice');
  if (!notice) return;

  const isSelf = signedInUserId !== null && Number(data.userId) === signedInUserId;

  if (data.allFromRole) {
    notice.innerHTML = `<div class="alert alert-warning" style="margin-bottom:1rem;"><div>
      <strong>This account is an Administrator.</strong> Administrators already hold every permission
      through their role, so there is nothing to tick or untick here. To reduce what this person can do,
      change their role instead.</div></div>`;
  } else if (isSelf) {
    notice.innerHTML = `<div class="alert alert-warning" style="margin-bottom:1rem;"><div>
      <strong>This is your own account.</strong> Nobody can change their own permissions &mdash; that is
      the simplest way an account could quietly give itself more power. Ask another administrator to make
      the change.</div></div>`;
  } else {
    notice.innerHTML = '';
  }
}

function renderPermissionGroups(selectedCodes) {
  const container = document.getElementById('permissionGroupsContainer');
  if (!container || !currentTarget) return;

  const selected = new Set(selectedCodes || []);
  const isSelf = signedInUserId !== null && Number(currentTarget.userId) === signedInUserId;
  const readOnly = Boolean(currentTarget.allFromRole) || isSelf;

  // Only permissions the acting administrator holds themselves can be handed
  // out or taken away — the backend refuses the rest, so it is not offered.
  //
  // CREATE_ADMIN and MANAGE_PERMISSIONS are stricter still: only a full
  // Administrator may hand those two out. A Head of Department who has been
  // given MANAGE_PERMISSIONS can therefore manage ordinary permissions, but
  // cannot pass that power on. Without this second test the box would look
  // tickable and the save would come back as "access denied", which is a
  // confusing way to learn the rule.
  const actorIsAdmin = signedInUserRole === 'ROLE_ADMIN';
  const actorHolds = code =>
    can(code) && (actorIsAdmin || RESTRICTED_CODES.indexOf(code) === -1);

  const byCode = {};
  permissionCatalogue.forEach(p => { byCode[p.permissionCode] = p; });

  const placed = new Set();
  let html = '';

  PERMISSION_GROUPS.forEach(group => {
    const available = group.codes.filter(code => byCode[code]);
    if (available.length === 0) return;
    available.forEach(code => placed.add(code));
    html += renderGroupHtml(group.title, available, byCode, selected, readOnly, actorHolds);
  });

  const leftovers = permissionCatalogue
    .map(p => p.permissionCode)
    .filter(code => !placed.has(code));
  if (leftovers.length > 0) {
    html += renderGroupHtml('Other Permissions', leftovers, byCode, selected, readOnly, actorHolds);
  }

  container.innerHTML = html;

  const countLabel = document.getElementById('permissionCountLabel');
  if (countLabel) {
    countLabel.textContent = currentTarget.allFromRole
      ? 'All permissions (from role)'
      : `${selected.size} of ${permissionCatalogue.length} granted`;
  }

  const actions = document.getElementById('permissionActions');
  if (actions) actions.style.display = readOnly ? 'none' : 'flex';

  container.querySelectorAll('input[type="checkbox"]').forEach(box => {
    box.addEventListener('change', updateCountLabel);
  });
}

function renderGroupHtml(title, codes, byCode, selected, readOnly, actorHolds) {
  let items = '';

  codes.forEach(code => {
    const perm = byCode[code];
    const checked = selected.has(code);
    const restricted = RESTRICTED_CODES.indexOf(code) !== -1;
    const notEnforced = NOT_YET_ENFORCED_CODES.indexOf(code) !== -1;

    // Locked when the whole form is read-only, or when the signed-in
    // administrator does not hold this permission themselves.
    const locked = readOnly || !actorHolds(code);

    let tags = '';
    if (restricted) tags += '<span class="permission-restricted-tag">Admin only</span>';
    if (notEnforced) tags += '<span class="permission-restricted-tag" style="color:var(--status-pending-text); background:var(--status-pending-bg); border-color:var(--status-pending-border);">Not yet used</span>';

    let hint = escapeHtml(perm.description || '');
    if (locked && !readOnly) {
      hint += hint ? ' — ' : '';
      // Two different reasons, so say which one applies.
      hint += (restricted && can(code))
        ? 'Only a full Administrator can hand this one out.'
        : 'You cannot change this because you do not hold it yourself.';
    }
    if (notEnforced) {
      hint += hint ? ' ' : '';
      hint += 'Achievements can currently only be edited or deleted by the faculty member who created them, so this has no effect yet.';
    }

    items += `
      <label class="permission-item${locked ? ' is-locked' : ''}">
        <input type="checkbox" value="${escapeHtml(code)}" ${checked ? 'checked' : ''} ${locked ? 'disabled' : ''}>
        <span class="permission-item-text">
          <span class="permission-code">${escapeHtml(code)}${tags}</span>
          <span class="permission-desc">${hint}</span>
        </span>
      </label>`;
  });

  return `
    <div class="permission-group">
      <div class="permission-group-title">${escapeHtml(title)}</div>
      <div class="permission-list">${items}</div>
    </div>`;
}

function updateCountLabel() {
  const countLabel = document.getElementById('permissionCountLabel');
  if (!countLabel || !currentTarget || currentTarget.allFromRole) return;
  countLabel.textContent = `${collectTickedCodes().length} of ${permissionCatalogue.length} granted`;
}

/**
 * Reads the ticked boxes AND keeps any permission the user already holds that
 * the current administrator is not allowed to touch. Without this, saving would
 * silently strip a permission just because its checkbox was disabled.
 */
function collectTickedCodes() {
  const container = document.getElementById('permissionGroupsContainer');
  if (!container) return [];

  const codes = [];
  container.querySelectorAll('input[type="checkbox"]').forEach(box => {
    if (box.disabled) {
      if (originalCodes.indexOf(box.value) !== -1) codes.push(box.value);
    } else if (box.checked) {
      codes.push(box.value);
    }
  });
  return codes;
}

async function savePermissions() {
  if (!currentTarget) return;

  const btn = document.getElementById('savePermissionsBtn');
  const codes = collectTickedCodes();

  const added = codes.filter(c => originalCodes.indexOf(c) === -1);
  const removed = originalCodes.filter(c => codes.indexOf(c) === -1);

  if (added.length === 0 && removed.length === 0) {
    showToast('Nothing has changed.', 'info');
    return;
  }

  const summary = [];
  if (added.length) summary.push(`grant ${added.length}`);
  if (removed.length) summary.push(`revoke ${removed.length}`);
  const ok = window.confirm(
    `Save permissions for ${currentTarget.fullName}?\n\nThis will ${summary.join(' and ')} permission(s).`
    + `\n\nGranting: ${added.length ? added.join(', ') : 'none'}`
    + `\nRevoking: ${removed.length ? removed.join(', ') : 'none'}`
  );
  if (!ok) return;

  btn.disabled = true;
  btn.textContent = 'Saving…';

  // Only permissionCodes is sent. The user being changed is in the URL and the
  // administrator making the change comes from the JWT on the server.
  const res = await ApiClient.put(`/users/${encodeURIComponent(currentTarget.userId)}/permissions`, {
    permissionCodes: codes
  });

  btn.disabled = false;
  btn.textContent = 'Save Permissions';

  if (!res.success) {
    let message = res.message || 'Failed to save permissions.';
    if (res.status === 403) message = message || 'You are not allowed to make this change.';
    showToast(message, 'error');
    return;
  }

  currentTarget = res.data;
  originalCodes = Array.isArray(res.data.permissionCodes) ? res.data.permissionCodes.slice() : [];
  renderSelectedUser(res.data);
  renderNotice(res.data);
  renderPermissionGroups(originalCodes);

  showToast(`Permissions saved for ${res.data.fullName}. The change is effective immediately.`, 'success');
}

// ---- small helpers ---------------------------------------------------------

function roleLabel(role) {
  switch (role) {
    case 'ROLE_ADMIN':
    case 'ADMIN':
      return 'Administrator';
    case 'ROLE_HOD':
    case 'HOD':
      return 'Head of Department';
    case 'ROLE_FACULTY':
    case 'FACULTY':
      return 'Faculty';
    default:
      return role || 'User';
  }
}

function showPermissionCard() {
  document.getElementById('emptyStateCard').style.display = 'none';
  document.getElementById('permissionCard').style.display = '';
}

function showEmptyState() {
  currentTarget = null;
  originalCodes = [];
  document.getElementById('emptyStateCard').style.display = '';
  document.getElementById('permissionCard').style.display = 'none';
  document.getElementById('selectedUserCard').style.display = 'none';
}

function showCatalogueError(res) {
  const container = document.getElementById('permissionGroupsContainer');
  showPermissionCard();
  const message = res.status === 403
    ? 'Managing permissions requires the MANAGE_PERMISSIONS permission.'
    : (res.message || 'Unable to load the permission catalogue.');
  if (container) {
    container.innerHTML = `<div class="empty-state" style="padding:2rem 1rem;">
      <div class="empty-state-title" style="color:var(--color-danger);">Access denied</div>
      <p class="empty-state-text">${escapeHtml(message)}</p></div>`;
  }
  showToast(message, 'error');
}

/** Keeps the address bar in step so the page can be reloaded or bookmarked. */
function updateUrlUserId(userId) {
  const url = new URL(window.location.href);
  if (userId) url.searchParams.set('userId', userId);
  else url.searchParams.delete('userId');
  window.history.replaceState({}, '', url);
}
