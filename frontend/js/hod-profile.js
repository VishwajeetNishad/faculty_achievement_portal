/**
 * HOD Profile controller.
 * Reads identity from window.HOD.me (already loaded via GET /auth/me in hod-common.js).
 * Saves editable fields via PUT /users/me {fullName, phone, designation}.
 * Employee ID / email / role / department are read-only (server never modifies them).
 */

document.addEventListener('DOMContentLoaded', initHodProfile);

async function initHodProfile() {
  const me = await window.HOD.ready;
  if (!me) return;

  hodFillProfileForm(me);

  const form = document.getElementById('hodProfileForm');
  if (form) form.addEventListener('submit', hodSaveProfile);
}

function hodPrettyRole(role) {
  const r = String(role || '').toUpperCase().replace(/^ROLE_/, '');
  if (r === 'HOD') return 'Head of Department (HOD)';
  if (r === 'ADMIN') return 'Administrator';
  if (r === 'FACULTY') return 'Faculty';
  return role || '—';
}

function hodFillProfileForm(u) {
  const set = (id, v) => { const el = document.getElementById(id); if (el) el.textContent = (v === null || v === undefined || v === '') ? '—' : v; };
  const setVal = (id, v) => { const el = document.getElementById(id); if (el) el.value = v || ''; };

  set('hodPfAvatar', hodInitials(u.fullName));
  set('hodPfHeaderName', u.fullName || '—');
  set('hodPfHeaderRole', hodPrettyRole(u.role));

  set('hodPfEmpId', u.employeeId);
  set('hodPfEmail', u.email);
  set('hodPfRole', hodPrettyRole(u.role));
  set('hodPfDept', u.departmentName ? `${u.departmentName}${u.departmentCode ? ` (${u.departmentCode})` : ''}` : (u.departmentCode || '—'));

  setVal('hodPfName', u.fullName);
  setVal('hodPfPhone', u.phone);
  setVal('hodPfDesignation', u.designation);
}

function hodClearProfileErrors() {
  ['hodPfNameErr', 'hodPfPhoneErr', 'hodPfDesignationErr'].forEach((id) => { const el = document.getElementById(id); if (el) el.textContent = ''; });
  ['hodPfName', 'hodPfPhone', 'hodPfDesignation'].forEach((id) => { const el = document.getElementById(id); if (el) el.classList.remove('has-error'); });
}

function hodSetFieldError(inputId, errId, msg) {
  const input = document.getElementById(inputId);
  const err = document.getElementById(errId);
  if (input) input.classList.add('has-error');
  if (err) err.textContent = msg;
}

async function hodSaveProfile(e) {
  e.preventDefault();
  hodClearProfileErrors();

  const fullName = (document.getElementById('hodPfName').value || '').trim();
  const phone = (document.getElementById('hodPfPhone').value || '').trim();
  const designation = (document.getElementById('hodPfDesignation').value || '').trim();

  // Mirror the server DTO constraints (fullName 2–100, phone ≤20, designation ≤100).
  let firstInvalid = null;
  if (fullName.length < 2 || fullName.length > 100) { hodSetFieldError('hodPfName', 'hodPfNameErr', 'Full name must be 2–100 characters.'); firstInvalid = firstInvalid || 'hodPfName'; }
  if (phone.length > 20) { hodSetFieldError('hodPfPhone', 'hodPfPhoneErr', 'Phone must not exceed 20 characters.'); firstInvalid = firstInvalid || 'hodPfPhone'; }
  if (designation.length > 100) { hodSetFieldError('hodPfDesignation', 'hodPfDesignationErr', 'Designation must not exceed 100 characters.'); firstInvalid = firstInvalid || 'hodPfDesignation'; }
  if (firstInvalid) { const el = document.getElementById(firstInvalid); if (el) el.focus(); return; }

  const btn = document.getElementById('hodPfSave');
  const original = btn ? btn.innerHTML : '';
  if (btn) { btn.disabled = true; btn.innerHTML = `<span class="material-symbols-outlined">hourglass_top</span> Saving…`; }

  const res = await ApiClient.put('/users/me', { fullName, phone, designation });

  if (btn) { btn.disabled = false; btn.innerHTML = original; }

  if (!res.success) {
    showToast(res.message || 'Could not save your profile.', 'error');
    return;
  }

  // Refresh identity everywhere from the authoritative server response.
  const updated = res.data || { ...window.HOD.me, fullName, phone, designation };
  window.HOD.me = updated;
  hodFillProfileForm(updated);

  const nameEl = document.getElementById('hodTopUserName');
  if (nameEl) nameEl.textContent = updated.fullName || 'Head of Department';
  const avatarEl = document.getElementById('hodTopAvatar');
  if (avatarEl) avatarEl.textContent = hodInitials(updated.fullName);

  showToast('Profile updated successfully.', 'success');
}
