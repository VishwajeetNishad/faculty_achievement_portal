/**
 * Admin — Create & Edit User Accounts
 *
 * Connected to live Spring Boot endpoints:
 *   GET   /api/departments            (department dropdown)
 *   GET   /api/users/{id}             (prefill when editing)
 *   POST  /api/users                  (create — needs CREATE_FACULTY / CREATE_HOD / CREATE_ADMIN)
 *   PUT   /api/users/{id}             (edit    — needs EDIT_FACULTY / EDIT_HOD)
 *   PATCH /api/users/{id}/status      (activate / deactivate — needs MANAGE_USER_STATUS)
 *
 * One page serves both jobs. Open it plain to create an account, or with
 * ?userId=<id> to edit one.
 *
 * A note on the permission checks in this file: they exist so the page does not
 * offer an action that would only come back as "Access denied". They are NOT
 * security. The backend re-reads the real permissions from the database on every
 * single request and is the only thing that actually decides.
 */

// Which account we are editing, or null when creating a new one.
let editingUserId = null;

// The account as the server currently has it, so we can send only what changed.
let originalUser = null;

let departmentsCache = [];

// role value → the permission needed to put somebody into that role
const ROLE_CREATE_PERMISSION = {
  ROLE_FACULTY: 'CREATE_FACULTY',
  ROLE_HOD: 'CREATE_HOD',
  ROLE_ADMIN: 'CREATE_ADMIN'
};

const ROLE_LABEL = {
  ROLE_FACULTY: 'Faculty',
  ROLE_HOD: 'Head of Department',
  ROLE_ADMIN: 'Administrator'
};

document.addEventListener('DOMContentLoaded', () => {
  if (!document.getElementById('userForm')) return;
  initializeUserForm();
});

function currentSessionUser() {
  try {
    return JSON.parse(sessionStorage.getItem('currentUser') || '{}');
  } catch (e) {
    return {};
  }
}

function isSessionAdmin() {
  const role = (currentSessionUser().role || '').toUpperCase();
  return role === 'ROLE_ADMIN' || role === 'ADMIN';
}

async function initializeUserForm() {
  const params = new URLSearchParams(window.location.search);
  const rawId = params.get('userId');
  editingUserId = rawId && /^\d+$/.test(rawId) ? rawId : null;

  // The role tiles and the status section are shown or hidden based on what the
  // signed-in user may do, so wait for the real permission list to arrive.
  if (typeof ensurePermissionsLoaded === 'function') {
    try { await ensurePermissionsLoaded(); } catch (e) { /* fall back to the cached list */ }
  }

  wireRoleTiles();
  wirePasswordMeter();
  wireStatusSection();
  wireResetPasswordToggle();

  await loadDepartmentOptions();

  if (editingUserId) {
    await enterEditMode(editingUserId);
  } else {
    enterCreateMode();
  }

  document.getElementById('userForm').addEventListener('submit', onSubmitUserForm);
}

// ─────────────────────────────────────────────────────────────────────────────
// Mode setup
// ─────────────────────────────────────────────────────────────────────────────

function enterCreateMode() {
  document.getElementById('pageTitle').textContent = 'Add User Account';
  document.title = 'Add User Account | NIET Admin Portal';
  document.getElementById('submitUserBtn').textContent = 'Create Account';
  document.getElementById('statusSection').style.display = 'none';
  document.getElementById('resetPasswordToggleGroup').style.display = 'none';
  document.getElementById('passwordGroup').style.display = '';

  applyRoleAvailabilityForCreate();

  // Nothing at all can be created without one of the three create permissions.
  const anyCreate = ['ROLE_FACULTY', 'ROLE_HOD', 'ROLE_ADMIN']
    .some(r => canAssignRoleOnCreate(r));

  if (!anyCreate) {
    showFormNotice(
      'danger',
      'You cannot create accounts.',
      'Creating a user account needs the CREATE_FACULTY, CREATE_HOD or CREATE_ADMIN permission. Ask an administrator to grant one from the User Permissions page.'
    );
    disableForm();
  }
}

