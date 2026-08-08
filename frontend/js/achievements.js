/**
 * Achievements Page & Add-Achievement Form Logic
 * Integrated with Spring Boot REST API endpoints:
 * - GET /api/achievements/user/{userId}
 * - GET /api/achievements/{id}
 * - GET /api/achievements/status/{status}
 * - GET /api/achievements/department/{departmentId}
 * - POST /api/achievements?userId=1
 * - PUT /api/achievements/{id}?userId=1
 * - DELETE /api/achievements/{id}?userId=1
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

  // Initial Table Render
  renderAchievementsTable();

  // Attach filter change listeners
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

  // Confirm Delete Modal Action Listener (Backend API Call)
  const confirmDeleteBtn = document.getElementById('confirmDeleteBtn');
  if (confirmDeleteBtn) {
    confirmDeleteBtn.addEventListener('click', async () => {
      if (!pendingDeleteId) return;

      confirmDeleteBtn.disabled = true;
      confirmDeleteBtn.textContent = 'Deleting...';

      if (CONFIG.DATA_SOURCE === "API") {
        const res = await ApiClient.delete(`/achievements/${pendingDeleteId}?userId=${CONFIG.DEV_USER_ID}`);
        if (res.success) {
          showToast('Achievement deleted successfully from backend database.', 'success');
          closeModal('deleteModal');
          pendingDeleteId = null;
          await renderAchievementsTable();
        } else {
          showToast(res.message || 'Failed to delete achievement', 'error');
        }
      } else {
        MockStore.deleteAchievement(pendingDeleteId);
        showToast('Demo achievement deleted.', 'success');
        closeModal('deleteModal');
        pendingDeleteId = null;
        renderAchievementsTable();
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

  let list = [];

  if (CONFIG.DATA_SOURCE === "API") {
    tableBody.innerHTML = `<tr><td colspan="6" class="empty-state"><div class="spinner"></div><p style="margin-top:0.5rem;">Loading records from live API...</p></td></tr>`;

    let res;
    if (selectedStatus) {
      // Backend Endpoint: GET /api/achievements/status/{status}
      res = await ApiClient.get(`/achievements/status/${selectedStatus}`);
    } else {
      // Backend Endpoint: GET /api/achievements/user/{userId}
      res = await ApiClient.get(`/achievements/user/${CONFIG.DEV_USER_ID}`);
    }

    if (res.success && Array.isArray(res.data)) {
      list = res.data;
    } else {
      showToast(res.message || 'Error fetching achievements from API', 'error');
      list = MockStore.getAchievements().filter(a => a.userId === CONFIG.DEV_USER_ID);
    }
  } else {
    list = MockStore.getAchievements().filter(a => a.userId === CONFIG.DEV_USER_ID);
    if (selectedStatus) {
      list = list.filter(a => a.status === selectedStatus);
    }
  }

  // Client-side Category & Keyword Filtering
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
          <p class="empty-state-text">Try adjusting your search criteria or clearing filters.</p>
        </td>
      </tr>
    `;
    return;
  }

  list.forEach(item => {
    const badgeClass = item.status === 'APPROVED' ? 'badge-approved' : (item.status === 'REJECTED' ? 'badge-rejected' : 'badge-pending');
    const badgeSymbol = item.status === 'APPROVED' ? '✓' : (item.status === 'REJECTED' ? '!' : '●');
    const categoryDisplayName = item.categoryName || item.categoryLabel || item.categoryCode || 'Achievement';

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

  // Attach dynamic row listeners
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

      // Map Frontend Category string to Backend AchievementCategory ID
      // Category #1: PUBLICATION, Category #2: PATENT, Category #3: RESEARCH_GRANT, Category #4: WORKSHOP_FDP, Category #5: AWARD
      const categoryIdMap = {
        publication: 1,
        patent: 2,
        research_grant: 3,
        workshop_fdp: 4,
        award: 5
      };

      const categoryId = categoryIdMap[categoryVal] || 1;

      // Construct Backend AchievementCreateRequest DTO payload
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

      if (CONFIG.DATA_SOURCE === "API") {
        // Backend Endpoint: POST /api/achievements?userId=1
        const res = await ApiClient.post(`/achievements?userId=${CONFIG.DEV_USER_ID}`, requestPayload);

        if (res.success) {
          showToast('Achievement created successfully in backend MySQL database!', 'success');
          form.reset();
          setTimeout(() => {
            window.location.href = 'achievements.html';
          }, 1200);
        } else {
          showToast(res.message || 'Failed to create achievement record.', 'error');
          if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.textContent = 'Submit Achievement Record';
          }
        }
      } else {
        MockStore.addAchievement({
          category: categoryVal.toUpperCase(),
          categoryLabel: categoryVal,
          title: title,
          achievementDate: date,
          academicYear: academicYear,
          description: description,
          proofDocumentUrl: proofUrl
        });
        showToast('Demo submission completed.', 'success');
        form.reset();
        setTimeout(() => {
          window.location.href = 'achievements.html';
        }, 1000);
      }
    });
  }
}

async function showAchievementDetailsModal(id) {
  let item = null;

  if (CONFIG.DATA_SOURCE === "API") {
    // Backend Endpoint: GET /api/achievements/{id}
    const res = await ApiClient.get(`/achievements/${id}`);
    if (res.success && res.data) {
      item = res.data;
    }
  }

  if (!item) {
    item = MockStore.getAchievementById(id);
  }

  if (!item) {
    showToast('Achievement details not found', 'error');
    return;
  }

  const modalBody = document.getElementById('viewModalContent');
  if (modalBody) {
    modalBody.innerHTML = `
      <p style="margin-bottom: 0.5rem;"><strong>Title:</strong> ${escapeHtml(item.title)}</p>
      <p style="margin-bottom: 0.5rem;"><strong>Category:</strong> ${escapeHtml(item.categoryName || item.categoryLabel)}</p>
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
