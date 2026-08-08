/**
 * Achievements Page & Add-Achievement Form Logic
 * Connected to Authenticated Spring Boot Security Endpoints:
 * - GET /api/achievements/me
 * - GET /api/achievements/{id}
 * - GET /api/achievements/status/{status}
 * - POST /api/achievements
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

/**
 * 1. Achievements List & Filtering Controller
 */
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

  // Delete Action Listener (Backend API Call with JWT Authorization)
  const confirmDeleteBtn = document.getElementById('confirmDeleteBtn');
  if (confirmDeleteBtn) {
    confirmDeleteBtn.addEventListener('click', async () => {
      if (!pendingDeleteId) return;

      confirmDeleteBtn.disabled = true;
      confirmDeleteBtn.textContent = 'Deleting...';

      // Backend Endpoint: DELETE /api/achievements/{id}
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

  if (selectedStatus) {
    // Backend Endpoint: GET /api/achievements/status/{status}
    res = await ApiClient.get(`/achievements/status/${selectedStatus}`);
  } else {
    // Authenticated User Endpoint: GET /api/achievements/me
    res = await ApiClient.get('/achievements/me');
  }

  if (res.success && Array.isArray(res.data)) {
    list = res.data;
  } else {
    showToast(res.message || 'Error fetching achievements from API', 'error');
    list = [];
  }

  // Client-side Category, Year & Keyword Filtering
  if (selectedCat) {
    list = list.filter(a => (a.categoryCode === selectedCat) || (a.category === selectedCat));
  }

  if (selectedYear) {
    list = list.filter(a => a.academicYear === selectedYear);
  }

  if (keyword) {
    list = list.filter(a => 
      a.title.toLowerCase().includes(keyword) || 
      (a.description && a.description.toLowerCase().includes(keyword)) ||
      (a.categoryName && a.categoryName.toLowerCase().includes(keyword))
    );
  }

  tableBody.innerHTML = '';

  if (list.length === 0) {
    tableBody.innerHTML = `
      <tr>
        <td colspan="6" class="empty-state">
          <div class="empty-state-title">No matching achievements found</div>
          <p class="empty-state-text">Try adjusting your search criteria or submitting a new record.</p>
        </td>
      </tr>
    `;
    return;
  }

  list.forEach(item => {
    const badgeClass = item.status === 'APPROVED' ? 'badge-approved' : (item.status === 'REJECTED' ? 'badge-rejected' : 'badge-pending');
    const badgeSymbol = item.status === 'APPROVED' ? '✓' : (item.status === 'REJECTED' ? '!' : '●');
    const categoryDisplayName = item.categoryName || item.categoryCode || 'Achievement';

    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td data-label="Title & Details">
        <div class="table-title-cell">${escapeHtml(item.title)}</div>
        <div class="table-subtext">${escapeHtml(categoryDisplayName)}</div>
      </td>
      <td data-label="Category">${escapeHtml(categoryDisplayName)}</td>
      <td data-label="Academic Year">${escapeHtml(item.academicYear)}</td>
      <td data-label="Date">${formatDate(item.achievementDate)}</td>
      <td data-label="Status">
        <span class="badge ${badgeClass}">
          <span class="badge-symbol">${badgeSymbol}</span> ${item.status}
        </span>
      </td>
      <td data-label="Actions">
        <div class="action-btn-group">
          <button class="btn btn-outline btn-sm view-item-btn" data-id="${item.id}">View</button>
          ${item.status === 'PENDING' ? `<button class="btn btn-danger btn-sm delete-item-btn" data-id="${item.id}">Delete</button>` : ''}
        </div>
      </td>
    `;
    tableBody.appendChild(tr);
  });

  tableBody.querySelectorAll('.view-item-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      showAchievementDetailsModal(btn.getAttribute('data-id'));
    });
  });

  tableBody.querySelectorAll('.delete-item-btn').forEach(btn => {
    btn.addEventListener('click', () => {
      pendingDeleteId = btn.getAttribute('data-id');
      openModal('deleteModal');
    });
  });
}

/**
 * 2. Add Achievement Form & Backend POST /api/achievements Controller
 */
function initializeAddAchievementForm() {
  const categorySelect = document.getElementById('categorySelect');
  const extensionSections = document.querySelectorAll('.fieldset-section');
  const form = document.getElementById('addAchievementForm');

  if (categorySelect) {
    categorySelect.addEventListener('change', (e) => {
      const selectedCategory = e.target.value;

      extensionSections.forEach(section => {
        section.style.display = 'none';
        section.querySelectorAll('input, select, textarea').forEach(input => input.disabled = true);
      });

      if (selectedCategory) {
        const targetSection = document.getElementById(`section-${selectedCategory.toLowerCase()}`);
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
      const description = document.getElementById('description')?.value || '';
      const proofUrl = document.getElementById('proofUrl')?.value || '';

      if (!FormValidator.validateRequired(categoryVal) || !FormValidator.validateRequired(title) || !FormValidator.validateDate(date)) {
        showToast('Please fill out all required fields marked with *', 'error');
        return;
      }

      const categoryIdMap = {
        publication: 1,
        patent: 2,
        research_grant: 3,
        workshop_fdp: 4,
        award: 5
      };

      const categoryId = categoryIdMap[categoryVal] || 1;

      const requestPayload = {
        categoryId: categoryId,
        title: title,
        description: description,
        achievementDate: date,
        academicYear: academicYear,
        proofDocumentUrl: proofUrl || null
      };

      const submitBtn = form.querySelector('button[type="submit"]');
      if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.textContent = 'Submitting to Server...';
      }

      // Backend Endpoint: POST /api/achievements (User inferred from JWT token context)
      const res = await ApiClient.post('/achievements', requestPayload);

      if (res.success) {
        showToast('Achievement created successfully in backend MySQL database!', 'success');
        form.reset();
        setTimeout(() => {
          window.location.href = 'achievements.html';
        }, 1000);
      } else {
        showToast(res.message || 'Failed to create achievement record.', 'error');
        if (submitBtn) {
          submitBtn.disabled = false;
          submitBtn.textContent = 'Submit Achievement Record';
        }
      }
    });
  }
}

async function showAchievementDetailsModal(id) {
  // Backend Endpoint: GET /api/achievements/{id}
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
      ${item.proofDocumentUrl ? `<p style="margin-top: 0.75rem;"><strong>Proof Link:</strong> <a href="${escapeHtml(item.proofDocumentUrl)}" target="_blank" rel="noopener">View Supporting Certificate</a></p>` : ''}
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