async function enterEditMode(userId) {
  document.getElementById('pageTitle').textContent = 'Edit User Account';
  document.title = 'Edit User Account | NIET Admin Portal';
  document.getElementById('submitUserBtn').textContent = 'Save Changes';
  document.getElementById('passwordSectionSub').textContent =
    'Leave the password alone for a normal edit. Tick the box below only when you need to set a new one.';

  const form = document.getElementById('userForm');
  const loading = document.getElementById('formLoading');
  form.style.display = 'none';
  loading.style.display = 'block';

  const res = await ApiClient.get(`/users/${encodeURIComponent(userId)}`);

  loading.style.display = 'none';
  form.style.display = '';

  if (!res.success) {
    const message = res.status === 403
      ? 'You do not have permission to view this account. Viewing a user needs an admin role or one of the user-management permissions.'
      : res.status === 404
        ? 'That user account no longer exists. It may have been removed since the roster was loaded.'
        : (res.message || 'The account could not be loaded.');
    showFormNotice('danger', 'Account could not be opened.', message);
    disableForm();
    return;
  }

  originalUser = res.data;
  prefillForm(originalUser);

  // Password is optional when editing: only sent if the box is ticked.
  document.getElementById('resetPasswordToggleGroup').style.display = '';
  document.getElementById('passwordGroup').style.display = 'none';
  document.getElementById('passwordLabel').classList.remove('required-field');
  document.getElementById('passwordLabel').textContent = 'New Password';

  applyRoleAvailabilityForEdit(originalUser);

  // Status lives behind its own permission and its own endpoint.
  if (can('MANAGE_USER_STATUS')) {
    document.getElementById('statusSection').style.display = '';
  }

  const self = String(currentSessionUser().userId || '') === String(originalUser.id);
  if (self) {
    showFormNotice(
      'info',
      'This is your own account.',
      'To protect the portal from locking itself out, you cannot change your own role, your own department or your own status here. Everything else can be edited.'
    );
    disableSelfProtectedFields();
  }

  if (!canEditTarget(originalUser)) {
    const label = ROLE_LABEL[normaliseRole(originalUser.role)] || originalUser.role;
    showFormNotice(
      'danger',
      'You cannot edit this account.',
      `Editing a ${label} account needs the matching permission. Ask an administrator to grant it from the User Permissions page.`
    );
    disableForm();
  }
}

function prefillForm(user) {
  document.getElementById('fullName').value = user.fullName || '';
  document.getElementById('employeeId').value = user.employeeId || '';
  document.getElementById('email').value = user.email || '';
  document.getElementById('phone').value = user.phone || '';
  document.getElementById('designation').value = user.designation || '';

  const deptSelect = document.getElementById('departmentId');
  if (user.departmentId) deptSelect.value = String(user.departmentId);

  const roleValue = normaliseRole(user.role);
  const radio = document.querySelector(`#roleGrid input[value="${roleValue}"]`);
  if (radio) {
    radio.checked = true;
    refreshRoleTileStyling();
  }

  const statusSelect = document.getElementById('statusSelect');
  if (user.status) statusSelect.value = user.status;
  statusSelect.setAttribute('data-original', user.status || 'ACTIVE');
}

/** The backend accepts both forms; the UI standardises on the ROLE_ prefix. */
function normaliseRole(role) {
  if (!role) return 'ROLE_FACULTY';
  const upper = String(role).toUpperCase();
  return upper.startsWith('ROLE_') ? upper : 'ROLE_' + upper;
}

// ─────────────────────────────────────────────────────────────────────────────
// Role tiles
// ─────────────────────────────────────────────────────────────────────────────

function wireRoleTiles() {
  document.querySelectorAll('#roleGrid input[name="role"]').forEach(input => {
    input.addEventListener('change', () => {
      refreshRoleTileStyling();
      refreshAdminWarning();
      refreshRoleChangeNotice();
    });
  });
  refreshRoleTileStyling();
}

function refreshRoleTileStyling() {
  document.querySelectorAll('#roleGrid .role-option').forEach(tile => {
    const input = tile.querySelector('input[name="role"]');
    tile.classList.toggle('is-selected', !!input && input.checked);
  });
}

function refreshAdminWarning() {
  const selected = selectedRole();
  const warning = document.getElementById('adminRoleWarning');
  warning.style.display = selected === 'ROLE_ADMIN' ? '' : 'none';
}

