/**
 * Achievements Page & Add-Achievement Form Logic
 * Connected to Authenticated Spring Boot Security Endpoints:
 * - GET /api/achievements/me
 * - GET /api/achievements/{id}
 * - POST /api/achievements
 * - POST /api/achievements/{id}/proof
 * - GET /api/achievements/{id}/proof
 * - PUT /api/achievements/{id}
 * - DELETE /api/achievements/{id}
 */

let pendingDeleteId = null;

document.addEventListener('DOMContentLoaded', () => {
  if (document.getElementById('achievementsTableBody')) {
    initializeAchievementsPage();
  }

  if (document.getElementById('addAchievementForm')) {
    initializeAddAchievementForm();
  }
});

function initializeAchievementsPage() {
  const searchInput = document.getElementById('searchKeyword');
  const categoryFilter = document.getElementById('filterCategory');
  const statusFilter = document.getElementById('filterStatus');
  const yearFilter = document.getElementById('filterYear');
  const clearBtn = document.getElementById('clearFiltersBtn');

  renderAchievementsTable();

  if (searchInput) searchInput.addEventListener('input', renderAchievementsTable);
  if (categoryFilter) categoryFilter.addEventListener('change', renderAchievementsTable);
  if (statusFilter) statusFilter.addEventListener('change', renderAchievementsTable);
  if (yearFilter) yearFilter.addEventListener('change', renderAchievementsTable);

  if (clearBtn) {
    clearBtn.addEventListener('click', () => {
      if (searchInput) searchInput.value = '';
      if (categoryFilter) categoryFilter.value = '';
      if (statusFilter) statusFilter.value = '';
      if (yearFilter) yearFilter.value = '';
      renderAchievementsTable();
      showToast('Filters cleared', 'info');
    });
  }

  const confirmDeleteBtn = document.getElementById('confirmDeleteBtn');
  if (confirmDeleteBtn) {
    confirmDeleteBtn.addEventListener('click', async () => {
      if (!pendingDeleteId) return;

      confirmDeleteBtn.disabled = true;
      confirmDeleteBtn.textContent = 'Deleting...';

      const res = await ApiClient.delete(`/achievements/${pendingDeleteId}`);
      if (res.success) {
        showToast('Achievement deleted successfully from backend database.', 'success');
        closeModal('deleteModal');
        pendingDeleteId = null;
        await renderAchievementsTable();
      } else {
        showToast(res.message || 'Failed to delete achievement', 'error');
      }

      confirmDeleteBtn.disabled = false;
      confirmDeleteBtn.textContent = 'Confirm Delete';
    });
  }
}

