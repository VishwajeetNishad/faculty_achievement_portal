/**
 * Faculty Profile Controller — PDF Design Matching (Pages 23 & 24)
 * Connected to GET /api/auth/me and PUT /api/users/me
 */

let currentProfile = null;

document.addEventListener('DOMContentLoaded', () => {
  loadProfile();

  const editBtn = document.getElementById('editProfileBtn');
  const saveBtn = document.getElementById('saveProfileBtn');
  const cancelBtn = document.getElementById('cancelEditBtn');
  const profileForm = document.getElementById('profileForm');

  if (editBtn) {
    editBtn.addEventListener('click', () => toggleEditMode(true));
  }

  if (cancelBtn) {
    cancelBtn.addEventListener('click', () => {
      if (currentProfile) populateProfile(currentProfile);
      toggleEditMode(false);
    });
  }

  if (saveBtn) {
    saveBtn.addEventListener('click', async (e) => {
      e.preventDefault();
      await saveProfileChanges();
    });
  }

  if (profileForm) {
    profileForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      await saveProfileChanges();
    });
  }
});

async function loadProfile() {
  try {
    const res = await ApiClient.get('/auth/me');
    if (res.success && res.data) {
      currentProfile = res.data;
      populateProfile(currentProfile);
    } else {
      showToast(res.message || 'Failed to load profile', 'error');
    }
  } catch (err) {
    console.error('Profile loading error:', err);
    showToast('Failed to load profile from backend server', 'error');
  }
}

function populateProfile(profile) {
  // Initials for the big avatar on the profile card. initialsFrom() lives in
  // common.js and is null-safe; the hand-rolled copy this replaced called
  // .substring() straight on profile.fullName without checking it, so a user
  // with no name recorded crashed this function and left the page half-drawn.
  const initials = initialsFrom(profile.fullName || profile.email);

  // Left Avatar Card
  const bigAvatar = document.getElementById('profileBigAvatar');
  const dispName = document.getElementById('profileDisplayName');
  const dispDesig = document.getElementById('profileDisplayDesignation');
  const dispDept = document.getElementById('profileDisplayDept');
  const dispEmp = document.getElementById('profileDisplayEmpId');

  // An em dash for anything the profile does not record, matching the Academic
  // Information block below which already did this. These four fallbacks used to
  // invent data instead: a name ("Dr. Faculty"), a department ("CSE"), and worst
  // of all an employee ID ("NIET-CSE-2024") that looked authentic enough for
  // somebody to copy off the screen into a real form.
  if (bigAvatar) bigAvatar.textContent = initials;
  if (dispName) dispName.textContent = profile.fullName || '—';
  if (dispDesig) dispDesig.textContent = profile.designation || '—';
  if (dispDept) dispDept.textContent = profile.departmentName || profile.departmentCode || '—';
  if (dispEmp) dispEmp.textContent = profile.employeeId || '—';

  // Personal Information
  const viewName = document.getElementById('viewFullName');
  const editName = document.getElementById('editFullName');
  const viewEmail = document.getElementById('viewEmail');
  const viewPhone = document.getElementById('viewPhone');
  const editPhone = document.getElementById('editPhone');

  if (viewName) viewName.textContent = profile.fullName || '—';
  if (editName) editName.value = profile.fullName || '';
  if (viewEmail) viewEmail.textContent = profile.email || '—';
  if (viewPhone) viewPhone.textContent = profile.phone || '—';
  if (editPhone) editPhone.value = profile.phone || '';

  // Academic Information
  const viewEmpCode = document.getElementById('viewEmpCode');
  const viewDesig = document.getElementById('viewDesignation');
  const viewDept = document.getElementById('viewDepartment');

  if (viewEmpCode) viewEmpCode.textContent = profile.employeeId || '—';
  if (viewDesig) viewDesig.textContent = profile.designation || '—';
  if (viewDept) viewDept.textContent = profile.departmentName || profile.departmentCode || '—';

  // Role and account status. Both were fixed text in the markup with no id, so
  // nothing could ever change them: a Head of Department read "Faculty" and a
  // suspended account read "Active". ROLE_DISPLAY_NAME comes from common.js and
  // accepts either storage form ("ROLE_HOD" or "HOD").
  const viewRole = document.getElementById('viewRole');
  const viewStatus = document.getElementById('viewAccountStatus');
  const statusPill = document.getElementById('profileStatusPill');

  if (viewRole) {
    const roleKey = String(profile.role || '').toUpperCase();
    viewRole.textContent = ROLE_DISPLAY_NAME[roleKey] || profile.role || '—';
  }

  // Only ACTIVE is coloured as reassurance. Anything else — SUSPENDED, INACTIVE,
  // or a value this frontend has not seen — gets the warning palette instead of
  // being quietly styled as if it were fine. The colours live in profile.html's
  // stylesheet as is-active / is-inactive modifiers rather than being written
  // here, so this function decides the state and the stylesheet owns the paint.
  const status = profile.status ? String(profile.status).toUpperCase() : '';
  const statusLabel = status ? status.charAt(0) + status.slice(1).toLowerCase() : '';
  const isActive = status === 'ACTIVE';

  if (viewStatus) {
    viewStatus.textContent = statusLabel || '—';
    viewStatus.classList.toggle('is-active', !!statusLabel && isActive);
    viewStatus.classList.toggle('is-inactive', !!statusLabel && !isActive);
  }

  if (statusPill) {
    // Stays hidden when the backend records no status at all, rather than
    // showing an empty coloured pill that suggests a state nobody asserted.
    statusPill.textContent = statusLabel ? `• ${statusLabel}` : '';
    statusPill.style.display = statusLabel ? 'inline-flex' : 'none';
    statusPill.classList.toggle('is-active', !!statusLabel && isActive);
    statusPill.classList.toggle('is-inactive', !!statusLabel && !isActive);
  }

  // The sidebar account block and the header avatar are deliberately not set
  // here. They carry data-identity attributes and common.js fills them from the
  // same /auth/me profile, so there is one implementation of "who is signed in"
  // instead of two that could drift apart. See applyIdentityWidget().
}

