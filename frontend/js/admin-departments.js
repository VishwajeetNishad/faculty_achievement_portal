/**
 * Admin — Department Management
 *
 * Connected to live Spring Boot endpoints:
 *   GET    /api/departments/summary   (list + how many accounts belong to each)
 *   POST   /api/departments           (add)
 *   PUT    /api/departments/{id}      (rename / re-describe)
 *   DELETE /api/departments/{id}      (remove — refused with 409 while occupied)
 *
 * All four writes are gated by MANAGE_DEPARTMENTS on the server (an administrator
 * implicitly holds it). The checks in this file only decide what to OFFER; the
 * backend re-reads the permission from the database on every request.
 */

let allDepartmentsData = [];

// Which department the edit modal is about; null means "creating a new one".
let editingDepartmentId = null;

// Which department the delete modal is about.
let deletingDepartment = null;

document.addEventListener('DOMContentLoaded', () => {
  if (!document.getElementById('departmentsTableBody')) return;
  initializeDepartmentsPage();
});

async function initializeDepartmentsPage() {
  if (typeof ensurePermissionsLoaded === 'function') {
    try { await ensurePermissionsLoaded(); } catch (e) { /* fall back to the cached list */ }
  }

  document.getElementById('addDepartmentBtn').addEventListener('click', () => openDepartmentModal(null));
  document.getElementById('deptModalSaveBtn').addEventListener('click', saveDepartment);
  document.getElementById('deptDeleteConfirmBtn').addEventListener('click', confirmDeleteDepartment);
  document.getElementById('searchDepartments').addEventListener('input', filterAndRenderDepartments);
  document.getElementById('deptStaffingFilter').addEventListener('change', filterAndRenderDepartments);

  // One delegated listener so the buttons keep working after every re-render.
  document.getElementById('departmentsTableBody').addEventListener('click', event => {
    const editBtn = event.target.closest('.js-dept-edit');
    if (editBtn) {
      const dept = allDepartmentsData.find(d => String(d.id) === editBtn.getAttribute('data-id'));
      if (dept) openDepartmentModal(dept);
      return;
    }

    const deleteBtn = event.target.closest('.js-dept-delete');
    if (deleteBtn) {
      const dept = allDepartmentsData.find(d => String(d.id) === deleteBtn.getAttribute('data-id'));
      if (dept) openDeleteModal(dept);
    }
  });

  await loadDepartments();
}

// ─────────────────────────────────────────────────────────────────────────────
// Load & render
// ─────────────────────────────────────────────────────────────────────────────

async function loadDepartments() {
  const tbody = document.getElementById('departmentsTableBody');
  tbody.innerHTML = `<tr><td colspan="4" class="empty-state"><div class="spinner"></div>
    <p style="margin-top:0.5rem;">Loading departments…</p></td></tr>`;

  const res = await ApiClient.get('/departments/summary');

  if (!res.success) {
    if (res.status === 403) {
      showDeptNotice('danger', 'You cannot manage departments.',
        'This screen needs the MANAGE_DEPARTMENTS permission, or an administrator role. Ask an administrator to grant it from the User Permissions page.');
      tbody.innerHTML = `<tr><td colspan="4" class="empty-state">
        <div class="empty-state-title">Access Denied</div>
        <p class="empty-state-text">Your account is not permitted to view department management information.</p></td></tr>`;
      document.getElementById('addDepartmentBtn').style.display = 'none';
    } else {
      tbody.innerHTML = `<tr><td colspan="4" class="empty-state">
        <div class="empty-state-title">Could not load departments</div>
        <p class="empty-state-text">${escapeHtml(res.message || 'The server did not return the department list.')}</p></td></tr>`;
    }
    return;
  }

  allDepartmentsData = (res.data || []).slice().sort((a, b) =>
    String(a.code || '').localeCompare(String(b.code || ''), undefined, { sensitivity: 'base' }));

  filterAndRenderDepartments();
}

function filterAndRenderDepartments() {
  const keyword = (document.getElementById('searchDepartments').value || '').toLowerCase().trim();
  const staffing = document.getElementById('deptStaffingFilter').value;

  let filtered = allDepartmentsData;

  if (keyword) {
    filtered = filtered.filter(d =>
      (d.code || '').toLowerCase().includes(keyword) ||
      (d.name || '').toLowerCase().includes(keyword) ||
      (d.description || '').toLowerCase().includes(keyword));
  }

  if (staffing === 'STAFFED') filtered = filtered.filter(d => Number(d.userCount || 0) > 0);
  if (staffing === 'EMPTY') filtered = filtered.filter(d => Number(d.userCount || 0) === 0);

  renderDepartmentsTable(filtered);
  updateDepartmentCounts(filtered);
}

