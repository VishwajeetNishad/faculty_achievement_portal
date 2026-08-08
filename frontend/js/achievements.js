/**
 * Achievements Page & Add-Achievement Form Logic
 * Fully Client-Side Filter, Search, Modal Population, and Mock Store Mutations
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

  // Confirm Delete Modal Action Listener
  const confirmDeleteBtn = document.getElementById('confirmDeleteBtn');
  if (confirmDeleteBtn) {
    confirmDeleteBtn.addEventListener('click', () => {
      if (pendingDeleteId) {
        MockStore.deleteAchievement(pendingDeleteId);
        closeModal('deleteModal');
        pendingDeleteId = null;
        renderAchievementsTable();
        showToast('Demo achievement record deleted successfully', 'success');
      }
    });
  }
}

function renderAchievementsTable() {
  const tableBody = document.getElementById('achievementsTableBody');
  if (!tableBody) return;

  const profile = MockStore.getFacultyProfile();
  let list = MockStore.getAchievements().filter(a => a.userId === profile.id);

  // Apply Search Keyword Filter
  const keyword = (document.getElementById('searchKeyword')?.value || '').toLowerCase().trim();
  if (keyword) {
    list = list.filter(a => 
      a.title.toLowerCase().includes(keyword) || 
      (a.journalName && a.journalName.toLowerCase().includes(keyword)) ||
      (a.description && a.description.toLowerCase().includes(keyword))
    );
  }

  // Apply Category Filter
  const selectedCat = document.getElementById('filterCategory')?.value;
  if (selectedCat) {
    list = list.filter(a => a.category === selectedCat);
  }

  // Apply Status Filter
  const selectedStatus = document.getElementById('filterStatus')?.value;
  if (selectedStatus) {
    list = list.filter(a => a.status === selectedStatus);
  }

  // Apply Year Filter
  const selectedYear = document.getElementById('filterYear')?.value;
  if (selectedYear) {
    list = list.filter(a => a.academicYear === selectedYear);
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

    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td data-label="Title & Details">
        <div class="table-title-cell">${escapeHtml(item.title)}</div>
        <div class="table-subtext">${item.journalName ? escapeHtml(item.journalName) : escapeHtml(item.categoryLabel)}</div>
      </td>
      <td data-label="Category">${escapeHtml(item.categoryLabel)}</td>
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

  // Attach dynamic row event listeners
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
 * 2. Add Achievement Form & Dynamic Category Extensions Controller
 */
function initializeAddAchievementForm() {
  const categorySelect = document.getElementById('categorySelect');
  const extensionSections = document.querySelectorAll('.fieldset-section');
  const form = document.getElementById('addAchievementForm');

  if (categorySelect) {
    categorySelect.addEventListener('change', (e) => {
      const selectedCategory = e.target.value;

      // Hide all dynamic extension fieldsets
      extensionSections.forEach(section => {
        section.style.display = 'none';
        // Disable inner inputs so they are not submitted when inactive
        section.querySelectorAll('input, select, textarea').forEach(input => input.disabled = true);
      });

      // Enable and show target category section
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
    form.addEventListener('submit', (e) => {
      e.preventDefault();

      const category = categorySelect.value;
      const title = document.getElementById('achievementTitle').value;
      const date = document.getElementById('achievementDate').value;
      const academicYear = document.getElementById('academicYear').value;

      if (!FormValidator.validateRequired(category) || !FormValidator.validateRequired(title) || !FormValidator.validateDate(date)) {
        showToast('Please fill out all required fields marked with *', 'error');
        return;
      }

      const categoryLabelMap = {
        publication: "Research Publication",
        patent: "Patent / Intellectual Property",
        research_grant: "Research Grant",
        workshop_fdp: "Workshop / FDP",
        award: "Award / Recognition"
      };

      const newRecord = {
        category: category.toUpperCase(),
        categoryLabel: categoryLabelMap[category] || category,
        title: title,
        achievementDate: date,
        academicYear: academicYear,
        description: document.getElementById('description')?.value || '',
        proofDocumentUrl: document.getElementById('proofUrl')?.value || ''
      };

      // Extract Category Specific Fields
      if (category === 'publication') {
        newRecord.journalName = document.getElementById('journalName')?.value || '';
        newRecord.indexing = document.getElementById('indexing')?.value || 'SCI';
        newRecord.impactFactor = document.getElementById('impactFactor')?.value || 0;
        newRecord.doi = document.getElementById('doi')?.value || '';
      }

      // Add to Demo Client Store
      MockStore.addAchievement(newRecord);

      showToast('Demo submission completed! Achievement added to demo state.', 'success');
      form.reset();
      
      setTimeout(() => {
        window.location.href = 'achievements.html';
      }, 1200);
    });
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

function showAchievementDetailsModal(id) {
  const item = MockStore.getAchievementById(id);
  if (!item) return;

  const modalBody = document.getElementById('viewModalContent');
  if (modalBody) {
    modalBody.innerHTML = `
      <p style="margin-bottom: 0.5rem;"><strong>Title:</strong> ${escapeHtml(item.title)}</p>
      <p style="margin-bottom: 0.5rem;"><strong>Category:</strong> ${escapeHtml(item.categoryLabel)}</p>
      <p style="margin-bottom: 0.5rem;"><strong>Academic Year:</strong> ${escapeHtml(item.academicYear)}</p>
      <p style="margin-bottom: 0.5rem;"><strong>Achievement Date:</strong> ${formatDate(item.achievementDate)}</p>
      <p style="margin-bottom: 0.5rem;"><strong>Status:</strong> <span class="badge badge-${item.status.toLowerCase()}">${item.status}</span></p>
      ${item.description ? `<p style="margin-bottom: 0.5rem;"><strong>Description:</strong> ${escapeHtml(item.description)}</p>` : ''}
      ${item.journalName ? `<p style="margin-bottom: 0.5rem;"><strong>Journal/Publisher:</strong> ${escapeHtml(item.journalName)}</p>` : ''}
      ${item.doi ? `<p style="margin-bottom: 0.5rem;"><strong>DOI:</strong> ${escapeHtml(item.doi)}</p>` : ''}
      ${item.proofDocumentUrl ? `<p style="margin-top: 0.75rem;"><strong>Proof Link:</strong> <a href="${escapeHtml(item.proofDocumentUrl)}" target="_blank" rel="noopener">View Supporting Certificate</a></p>` : ''}
    `;
    openModal('viewModal');
  }
}