/** In edit mode, spell out what a role change actually does. */
function refreshRoleChangeNotice() {
  const holder = document.getElementById('roleChangeNotice');
  if (!originalUser) { holder.innerHTML = ''; return; }

  const from = normaliseRole(originalUser.role);
  const to = selectedRole();

  if (from === to) { holder.innerHTML = ''; return; }

  let extra = '';
  if (to === 'ROLE_HOD') {
    extra = ' They will be able to verify achievements for everyone in their department.';
  } else if (from === 'ROLE_HOD') {
    extra = ' They will lose the ability to verify their department’s achievements.';
  } else if (to === 'ROLE_ADMIN') {
    extra = ' They will gain full control of the portal.';
  } else if (from === 'ROLE_ADMIN') {
    extra = ' They will lose administrator access to the portal.';
  }

  holder.innerHTML = `
    <div class="alert alert-warning" style="margin-top: 1rem;">
      <div>
        <strong>Role change:</strong> ${escapeHtml(ROLE_LABEL[from] || from)} &rarr;
        ${escapeHtml(ROLE_LABEL[to] || to)}.${escapeHtml(extra)}
        This is recorded in the audit trail and takes effect immediately &mdash; they do not need to sign in again.
      </div>
    </div>`;
}

function selectedRole() {
  const checked = document.querySelector('#roleGrid input[name="role"]:checked');
  return checked ? checked.value : 'ROLE_FACULTY';
}

function canAssignRoleOnCreate(roleValue) {
  const needed = ROLE_CREATE_PERMISSION[roleValue];
  if (!needed || !can(needed)) return false;
  // The backend additionally insists that only an administrator may create
  // another administrator, even with CREATE_ADMIN granted. Mirror that here so
  // the tile is not offered and then refused.
  if (roleValue === 'ROLE_ADMIN' && !isSessionAdmin()) return false;
  return true;
}

function canEditTarget(user) {
  const role = normaliseRole(user.role);
  if (role === 'ROLE_ADMIN') return isSessionAdmin();
  if (role === 'ROLE_HOD') return can('EDIT_HOD');
  return can('EDIT_FACULTY');
}

function applyRoleAvailabilityForCreate() {
  document.querySelectorAll('#roleGrid .role-option').forEach(tile => {
    const roleValue = tile.getAttribute('data-role');
    lockRoleTile(tile, !canAssignRoleOnCreate(roleValue),
      roleValue === 'ROLE_ADMIN' && can('CREATE_ADMIN') && !isSessionAdmin()
        ? 'Admins only'
        : 'Not permitted');
  });
  selectFirstAvailableRole();
  refreshAdminWarning();
}

function applyRoleAvailabilityForEdit(user) {
  const currentRole = normaliseRole(user.role);
  const self = String(currentSessionUser().userId || '') === String(user.id);

  document.querySelectorAll('#roleGrid .role-option').forEach(tile => {
    const roleValue = tile.getAttribute('data-role');

    // The account's existing role is always shown, never locked — otherwise the
    // form could not be submitted at all without changing the role.
    if (roleValue === currentRole) { lockRoleTile(tile, false); return; }

    // Nobody may change their own role, so every other tile is locked.
    if (self) { lockRoleTile(tile, true, 'Not for your own account'); return; }

    lockRoleTile(tile, !canAssignRoleOnCreate(roleValue), 'Not permitted');
  });

  refreshAdminWarning();
}

function lockRoleTile(tile, locked, tagText) {
  const input = tile.querySelector('input[name="role"]');
  tile.classList.toggle('is-locked', locked);
  if (input) input.disabled = locked;

  const nameEl = tile.querySelector('.role-option-name');
  const existingTag = nameEl ? nameEl.querySelector('.role-locked-tag') : null;
  if (existingTag) existingTag.remove();

  if (locked && nameEl) {
    const tag = document.createElement('span');
    tag.className = 'role-locked-tag';
    tag.textContent = tagText || 'Not permitted';
    nameEl.appendChild(tag);
  }
}

function selectFirstAvailableRole() {
  const checked = document.querySelector('#roleGrid input[name="role"]:checked');
  if (checked && !checked.disabled) { refreshRoleTileStyling(); return; }

  const firstOpen = document.querySelector('#roleGrid input[name="role"]:not(:disabled)');
  if (firstOpen) firstOpen.checked = true;
  refreshRoleTileStyling();
}

