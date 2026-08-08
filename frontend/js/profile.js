/**
 * Faculty Profile Controller — Real Backend API Integration
 * Uses GET /api/auth/me for profile data and PUT /api/users/me for updates.
 * No MockStore dependency.
 */

let currentProfile = null;

document.addEventListener('DOMContentLoaded', () => {
  const profileForm = document.getElementById('profileForm');
  const editBtn = document.getElementById('editProfileBtn');
  const cancelBtn = document.getElementById('cancelProfileBtn');
  const saveBtn = document.getElementById('saveProfileBtn');

  if (!profileForm) return;

  // Load real profile from backend
  loadProfile();

  if (editBtn) {
    editBtn.addEventListener('click', () => {
      toggleEditState(true);
    });
  }

  if (cancelBtn) {
    cancelBtn.addEventListener('click', () => {
      if (currentProfile) {
        populateProfileForm(currentProfile);
      }
      toggleEditState(false);
      showToast('Profile editing cancelled', 'info');
    });
  }

  if (profileForm) {
    profileForm.addEventListener('submit', async (e) => {
      e.preventDefault();
      await saveProfile();
    });
  }
});

async function loadProfile() {
  const profileForm = document.getElementById('profileForm');
  if (!profileForm) return;

  // Show loading state
  const nameInput = document.getElementById('profileName');
  if (nameInput) nameInput.value = 'Loading...';

  try {
    const res = await ApiClient.get('/auth/me');

    if (res.success && res.data) {
      currentProfile = res.data;
      populateProfileForm(currentProfile);
    } else if (res.status === 401) {
      showToast('Session expired. Please sign in again.', 'error');
      setTimeout(() => { window.location.href = 'login.html?session=expired'; }, 1500);
    } else {
      showToast(res.message || 'Error loading profile', 'error');
    }
  } catch (error) {
    console.error('Error loading profile:', error);
    showToast('Unable to connect to server. Please check that the backend is running.', 'error');
  }
}

function populateProfileForm(profile) {
  const nameInput = document.getElementById('profileName');
  const empInput = document.getElementById('profileEmpId');
  const emailInput = document.getElementById('profileEmail');
  const deptInput = document.getElementById('profileDepartment');
  const desigInput = document.getElementById('profileDesignation');
  const roleInput = document.getElementById('profileRole');
  const phoneInput = document.getElementById('profilePhone');
  const statusInput = document.getElementById('profileStatus');

  if (nameInput) nameInput.value = profile.fullName || '';
  if (empInput) empInput.value = profile.employeeId || '';
  if (emailInput) emailInput.value = profile.email || '';
  if (deptInput) deptInput.value = profile.departmentName || '';
  if (desigInput) desigInput.value = profile.designation || '';
  if (roleInput) roleInput.value = profile.role || '';
  if (phoneInput) phoneInput.value = profile.phone || '';
  if (statusInput) statusInput.value = profile.status || '';
}

async function saveProfile() {
  const fullName = document.getElementById('profileName')?.value?.trim();
  const designation = document.getElementById('profileDesignation')?.value?.trim();
  const phone = document.getElementById('profilePhone')?.value?.trim();

  // Client-side validation
  if (fullName && fullName.length < 2) {
    showToast('Full name must be at least 2 characters', 'error');
    return;
  }

  if (phone && !/^[0-9+\-\s()]{7,20}$/.test(phone)) {
    showToast('Please enter a valid phone number', 'error');
    return;
  }

  const updateData = {};
  if (fullName) updateData.fullName = fullName;
  if (designation) updateData.designation = designation;
  updateData.phone = phone || '';

  const saveBtn = document.getElementById('saveProfileBtn');
  try {
    if (saveBtn) {
      saveBtn.disabled = true;
      saveBtn.textContent = 'Saving...';
    }

    const res = await ApiClient.put('/users/me', updateData);

    if (res.success && res.data) {
      currentProfile = res.data;
      populateProfileForm(currentProfile);
      toggleEditState(false);
      showToast('Profile updated successfully!', 'success');
    } else if (res.status === 400) {
      showToast(res.message || 'Invalid profile data. Please check your input.', 'error');
    } else if (res.status === 401) {
      showToast('Session expired. Please sign in again.', 'error');
      setTimeout(() => { window.location.href = 'login.html?session=expired'; }, 1500);
    } else if (res.status === 403) {
      showToast('Access denied. You do not have permission to update this profile.', 'error');
    } else {
      showToast(res.message || 'Error saving profile. Please try again.', 'error');
    }
  } catch (error) {
    console.error('Error saving profile:', error);
    showToast('Unable to connect to server. Please try again.', 'error');
  } finally {
    if (saveBtn) {
      saveBtn.disabled = false;
      saveBtn.textContent = 'Save Profile Changes';
    }
  }
}

function toggleEditState(isEditable) {
  const editableInputs = document.querySelectorAll('.profile-editable');
  const editBtn = document.getElementById('editProfileBtn');
  const actionGroup = document.getElementById('profileActionGroup');

  editableInputs.forEach(input => {
    input.readOnly = !isEditable;
    if (isEditable) {
      input.classList.add('is-editing');
    } else {
      input.classList.remove('is-editing');
    }
  });

  if (editBtn) editBtn.style.display = isEditable ? 'none' : 'inline-flex';
  if (actionGroup) actionGroup.style.display = isEditable ? 'flex' : 'none';
}
