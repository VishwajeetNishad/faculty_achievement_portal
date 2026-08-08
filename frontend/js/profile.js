/**
 * Faculty Profile Controller — Edit Mode Toggle & Temporary State Update
 */

document.addEventListener('DOMContentLoaded', () => {
  const profileForm = document.getElementById('profileForm');
  const editBtn = document.getElementById('editProfileBtn');
  const cancelBtn = document.getElementById('cancelProfileBtn');
  const saveBtn = document.getElementById('saveProfileBtn');

  if (!profileForm) return;

  // Load Initial Profile
  const profile = MockStore.getFacultyProfile();
  populateProfileForm(profile);

  if (editBtn) {
    editBtn.addEventListener('click', () => {
      toggleEditState(true);
    });
  }

  if (cancelBtn) {
    cancelBtn.addEventListener('click', () => {
      populateProfileForm(MockStore.getFacultyProfile());
      toggleEditState(false);
      showToast('Profile editing cancelled', 'info');
    });
  }

  if (profileForm) {
    profileForm.addEventListener('submit', (e) => {
      e.preventDefault();

      const updated = {
        fullName: document.getElementById('profileName')?.value || profile.fullName,
        designation: document.getElementById('profileDesignation')?.value || profile.designation,
        email: document.getElementById('profileEmail')?.value || profile.email
      };

      MockStore.updateFacultyProfile(updated);
      toggleEditState(false);
      showToast('Demo profile changes applied locally in memory.', 'success');
    });
  }
});

function populateProfileForm(profile) {
  const nameInput = document.getElementById('profileName');
  const empInput = document.getElementById('profileEmpId');
  const emailInput = document.getElementById('profileEmail');
  const deptInput = document.getElementById('profileDepartment');
  const desigInput = document.getElementById('profileDesignation');
  const roleInput = document.getElementById('profileRole');

  if (nameInput) nameInput.value = profile.fullName;
  if (empInput) empInput.value = profile.employeeId;
  if (emailInput) emailInput.value = profile.email;
  if (deptInput) deptInput.value = profile.department;
  if (desigInput) desigInput.value = profile.designation;
  if (roleInput) roleInput.value = profile.role;
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