async function renderAchievementsTable() {
  const tableBody = document.getElementById('achievementsTableBody');
  if (!tableBody) return;

  const selectedStatus = document.getElementById('filterStatus')?.value;
  const selectedCat = document.getElementById('filterCategory')?.value;
  const selectedYear = document.getElementById('filterYear')?.value;
  const keyword = (document.getElementById('searchKeyword')?.value || '').toLowerCase().trim();

  tableBody.innerHTML = `<tr><td colspan="6" class="empty-state"><div class="spinner"></div><p style="margin-top:0.5rem;">Loading records from live API...</p></td></tr>`;

  let list = [];
  let res;

  if (selectedStatus && selectedStatus.trim() !== '') {
    res = await ApiClient.get(`/achievements/status/${selectedStatus.toUpperCase()}`);
  } else {
    res = await ApiClient.get('/achievements/me');
  }

  if (!res.success) {
    tableBody.innerHTML = `
      <tr>
        <td colspan="6" class="empty-state">
          <div class="empty-state-title" style="color: var(--danger-color);">Error Loading Data</div>
          <p class="empty-state-text">${escapeHtml(res.message)}</p>
        </td>
      </tr>
    `;
    return;
  }

  list = Array.isArray(res.data) ? res.data : [];

  if (selectedCat && selectedCat.trim() !== '') {
    list = list.filter(a => (a.categoryCode || '').toLowerCase() === selectedCat.toLowerCase());
  }

  if (selectedYear && selectedYear.trim() !== '') {
    list = list.filter(a => a.academicYear === selectedYear);
  }

  if (keyword && keyword !== '') {
    list = list.filter(a => 
      (a.title || '').toLowerCase().includes(keyword) || 
      (a.categoryName || '').toLowerCase().includes(keyword) ||
      (a.description || '').toLowerCase().includes(keyword)
    );
  }

  tableBody.innerHTML = '';

  if (list.length === 0) {
    tableBody.innerHTML = `
      <tr>
        <td colspan="6" class="empty-state">
          <div class="empty-state-title">No Achievements Found</div>
          <p class="empty-state-text">No records match the current filters.</p>
        </td>
      </tr>
    `;
    return;
  }

  list.forEach(item => {
    const tr = document.createElement('tr');
    const statusClass = (item.status || 'PENDING').toLowerCase();
    
    tr.innerHTML = `
      <td data-label="Title & Category">
        <div class="table-title-cell">${escapeHtml(item.title)}</div>
        <div class="table-subtext">${escapeHtml(item.categoryName || item.categoryCode)}</div>
      </td>
      <td data-label="Date">${formatDate(item.achievementDate)}</td>
      <td data-label="Academic Year">${escapeHtml(item.academicYear)}</td>
      <td data-label="Status">
        <span class="badge badge-${statusClass}">${item.status}</span>
      </td>
      <td data-label="Proof">
        ${item.proofDocumentUrl ? `<button class="btn btn-outline btn-sm view-proof-btn" data-id="${item.id}">📄 View PDF</button>` : '<span style="color:#94A3B8; font-size:0.85rem;">No File</span>'}
      </td>
      <td data-label="Actions">
        <div style="display:flex; gap:0.5rem;">
          <button class="btn btn-outline btn-sm view-item-btn" data-id="${item.id}">View</button>
          <button class="btn btn-danger btn-sm delete-item-btn" data-id="${item.id}">Delete</button>
        </div>
      </td>
    `;
    tableBody.appendChild(tr);
  });

  // Attach Proof PDF Click Handlers
  tableBody.querySelectorAll('.view-proof-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const id = btn.getAttribute('data-id');
      openProtectedProofPdf(id);
    });
  });

  tableBody.querySelectorAll('.view-item-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      const id = btn.getAttribute('data-id');
      showAchievementDetailsModal(id);
    });
  });

  tableBody.querySelectorAll('.delete-item-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      pendingDeleteId = btn.getAttribute('data-id');
      openModal('deleteModal');
    });
  });
}