// ─────────────────────────────────────────────────────────────────────────────
// Password field
// ─────────────────────────────────────────────────────────────────────────────

function wireResetPasswordToggle() {
  const checkbox = document.getElementById('resetPasswordCheckbox');
  if (!checkbox) return;

  checkbox.addEventListener('change', () => {
    const group = document.getElementById('passwordGroup');
    group.style.display = checkbox.checked ? '' : 'none';
    if (!checkbox.checked) {
      document.getElementById('password').value = '';
      updatePasswordMeter('');
      clearFieldError('password');
    }
  });
}

function wirePasswordMeter() {
  const input = document.getElementById('password');
  if (!input) return;
  input.addEventListener('input', () => updatePasswordMeter(input.value));
}

/**
 * A rough strength hint, purely to encourage a better password. The only rule
 * the backend actually enforces is a minimum of 8 characters.
 */
function updatePasswordMeter(value) {
  let score = 0;
  if (value.length >= 8) score++;
  if (value.length >= 12) score++;
  if (/[A-Z]/.test(value) && /[a-z]/.test(value)) score++;
  if (/\d/.test(value) && /[^A-Za-z0-9]/.test(value)) score++;

  const colours = ['#EF4444', '#F59E0B', '#0284C7', '#10B981'];
  const words = ['Weak', 'Fair', 'Good', 'Strong'];

  document.querySelectorAll('.pw-meter-bar').forEach((bar, index) => {
    bar.style.backgroundColor = index < score ? colours[score - 1] : 'var(--border-color)';
  });

  const label = document.getElementById('pwMeterLabel');
  if (!value) {
    label.textContent = 'Use at least 8 characters. Mixing letters, numbers and a symbol makes it much harder to guess.';
    label.style.color = 'var(--text-secondary)';
  } else if (value.length < 8) {
    label.textContent = `Too short — ${8 - value.length} more character${8 - value.length === 1 ? '' : 's'} needed.`;
    label.style.color = 'var(--status-rejected-text)';
  } else {
    label.textContent = `${words[score - 1]} password.`;
    label.style.color = 'var(--text-secondary)';
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Status section
// ─────────────────────────────────────────────────────────────────────────────

function wireStatusSection() {
  const select = document.getElementById('statusSelect');
  if (!select) return;

  select.addEventListener('change', () => {
    const changed = select.value !== (select.getAttribute('data-original') || 'ACTIVE');
    document.getElementById('statusReasonGroup').style.display = changed ? '' : 'none';
  });
}

// ─────────────────────────────────────────────────────────────────────────────
// Departments
// ─────────────────────────────────────────────────────────────────────────────

async function loadDepartmentOptions() {
  const select = document.getElementById('departmentId');
  const res = await ApiClient.get('/departments');

  if (!res.success || !Array.isArray(res.data) || res.data.length === 0) {
    select.innerHTML = '<option value="">No departments available</option>';
    showFormNotice(
      'warning',
      'No departments found.',
      'Every account must belong to a department. Add one on the Departments page first.'
    );
    return;
  }

  departmentsCache = res.data
    .slice()
    .sort((a, b) => String(a.code || '').localeCompare(String(b.code || ''), undefined, { sensitivity: 'base' }));

  select.innerHTML = '<option value="">Select a department…</option>';
  departmentsCache.forEach(dept => {
    const option = document.createElement('option');
    option.value = String(dept.id);
    option.textContent = `${dept.code} — ${dept.name}`;
    select.appendChild(option);
  });
}

// ─────────────────────────────────────────────────────────────────────────────
// Submit
// ─────────────────────────────────────────────────────────────────────────────

async function onSubmitUserForm(event) {
  event.preventDefault();
  clearAllFieldErrors();

  const values = readFormValues();
  if (!validateFormValues(values)) return;

  const button = document.getElementById('submitUserBtn');
  const originalLabel = button.textContent;
  button.disabled = true;
  button.textContent = editingUserId ? 'Saving…' : 'Creating…';

  try {
    if (editingUserId) {
      await saveExistingUser(values);
    } else {
      await createNewUser(values);
    }
  } finally {
    button.disabled = false;
    button.textContent = originalLabel;
  }
}

function readFormValues() {
  return {
    fullName: document.getElementById('fullName').value.trim(),
    employeeId: document.getElementById('employeeId').value.trim(),
    email: document.getElementById('email').value.trim(),
    phone: document.getElementById('phone').value.trim(),
    designation: document.getElementById('designation').value.trim(),
    departmentId: document.getElementById('departmentId').value,
    role: selectedRole(),
    password: document.getElementById('password').value,
    resetPassword: !!document.getElementById('resetPasswordCheckbox')?.checked,
    status: document.getElementById('statusSelect')?.value || 'ACTIVE',
    statusReason: document.getElementById('statusReason')?.value.trim() || ''
  };
}

function validateFormValues(values) {
  let valid = true;

  if (!values.fullName) { setFieldError('fullName', 'Please enter the full name.'); valid = false; }
  else if (values.fullName.length < 2) { setFieldError('fullName', 'The name is too short.'); valid = false; }

  if (!values.employeeId) { setFieldError('employeeId', 'Please enter the employee ID.'); valid = false; }

  if (!values.email) { setFieldError('email', 'Please enter the official email.'); valid = false; }
  else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.email)) { setFieldError('email', 'That does not look like a valid email address.'); valid = false; }

  if (!values.designation) { setFieldError('designation', 'Please enter the designation.'); valid = false; }

  if (!values.departmentId) { setFieldError('departmentId', 'Please choose a department.'); valid = false; }

  const passwordRequired = !editingUserId || values.resetPassword;
  if (passwordRequired) {
    if (!values.password) { setFieldError('password', 'Please enter a password.'); valid = false; }
    else if (values.password.length < 8) { setFieldError('password', 'The password must be at least 8 characters.'); valid = false; }
  }

  if (!valid) {
    showToast('Please correct the highlighted fields.', 'error');
    const firstError = document.querySelector('.form-error.is-visible');
    if (firstError) firstError.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  return valid;
}

async function createNewUser(values) {
  const payload = {
    fullName: values.fullName,
    employeeId: values.employeeId,
    email: values.email,
    password: values.password,
    designation: values.designation,
    departmentId: Number(values.departmentId),
    role: values.role
  };
  if (values.phone) payload.phone = values.phone;

  const res = await ApiClient.post('/users', payload);

  if (res.success) {
    showToast(`${values.fullName} can now sign in as ${ROLE_LABEL[values.role]}.`, 'success');
    // Straight to the roster so the new row is visible, which is the proof the
    // account really exists rather than a message claiming it does.
    setTimeout(() => { window.location.href = 'faculty.html'; }, 900);
    return;
  }

  reportSaveFailure(res, 'create this account');
}

async function saveExistingUser(values) {
  const payload = {};

  if (values.fullName !== (originalUser.fullName || '')) payload.fullName = values.fullName;
  if (values.employeeId !== (originalUser.employeeId || '')) payload.employeeId = values.employeeId;
  if (values.email.toLowerCase() !== String(originalUser.email || '').toLowerCase()) payload.email = values.email;
  if (values.phone !== (originalUser.phone || '')) payload.phone = values.phone;
  if (values.designation !== (originalUser.designation || '')) payload.designation = values.designation;
  if (String(values.departmentId) !== String(originalUser.departmentId || '')) payload.departmentId = Number(values.departmentId);
  if (values.role !== normaliseRole(originalUser.role)) payload.role = values.role;
  if (values.resetPassword && values.password) payload.newPassword = values.password;

  const originalStatus = document.getElementById('statusSelect')?.getAttribute('data-original') || 'ACTIVE';
  const statusChanged = can('MANAGE_USER_STATUS') && values.status !== originalStatus;

  if (Object.keys(payload).length === 0 && !statusChanged) {
    showToast('Nothing has changed, so there is nothing to save.', 'info');
    return;
  }

  // Two endpoints because status sits behind its own permission. Each result is
  // reported separately — if one succeeds and the other is refused, you are told
  // exactly which is which rather than a single misleading message.
  let profileSaved = true;

  if (Object.keys(payload).length > 0) {
    const res = await ApiClient.put(`/users/${encodeURIComponent(editingUserId)}`, payload);
    if (res.success) {
      originalUser = res.data;
      prefillForm(originalUser);
      refreshRoleChangeNotice();
      const checkbox = document.getElementById('resetPasswordCheckbox');
      if (checkbox) { checkbox.checked = false; document.getElementById('passwordGroup').style.display = 'none'; }
      document.getElementById('password').value = '';
      showToast('Account details saved.', 'success');
    } else {
      profileSaved = false;
      reportSaveFailure(res, 'save these details');
    }
  }

  if (statusChanged) {
    const body = { status: values.status };
    if (values.statusReason) body.reason = values.statusReason;

    const res = await ApiClient.patch(`/users/${encodeURIComponent(editingUserId)}/status`, body);
    if (res.success) {
      originalUser = res.data;
      const select = document.getElementById('statusSelect');
      select.setAttribute('data-original', res.data.status);
      document.getElementById('statusReasonGroup').style.display = 'none';
      document.getElementById('statusReason').value = '';
      showToast(
        values.status === 'ACTIVE'
          ? 'Account reactivated — they can sign in again.'
          : 'Account deactivated — they can no longer sign in.',
        values.status === 'ACTIVE' ? 'success' : 'warning'
      );
    } else {
      reportSaveFailure(res, 'change the status');
      return;
    }
  }

  if (profileSaved) {
    setTimeout(() => { window.location.href = 'faculty.html'; }, 1100);
  }
}

/**
 * Turns the backend's HTTP status into a message that says what to do next.
 * The server's own message is shown whenever it has one, because that is where
 * the precise reason lives (which field clashed, which guard tripped).
 */
function reportSaveFailure(res, action) {
  const serverMessage = res.message || '';

  if (res.status === 409) {
    showToast(serverMessage || 'That employee ID or email is already in use.', 'error');
    if (/employee/i.test(serverMessage)) setFieldError('employeeId', serverMessage);
    else if (/email/i.test(serverMessage)) setFieldError('email', serverMessage);
    else showFormNotice('danger', 'Change refused.', serverMessage);
    return;
  }

  if (res.status === 403) {
    showFormNotice('danger', `You are not allowed to ${action}.`,
      serverMessage || 'Your account does not hold the permission this needs.');
    showToast('Access denied by the server.', 'error');
    return;
  }

  if (res.status === 400) {
    showToast(serverMessage || 'Some of the details were rejected. Please check them.', 'error');
    showFormNotice('danger', 'The details were rejected.', serverMessage);
    return;
  }

  showToast(serverMessage || `Could not ${action}.`, 'error');
}

// ─────────────────────────────────────────────────────────────────────────────
// Small UI helpers
// ─────────────────────────────────────────────────────────────────────────────

function setFieldError(fieldId, message) {
  const holder = document.getElementById('err-' + fieldId);
  const input = document.getElementById(fieldId);
  if (holder) {
    holder.textContent = message;
    holder.classList.add('is-visible');
    holder.style.display = 'block';
  }
  if (input) input.classList.add('is-invalid');
}

function clearFieldError(fieldId) {
  const holder = document.getElementById('err-' + fieldId);
  const input = document.getElementById(fieldId);
  if (holder) {
    holder.textContent = '';
    holder.classList.remove('is-visible');
    holder.style.display = 'none';
  }
  if (input) input.classList.remove('is-invalid');
}

function clearAllFieldErrors() {
  ['fullName', 'employeeId', 'email', 'phone', 'designation', 'departmentId', 'password']
    .forEach(clearFieldError);
  document.getElementById('formNotice').innerHTML = '';
}

function showFormNotice(type, title, body) {
  const holder = document.getElementById('formNotice');
  if (!holder) return;
  holder.innerHTML = `
    <div class="alert alert-${type}" style="margin-bottom: 1.25rem;">
      <div><strong>${escapeHtml(title)}</strong> ${escapeHtml(body || '')}</div>
    </div>`;
}

function disableForm() {
  const form = document.getElementById('userForm');
  form.querySelectorAll('input, select, textarea, button[type="submit"]').forEach(el => { el.disabled = true; });
  document.getElementById('formActionNote').textContent =
    'This form is read-only because your account cannot perform this action.';
}

/** Fields the backend refuses to let anyone change on their own account. */
function disableSelfProtectedFields() {
  document.getElementById('departmentId').disabled = true;
  const statusSelect = document.getElementById('statusSelect');
  if (statusSelect) statusSelect.disabled = true;
  document.querySelectorAll('#roleGrid input[name="role"]').forEach(input => { input.disabled = true; });
}