function toggleEditMode(isEditing) {
  const editBtn = document.getElementById('editProfileBtn');
  const saveBtn = document.getElementById('saveProfileBtn');
  const cancelBtn = document.getElementById('cancelEditBtn');

  const viewName = document.getElementById('viewFullName');
  const editName = document.getElementById('editFullName');
  const viewPhone = document.getElementById('viewPhone');
  const editPhone = document.getElementById('editPhone');

  if (editBtn) editBtn.style.display = isEditing ? 'none' : 'inline-flex';
  if (saveBtn) saveBtn.style.display = isEditing ? 'inline-flex' : 'none';
  if (cancelBtn) cancelBtn.style.display = isEditing ? 'inline-flex' : 'none';

  if (viewName) viewName.style.display = isEditing ? 'none' : 'inline';
  if (editName) editName.style.display = isEditing ? 'block' : 'none';
  if (viewPhone) viewPhone.style.display = isEditing ? 'none' : 'inline';
  if (editPhone) editPhone.style.display = isEditing ? 'block' : 'none';
}

async function saveProfileChanges() {
  const fullName = document.getElementById('editFullName')?.value?.trim();
  const phone = document.getElementById('editPhone')?.value?.trim();

  if (fullName && fullName.length < 2) {
    showToast('Full name must be at least 2 characters', 'error');
    return;
  }

  const saveBtn = document.getElementById('saveProfileBtn');
  if (saveBtn) {
    saveBtn.disabled = true;
    saveBtn.textContent = 'Saving...';
  }

  try {
    const res = await ApiClient.put('/users/me', {
      fullName: fullName || currentProfile.fullName,
      phone: phone || ''
    });

    if (res.success && res.data) {
      currentProfile = res.data;
      populateProfile(currentProfile);

      // Keep the shared profile in step so the sidebar and header show a changed
      // name straight away instead of waiting for the next page load. Merged
      // rather than assigned because PUT /users/me omits the permissions list
      // (only GET /auth/me populates it) and overwriting would lose it.
      window.CURRENT_USER_PROFILE = Object.assign({}, window.CURRENT_USER_PROFILE, res.data);
      applyIdentityWidget();

      toggleEditMode(false);
      showToast('Profile updated successfully!', 'success');
    } else {
      showToast(res.message || 'Failed to update profile', 'error');
    }
  } catch (err) {
    showToast('Error saving profile changes', 'error');
  } finally {
    if (saveBtn) {
      saveBtn.disabled = false;
      saveBtn.textContent = 'Save Changes';
    }
  }
}