function updateDepartmentCounts(filtered) {
  const assigned = allDepartmentsData.reduce((sum, d) => sum + Number(d.userCount || 0), 0);
  const empty = allDepartmentsData.filter(d => Number(d.userCount || 0) === 0).length;

  document.getElementById('shownDeptCount').textContent = filtered.length;
  document.getElementById('totalDeptCount').textContent = allDepartmentsData.length;
  document.getElementById('totalAssignedCount').textContent = assigned;
  document.getElementById('emptyDeptCount').textContent = empty;
}

function renderDepartmentsTable(departments) {
  const tbody = document.getElementById('departmentsTableBody');
  tbody.innerHTML = '';

  if (departments.length === 0) {
    tbody.innerHTML = `<tr><td colspan="4" class="empty-state">
      <div class="empty-state-title">No departments found</div>
      <p class="empty-state-text">${allDepartmentsData.length === 0
        ? 'No departments exist yet. Add the first one to start creating accounts.'
        : 'No department matches your search.'}</p></td></tr>`;
    return;
  }

  departments.forEach(dept => {
    const count = Number(dept.userCount || 0);
    const isEmpty = count === 0;

    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td data-label="Code & Name">
        <div class="table-title-cell">${escapeHtml(dept.name || '')}</div>
        <div style="margin-top:0.2rem;"><span class="dept-code-chip">${escapeHtml(dept.code || '')}</span></div>
      </td>
      <td data-label="Description">
        ${dept.description
          ? escapeHtml(dept.description)
          : '<span class="table-subtext">No description</span>'}
      </td>
      <td data-label="Accounts">
        <span class="dept-count-pill${isEmpty ? ' is-empty' : ''}">
          ${count} ${count === 1 ? 'account' : 'accounts'}
        </span>
      </td>
      <td data-label="Actions">
        <div class="action-btn-group">
          <button type="button" class="btn btn-outline btn-sm js-dept-edit" data-id="${escapeHtml(String(dept.id))}">Edit</button>
          <button type="button" class="btn btn-danger btn-sm js-dept-delete" data-id="${escapeHtml(String(dept.id))}"
                  ${isEmpty ? '' : 'disabled title="Move the accounts in this department elsewhere first."'}>Delete</button>
        </div>
      </td>
    `;
    tbody.appendChild(tr);
  });
}

// ─────────────────────────────────────────────────────────────────────────────
// Create / edit
// ─────────────────────────────────────────────────────────────────────────────

function openDepartmentModal(dept) {
  editingDepartmentId = dept ? dept.id : null;

  document.getElementById('deptModalNotice').innerHTML = '';
  ['deptCode', 'deptName', 'deptDescription'].forEach(clearDeptFieldError);

  document.getElementById('deptModalTitle').textContent = dept ? 'Edit Department' : 'Add Department';
  document.getElementById('deptModalSaveBtn').textContent = dept ? 'Save Changes' : 'Create Department';

  document.getElementById('deptCode').value = dept ? (dept.code || '') : '';
  document.getElementById('deptName').value = dept ? (dept.name || '') : '';
  document.getElementById('deptDescription').value = dept ? (dept.description || '') : '';

  // Changing a code that faculty already belong to is allowed but worth naming,
  // because the code appears in filters and badges across the portal.
  if (dept && Number(dept.userCount || 0) > 0) {
    showDeptModalNotice('info',
      `${Number(dept.userCount)} account${Number(dept.userCount) === 1 ? '' : 's'} belong to this department.`,
      'Renaming it is safe — nobody is moved. The new name appears everywhere at once.');
  }

  openModal('deptModal');
  document.getElementById('deptCode').focus();
}

async function saveDepartment() {
  const code = document.getElementById('deptCode').value.trim();
  const name = document.getElementById('deptName').value.trim();
  const description = document.getElementById('deptDescription').value.trim();

  ['deptCode', 'deptName', 'deptDescription'].forEach(clearDeptFieldError);
  document.getElementById('deptModalNotice').innerHTML = '';

  // Mirrors the DepartmentRequest bean validation, so an obvious mistake is
  // caught before a round trip. The server validates again regardless.
  let valid = true;
  if (!code) { setDeptFieldError('deptCode', 'Please enter a short code.'); valid = false; }
  else if (!/^[A-Za-z0-9_-]+$/.test(code)) {
    setDeptFieldError('deptCode', 'Use only letters, numbers, dashes and underscores.');
    valid = false;
  }
  if (!name) { setDeptFieldError('deptName', 'Please enter the full department name.'); valid = false; }
  else if (name.length < 2) { setDeptFieldError('deptName', 'The name is too short.'); valid = false; }
  if (!valid) return;

  const payload = { code, name };
  if (description) payload.description = description;

  const btn = document.getElementById('deptModalSaveBtn');
  const originalLabel = btn.textContent;
  btn.disabled = true;
  btn.textContent = 'Saving…';

  const res = editingDepartmentId
    ? await ApiClient.put(`/departments/${encodeURIComponent(editingDepartmentId)}`, payload)
    : await ApiClient.post('/departments', payload);

  btn.disabled = false;
  btn.textContent = originalLabel;

  if (res.success) {
    closeModal('deptModal');
    showToast(editingDepartmentId
      ? `${name} saved.`
      : `${name} added. You can now assign accounts to it.`, 'success');
    editingDepartmentId = null;
    await loadDepartments();
    return;
  }

  if (res.status === 409) {
    // A duplicate code or name — the server says which.
    const message = res.message || 'A department with that code or name already exists.';
    if (/code/i.test(message)) setDeptFieldError('deptCode', message);
    else if (/name/i.test(message)) setDeptFieldError('deptName', message);
    else showDeptModalNotice('danger', 'Already exists.', message);
    return;
  }

  if (res.status === 403) {
    showDeptModalNotice('danger', 'You are not allowed to save this.',
      res.message || 'This needs the MANAGE_DEPARTMENTS permission.');
    return;
  }

  showDeptModalNotice('danger', 'The department could not be saved.',
    res.message || 'Please check the details and try again.');
}

// ─────────────────────────────────────────────────────────────────────────────
// Delete
// ─────────────────────────────────────────────────────────────────────────────

function openDeleteModal(dept) {
  deletingDepartment = dept;
  const count = Number(dept.userCount || 0);

  document.getElementById('deptDeleteMessage').innerHTML = count > 0
    ? `<strong>${escapeHtml(dept.name)}</strong> still has ${count} account${count === 1 ? '' : 's'} in it.
       Every account must belong to a department, so move those accounts to another department first.
       The portal will refuse this deletion until the department is empty.`
    : `Delete <strong>${escapeHtml(dept.name)}</strong> (${escapeHtml(dept.code)})? Nobody belongs to it,
       so nothing else is affected. This cannot be undone.`;

  document.getElementById('deptDeleteConfirmBtn').disabled = count > 0;
  openModal('deptDeleteModal');
}

async function confirmDeleteDepartment() {
  if (!deletingDepartment) return;

  const btn = document.getElementById('deptDeleteConfirmBtn');
  const originalLabel = btn.textContent;
  btn.disabled = true;
  btn.textContent = 'Deleting…';

  const res = await ApiClient.delete(`/departments/${encodeURIComponent(deletingDepartment.id)}`);

  btn.disabled = false;
  btn.textContent = originalLabel;

  if (res.success) {
    closeModal('deptDeleteModal');
    showToast(`${deletingDepartment.name} deleted.`, 'success');
    deletingDepartment = null;
    await loadDepartments();
    return;
  }

  if (res.status === 409) {
    // The authoritative refusal. The count shown in the table can be stale if
    // somebody was added in another tab, so this path is reachable.
    showToast(res.message || 'Accounts still belong to this department, so it cannot be deleted.', 'error');
    closeModal('deptDeleteModal');
    await loadDepartments();
    return;
  }

  showToast(res.message || 'The department could not be deleted.', 'error');
}

// ─────────────────────────────────────────────────────────────────────────────
// Small UI helpers
// ─────────────────────────────────────────────────────────────────────────────

function setDeptFieldError(fieldId, message) {
  const holder = document.getElementById('err-' + fieldId);
  const input = document.getElementById(fieldId);
  if (holder) { holder.textContent = message; holder.style.display = 'block'; }
  if (input) input.classList.add('is-invalid');
}

function clearDeptFieldError(fieldId) {
  const holder = document.getElementById('err-' + fieldId);
  const input = document.getElementById(fieldId);
  if (holder) { holder.textContent = ''; holder.style.display = 'none'; }
  if (input) input.classList.remove('is-invalid');
}

function showDeptNotice(type, title, body) {
  const holder = document.getElementById('deptNotice');
  if (!holder) return;
  holder.innerHTML = `<div class="alert alert-${type}" style="margin-bottom: 1.25rem;">
    <div><strong>${escapeHtml(title)}</strong> ${escapeHtml(body || '')}</div></div>`;
}

function showDeptModalNotice(type, title, body) {
  const holder = document.getElementById('deptModalNotice');
  if (!holder) return;
  holder.innerHTML = `<div class="alert alert-${type}" style="margin-bottom: 1rem;">
    <div><strong>${escapeHtml(title)}</strong> ${escapeHtml(body || '')}</div></div>`;
}