function initializeAddAchievementForm() {
  const form = document.getElementById('addAchievementForm');
  const categorySelect = document.getElementById('achievementCategory');
  const fieldsetSections = document.querySelectorAll('.fieldset-section');

  if (categorySelect) {
    categorySelect.addEventListener('change', () => {
      const selectedVal = categorySelect.value;
      
      fieldsetSections.forEach(sec => {
        sec.style.display = 'none';
        sec.querySelectorAll('input, select, textarea').forEach(input => input.disabled = true);
      });

      const catMap = {
        '1': 'section-publication',
        '2': 'section-patent',
        '3': 'section-research_grant',
        '4': 'section-workshop_fdp',
        '5': 'section-award'
      };

      const targetId = catMap[selectedVal];
      if (targetId) {
        const targetSection = document.getElementById(targetId);
        if (targetSection) {
          targetSection.style.display = 'block';
          targetSection.querySelectorAll('input, select, textarea').forEach(input => input.disabled = false);
        }
      }
    });
  }

  if (form) {
    form.addEventListener('submit', async (e) => {
      e.preventDefault();

      const categoryVal = categorySelect.value;
      const title = document.getElementById('achievementTitle').value;
      const date = document.getElementById('achievementDate').value;
      const academicYear = document.getElementById('academicYear').value;
      const description = document.getElementById('achievementDescription')?.value || '';
      const proofUrl = document.getElementById('proofUrl')?.value || '';
      const proofFileInput = document.getElementById('proofFileInput');

      if (!FormValidator.validateRequired(categoryVal) || !FormValidator.validateRequired(title) || !FormValidator.validateDate(date)) {
        showToast('Please fill out all required fields marked with *', 'error');
        return;
      }

      const requestPayload = {
        categoryId: parseInt(categoryVal, 10),
        title: title,
        description: description,
        achievementDate: date,
        academicYear: academicYear,
        proofDocumentUrl: proofUrl || null
      };

      const submitBtn = document.getElementById('submitAchievementBtn');
      if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.textContent = 'Submitting Record...';
      }

      // Step 1: Create Achievement Record -> POST /api/achievements
      const res = await ApiClient.post('/achievements', requestPayload);

      if (!res.success || !res.data) {
        showToast(res.message || 'Failed to create achievement record.', 'error');
        if (submitBtn) {
          submitBtn.disabled = false;
          submitBtn.textContent = 'Submit Achievement Record';
        }
        return;
      }

      const createdId = res.data.id;

      // Step 2: Upload PDF File if selected -> POST /api/achievements/{id}/proof
      if (proofFileInput && proofFileInput.files && proofFileInput.files.length > 0) {
        const pdfFile = proofFileInput.files[0];
        
        if (!pdfFile.name.toLowerCase().endsWith('.pdf')) {
          showToast('Record created, but file upload failed: File must be a PDF (.pdf).', 'warning');
        } else {
          if (submitBtn) submitBtn.textContent = 'Uploading PDF Proof...';
          
          const formData = new FormData();
          formData.append('file', pdfFile);

          const uploadRes = await ApiClient.upload(`/achievements/${createdId}/proof`, formData);
          if (uploadRes.success) {
            showToast('Achievement record & PDF proof document uploaded successfully!', 'success');
          } else {
            showToast(`Achievement created, but PDF upload failed: ${uploadRes.message}`, 'warning');
          }
        }
      } else {
        showToast('Achievement created successfully!', 'success');
      }

      form.reset();
      setTimeout(() => {
        window.location.href = 'achievements.html';
      }, 1000);
    });
  }
}

async function openProtectedProofPdf(id) {
  showToast('Downloading protected PDF document...', 'info');
  const res = await ApiClient.downloadBlob(`/achievements/${id}/proof`);
  
  if (res.success && res.objectUrl) {
    window.open(res.objectUrl, '_blank');
  } else {
    showToast(res.message || 'Unable to load proof document', 'error');
  }
}

async function showAchievementDetailsModal(id) {
  const res = await ApiClient.get(`/achievements/${id}`);
  if (!res.success || !res.data) {
    showToast(res.message || 'Achievement details not found or access denied', 'error');
    return;
  }

  const item = res.data;
  const modalBody = document.getElementById('viewModalContent');
  if (modalBody) {
    modalBody.innerHTML = `
      <p style="margin-bottom: 0.5rem;"><strong>Title:</strong> ${escapeHtml(item.title)}</p>
      <p style="margin-bottom: 0.5rem;"><strong>Category:</strong> ${escapeHtml(item.categoryName || item.categoryCode)}</p>
      <p style="margin-bottom: 0.5rem;"><strong>Academic Year:</strong> ${escapeHtml(item.academicYear)}</p>
      <p style="margin-bottom: 0.5rem;"><strong>Achievement Date:</strong> ${formatDate(item.achievementDate)}</p>
      <p style="margin-bottom: 0.5rem;"><strong>Status:</strong> <span class="badge badge-${item.status.toLowerCase()}">${item.status}</span></p>
      ${item.description ? `<p style="margin-bottom: 0.5rem;"><strong>Description:</strong> ${escapeHtml(item.description)}</p>` : ''}
      ${item.verificationComment ? `<p style="margin-bottom: 0.5rem; color:#DC2626;"><strong>Reviewer Comment:</strong> ${escapeHtml(item.verificationComment)}</p>` : ''}
      ${item.proofDocumentUrl ? `<p style="margin-top: 0.75rem;"><button class="btn btn-primary btn-sm" onclick="openProtectedProofPdf(${item.id})">📄 View Protected Proof PDF</button></p>` : ''}
    `;
    openModal('viewModal');
  }
}

function formatDate(dateStr) {
  if (!dateStr) return '';
  const d = new Date(dateStr);
  return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
}

function escapeHtml(str) {
  if (!str) return '';
  return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
