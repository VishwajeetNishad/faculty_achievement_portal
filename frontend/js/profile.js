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
  // Initials
  const names = (profile.fullName || 'User').split(' ');
  const initials = names.length >= 2 ? (names[0][0] + names[names.length - 1][0]).toUpperCase() : profile.fullName.substring(0, 2).toUpperCase();

  // Left Avatar Card
  const bigAvatar = document.getElementById('profileBigAvatar');
  const dispName = document.getElementById('profileDisplayName');
  const dispDesig = document.getElementById('profileDisplayDesignation');
  const dispDept = document.getElementById('profileDisplayDept');
  const dispEmp = document.getElementById('profileDisplayEmpId');

  if (bigAvatar) bigAvatar.textContent = initials;
  if (dispName) dispName.textContent = profile.fullName || 'Dr. Faculty';
  if (dispDesig) dispDesig.textContent = profile.designation || 'Faculty';
  if (dispDept) dispDept.textContent = profile.departmentName || profile.departmentCode || 'CSE';
  if (dispEmp) dispEmp.textContent = profile.employeeId || 'NIET-CSE-2024';

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

  // Sidebar widgets
  const sidebarAvatar = document.getElementById('sidebarAvatar');
  const sidebarName = document.getElementById('sidebarUserName');
  const sidebarRole = document.getElementById('sidebarUserRole');
  const headerAvatar = document.getElementById('headerAvatar');

  if (sidebarAvatar) sidebarAvatar.textContent = initials;
  if (headerAvatar) headerAvatar.textContent = initials;
  if (sidebarName) sidebarName.textContent = profile.fullName;
  if (sidebarRole) sidebarRole.textContent = `${profile.designation || 'Faculty'} • ${profile.departmentCode || 'CSE'}`;
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
