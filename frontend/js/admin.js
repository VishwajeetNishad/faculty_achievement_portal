/**
 * Admin / HOD Verification Queue, Achievement Search & Control Center Controller
 * Connected to Live Spring Boot Endpoints:
 *   GET  /api/achievements/search        (server-side search with pagination)
 *   GET  /api/achievements/export/csv    (CSV export)
 *   PATCH /api/achievements/{id}/verification
 *   GET  /api/achievements/{id}/proof
 *   GET  /api/dashboard/admin
 *   GET  /api/users
 *   GET  /api/departments
 *
 * Note: there is deliberately no /api/categories call — that endpoint does not
 * exist. See ADMIN_CATEGORY_OPTIONS below.
 */

let selectedReviewId  = null;
let adminCurrentPage  = 0;
const ADMIN_PAGE_SIZE = 15;

document.addEventListener('DOMContentLoaded', () => {
  // Admin Dashboard page
  if (document.getElementById('adminPendingCount') || document.getElementById('adminDeptComparisonBody')) {
    initializeAdminDashboard();
  }

  // Admin Achievement Search page (achievements.html)
  if (document.getElementById('adminQueueTableBody') && document.getElementById('adminSearchKeyword')) {
    initializeAdminAchievementSearch();
  } else if (document.getElementById('adminQueueTableBody')) {
    // Legacy: old achievements page without search bar — load pending queue
    initializeAdminDashboard();
  }

  // Faculty Roster page
  if (document.getElementById('facultyRosterTableBody')) {
    initializeFacultyRoster();
  }
});

async function initializeAdminDashboard() {
  await renderAdminStatsAndQueue();

  // Attach Approve / Reject Modal Event Handlers (Real Backend API Call)
  const approveBtn = document.getElementById('btnApproveRecord');
  const rejectBtn = document.getElementById('btnRejectRecord');

  if (approveBtn) {
    approveBtn.addEventListener('click', async () => {
      if (!selectedReviewId) return;

      const comment = (document.getElementById('verifyComment')?.value || '').trim() || 'Verified and approved by department reviewer.';
      
      approveBtn.disabled = true;
      approveBtn.textContent = 'Approving...';

      const res = await ApiClient.patch(`/achievements/${selectedReviewId}/verification`, {
        status: 'APPROVED',
        verificationComment: comment
      });

      if (res.success) {
        showToast('Achievement record APPROVED successfully in database.', 'success');
        closeModal('reviewModal');
        selectedReviewId = null;
        await renderAdminStatsAndQueue();
      } else {
        showToast(res.message || 'Failed to approve achievement', 'error');
      }

      approveBtn.disabled = false;
      approveBtn.textContent = 'Approve Achievement';
    });
  }

  if (rejectBtn) {
    rejectBtn.addEventListener('click', async () => {
      if (!selectedReviewId) return;

      const comment = (document.getElementById('verifyComment')?.value || '').trim();
      if (!comment) {
        showToast('Please enter a review comment explaining why this record is rejected.', 'error');
        return;
      }

      rejectBtn.disabled = true;
      rejectBtn.textContent = 'Rejecting...';

      const res = await ApiClient.patch(`/achievements/${selectedReviewId}/verification`, {
        status: 'REJECTED',
        verificationComment: comment
      });

      if (res.success) {
        showToast('Achievement record REJECTED with feedback.', 'warning');
        closeModal('reviewModal');
        selectedReviewId = null;
        await renderAdminStatsAndQueue();
      } else {
        showToast(res.message || 'Failed to reject achievement', 'error');
      }

      rejectBtn.disabled = false;
      rejectBtn.textContent = 'Reject Achievement';
    });
  }
}

async function renderAdminStatsAndQueue() {
  const tableBody = document.getElementById('adminQueueTableBody');
  if (tableBody) {
    tableBody.innerHTML = `<tr><td colspan="5" class="empty-state"><div class="spinner"></div><p style="margin-top:0.5rem;">Fetching institutional analytics from live API...</p></td></tr>`;
  }

  // 1. Fetch Real Admin Dashboard Analytics: GET /api/dashboard/admin
  const dashRes = await ApiClient.get('/dashboard/admin');

  if (dashRes.success && dashRes.data) {
    const data = dashRes.data;

    // Update Metric Cards
    const totalFacElem = document.getElementById('adminTotalFaculty');
    const pendingElem = document.getElementById('adminPendingCount');
    const verifiedElem = document.getElementById('adminVerifiedCount');
    const rejectedElem = document.getElementById('adminRejectedCount');

    if (totalFacElem) totalFacElem.textContent = data.totalFaculty;
    if (pendingElem) pendingElem.textContent = data.pendingCount;
    if (verifiedElem) verifiedElem.textContent = data.approvedCount;
    if (rejectedElem) rejectedElem.textContent = data.rejectedCount;

    // Render Department Comparison Table
    renderDepartmentComparisonTable(data.departmentComparison || []);

    // Render Distribution Bars
    renderDistributionBars('adminCategoryContainer', data.categoryDistribution, data.totalAchievements, '#002147');
    renderDistributionBars('adminYearContainer', data.academicYearDistribution, data.totalAchievements, '#F2A900');
  }

  // 2. Fetch PENDING records for verification queue
  const resPending = await ApiClient.get('/achievements/status/PENDING');
  const pendingItems = (resPending.success && Array.isArray(resPending.data)) ? resPending.data : [];

  // Render Verification Queue Table
  if (!tableBody) return;
  tableBody.innerHTML = '';

  if (pendingItems.length === 0) {
    tableBody.innerHTML = `
      <tr>
        <td colspan="5" class="empty-state">
          <div class="empty-state-title">Verification Queue Empty</div>
          <p class="empty-state-text">All submitted faculty achievements have been reviewed.</p>
        </td>
      </tr>
    `;
    return;
  }

  pendingItems.forEach(item => {
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td data-label="Faculty & Dept">
        <div class="table-title-cell">${escapeHtml(item.facultyName)}</div>
        <div class="table-subtext">${escapeHtml(item.departmentName || item.departmentCode || 'Faculty')}</div>
      </td>
      <td data-label="Title & Category">
        <div class="table-title-cell">${escapeHtml(item.title)}</div>
        <div class="table-subtext">${escapeHtml(item.categoryName || item.categoryCode)}</div>
      </td>
      <td data-label="Submitted Date">${formatDate(item.achievementDate)}</td>
      <td data-label="Status">
        <span class="badge badge-pending"><span class="badge-symbol">●</span> PENDING</span>
      </td>
      <td data-label="Action">
        <button class="btn btn-primary btn-sm review-record-btn" data-id="${item.id}">Review & Verify</button>
      </td>
    `;
    tableBody.appendChild(tr);
  });

  tableBody.querySelectorAll('.review-record-btn').forEach(btn => {
    btn.addEventListener('click', async () => {
      selectedReviewId = btn.getAttribute('data-id');
      
      const itemRes = await ApiClient.get(`/achievements/${selectedReviewId}`);
      if (itemRes.success && itemRes.data) {
        const item = itemRes.data;
        const reviewContent = document.getElementById('reviewModalContent');
        if (reviewContent) {
          reviewContent.innerHTML = `
            <p style="margin-bottom: 0.5rem;"><strong>Faculty Member:</strong> ${escapeHtml(item.facultyName)} (${escapeHtml(item.departmentName || item.departmentCode)})</p>
            <p style="margin-bottom: 0.5rem;"><strong>Employee ID:</strong> ${escapeHtml(item.employeeId)}</p>
            <p style="margin-bottom: 0.5rem;"><strong>Title:</strong> ${escapeHtml(item.title)}</p>
            <p style="margin-bottom: 0.5rem;"><strong>Category:</strong> ${escapeHtml(item.categoryName || item.categoryCode)}</p>
            <p style="margin-bottom: 0.5rem;"><strong>Academic Year:</strong> ${escapeHtml(item.academicYear)}</p>
            <p style="margin-bottom: 0.5rem;"><strong>Achievement Date:</strong> ${formatDate(item.achievementDate)}</p>
            ${item.description ? `<p style="margin-bottom: 0.5rem;"><strong>Description:</strong> ${escapeHtml(item.description)}</p>` : ''}
            ${item.proofDocumentUrl ? `<p style="margin-top: 0.75rem;"><button class="btn btn-outline btn-sm" onclick="openProtectedProofPdf(${item.id})">📄 View Protected Proof PDF</button></p>` : '<p style="margin-top: 0.5rem; color:#94A3B8;">No Proof Document Attached</p>'}
          `;
        }
        openModal('reviewModal');
      } else {
        showToast(itemRes.message || 'Error fetching achievement details', 'error');
      }
    });
  });
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

// ─────────────────────────────────────────────────────────────────────────────
// ADMIN ACHIEVEMENT SEARCH (achievements.html)
// Server-side GET /api/achievements/search with full institutional scope
// ─────────────────────────────────────────────────────────────────────────────

async function initializeAdminAchievementSearch() {
  // Load category and department dropdowns from backend
  await loadAdminCategoryOptions();
  await loadAdminDepartmentOptions();
  loadAdminYearOptions();

  // Initial search
  await runAdminSearch(0);

  // Buttons
  document.getElementById('adminApplyFiltersBtn')?.addEventListener('click', () => runAdminSearch(0));
  document.getElementById('adminClearFiltersBtn')?.addEventListener('click', () => {
    document.getElementById('adminSearchKeyword').value  = '';
    document.getElementById('adminFilterStatus').value   = '';
    document.getElementById('adminFilterCategory').value = '';
    document.getElementById('adminFilterDept').value     = '';
    document.getElementById('adminFilterYear').value     = '';
    document.getElementById('adminFilterFromDate').value = '';
    document.getElementById('adminFilterToDate').value   = '';
    document.getElementById('adminFilterSort').value     = 'createdAt_desc';
    runAdminSearch(0);
    showToast('Filters cleared', 'info');
  });
  document.getElementById('adminSearchKeyword')?.addEventListener('keydown', e => {
    if (e.key === 'Enter') runAdminSearch(0);
  });
  document.getElementById('adminExportCsvBtn')?.addEventListener('click', adminExportCsv);

  // Approve / Reject modal handlers
  document.getElementById('btnApproveRecord')?.addEventListener('click', async () => {
    if (!selectedReviewId) return;
    const btn = document.getElementById('btnApproveRecord');
    btn.disabled = true; btn.textContent = 'Approving...';
    const comment = (document.getElementById('verifyComment')?.value || '').trim() || 'Verified and approved.';
    const res = await ApiClient.patch(`/achievements/${selectedReviewId}/verification`, { status: 'APPROVED', verificationComment: comment });
    if (res.success) {
      showToast('Achievement APPROVED successfully.', 'success');
      closeModal('reviewModal');
      selectedReviewId = null;
      await runAdminSearch(adminCurrentPage);
    } else {
      showToast(res.message || 'Failed to approve', 'error');
    }
    btn.disabled = false; btn.textContent = 'Approve Achievement';
  });

  document.getElementById('btnRejectRecord')?.addEventListener('click', async () => {
    if (!selectedReviewId) return;
    const comment = (document.getElementById('verifyComment')?.value || '').trim();
    if (!comment) { showToast('Rejection requires a comment.', 'error'); return; }
    const btn = document.getElementById('btnRejectRecord');
    btn.disabled = true; btn.textContent = 'Rejecting...';
    const res = await ApiClient.patch(`/achievements/${selectedReviewId}/verification`, { status: 'REJECTED', verificationComment: comment });
    if (res.success) {
      showToast('Achievement REJECTED with feedback.', 'warning');
      closeModal('reviewModal');
      selectedReviewId = null;
      await runAdminSearch(adminCurrentPage);
    } else {
      showToast(res.message || 'Failed to reject', 'error');
    }
    btn.disabled = false; btn.textContent = 'Reject Achievement';
  });
}

async function runAdminSearch(page) {
  adminCurrentPage = page;
  const tableBody = document.getElementById('adminQueueTableBody');
  if (!tableBody) return;

  tableBody.innerHTML = `<tr><td colspan="6" class="empty-state"><div class="spinner"></div><p style="margin-top:0.5rem;">Searching institutional records...</p></td></tr>`;

  const applyBtn = document.getElementById('adminApplyFiltersBtn');
  if (applyBtn) { applyBtn.disabled = true; applyBtn.textContent = 'Searching...'; }

  const params = buildAdminSearchParams(page);
  const res = await ApiClient.get('/achievements/search?' + params.toString());

  if (applyBtn) { applyBtn.disabled = false; applyBtn.textContent = 'Search'; }

  if (!res.success) {
    let msg = res.message || 'Search failed';
    if (res.status === 400) msg = 'Invalid search parameters: ' + msg;
    if (res.status === 403) msg = 'Institutional search requires admin privileges.';
    tableBody.innerHTML = `<tr><td colspan="6" class="empty-state"><div class="empty-state-title" style="color:var(--danger-color);">Error</div><p class="empty-state-text">${escapeHtml(msg)}</p></td></tr>`;
    hideAdminPagination();
    return;
  }

  const data = res.data;
  const list = Array.isArray(data.content) ? data.content : [];

  tableBody.innerHTML = '';

  if (list.length === 0) {
    tableBody.innerHTML = `<tr><td colspan="6" class="empty-state"><div class="empty-state-title">No Achievements Found</div><p class="empty-state-text">No records match the selected filters.</p></td></tr>`;
    hideAdminPagination();
    return;
  }

  list.forEach(item => {
    const statusClass = (item.status || 'PENDING').toLowerCase();
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td data-label="Faculty &amp; Dept">
        <div class="table-title-cell">${escapeHtml(item.facultyName || '—')}</div>
        <div class="table-subtext">${escapeHtml(item.departmentName || item.departmentCode || 'Faculty')}</div>
      </td>
      <td data-label="Title &amp; Category">
        <div class="table-title-cell">${escapeHtml(item.title)}</div>
        <div class="table-subtext">${escapeHtml(item.categoryName || item.categoryCode || '')}</div>
      </td>
      <td data-label="Academic Year">${escapeHtml(item.academicYear || '—')}</td>
      <td data-label="Submitted">${formatDate(item.createdAt || item.achievementDate)}</td>
      <td data-label="Status"><span class="badge badge-${statusClass}">${item.status}</span></td>
      <td data-label="Action">
        <button class="btn btn-primary btn-sm review-record-btn" data-id="${item.id}">Review</button>
      </td>
    `;
    tableBody.appendChild(tr);
  });

  tableBody.querySelectorAll('.review-record-btn').forEach(btn => {
    btn.addEventListener('click', async () => {
      selectedReviewId = btn.getAttribute('data-id');
      const itemRes = await ApiClient.get(`/achievements/${selectedReviewId}`);
      if (itemRes.success && itemRes.data) {
        const item = itemRes.data;
        const reviewContent = document.getElementById('reviewModalContent');
        if (reviewContent) {
          reviewContent.innerHTML = `
            <p style="margin-bottom:0.5rem;"><strong>Faculty:</strong> ${escapeHtml(item.facultyName)} (${escapeHtml(item.departmentName || item.departmentCode)})</p>
            <p style="margin-bottom:0.5rem;"><strong>Employee ID:</strong> ${escapeHtml(item.employeeId)}</p>
            <p style="margin-bottom:0.5rem;"><strong>Title:</strong> ${escapeHtml(item.title)}</p>
            <p style="margin-bottom:0.5rem;"><strong>Category:</strong> ${escapeHtml(item.categoryName || item.categoryCode)}</p>
            <p style="margin-bottom:0.5rem;"><strong>Academic Year:</strong> ${escapeHtml(item.academicYear)}</p>
            <p style="margin-bottom:0.5rem;"><strong>Status:</strong> <span class="badge badge-${item.status.toLowerCase()}">${item.status}</span></p>
            <p style="margin-bottom:0.5rem;"><strong>Achievement Date:</strong> ${formatDate(item.achievementDate)}</p>
            ${item.description ? `<p style="margin-bottom:0.5rem;"><strong>Description:</strong> ${escapeHtml(item.description)}</p>` : ''}
            ${item.proofDocumentUrl ? `<p style="margin-top:0.75rem;"><button class="btn btn-outline btn-sm" onclick="openProtectedProofPdf(${item.id})">📄 View Proof PDF</button></p>` : '<p style="margin-top:0.5rem; color:#94A3B8;">No Proof Document Attached</p>'}
          `;
        }
        document.getElementById('verifyComment').value = '';
        openModal('reviewModal');
      } else {
        showToast(itemRes.message || 'Error fetching achievement details', 'error');
      }
    });
  });

  renderAdminPagination(data);
}

function buildAdminSearchParams(page) {
  const keyword  = document.getElementById('adminSearchKeyword')?.value?.trim() || '';
  const status   = document.getElementById('adminFilterStatus')?.value || '';
  const catCode  = document.getElementById('adminFilterCategory')?.value || '';
  const deptId   = document.getElementById('adminFilterDept')?.value || '';
  const year     = document.getElementById('adminFilterYear')?.value || '';
  const fromDate = document.getElementById('adminFilterFromDate')?.value || '';
  const toDate   = document.getElementById('adminFilterToDate')?.value || '';
  const sortVal  = document.getElementById('adminFilterSort')?.value || 'createdAt_desc';
  const [sortBy, sortDir] = sortVal.split('_');

  const params = new URLSearchParams();
  if (keyword)  params.set('keyword', keyword);
  if (status)   params.set('status', status);
  if (catCode)  params.set('categoryCode', catCode);
  // departmentId is accepted as an additional FILTER on the server;
  // it cannot bypass ADMIN scope — auth scope is always derived from JWT first.
  if (deptId)   params.set('departmentId', deptId);
  if (year)     params.set('academicYear', year);
  if (fromDate) params.set('fromDate', fromDate);
  if (toDate)   params.set('toDate', toDate);
  params.set('sortBy', sortBy || 'createdAt');
  params.set('sortDir', sortDir || 'desc');
  params.set('page', String(page));
  params.set('size', String(ADMIN_PAGE_SIZE));
  return params;
}

function renderAdminPagination(data) {
  const bar      = document.getElementById('adminPaginationBar');
  const info     = document.getElementById('adminPaginationInfo');
  const controls = document.getElementById('adminPaginationControls');
  if (!bar || !info || !controls) return;

  const { page, size, totalElements, totalPages, first, last } = data;
  if (totalElements === 0) { hideAdminPagination(); return; }

  bar.style.display = 'flex';
  const start = page * size + 1;
  const end   = Math.min(page * size + size, totalElements);
  info.textContent = `Showing ${start}–${end} of ${totalElements} results`;

  controls.innerHTML = '';
  const prevBtn = document.createElement('button');
  prevBtn.textContent = '‹ Prev'; prevBtn.disabled = first;
  prevBtn.addEventListener('click', () => runAdminSearch(page - 1));
  controls.appendChild(prevBtn);

  const startPage = Math.max(0, page - 2);
  const endPage   = Math.min(totalPages - 1, page + 2);
  for (let i = startPage; i <= endPage; i++) {
    const btn = document.createElement('button');
    btn.textContent = String(i + 1);
    if (i === page) btn.classList.add('active');
    btn.addEventListener('click', () => runAdminSearch(i));
    controls.appendChild(btn);
  }

  const nextBtn = document.createElement('button');
  nextBtn.textContent = 'Next ›'; nextBtn.disabled = last;
  nextBtn.addEventListener('click', () => runAdminSearch(page + 1));
  controls.appendChild(nextBtn);
}

function hideAdminPagination() {
  const bar = document.getElementById('adminPaginationBar');
  if (bar) bar.style.display = 'none';
}

// The portal has no GET /api/categories endpoint, so the seeded category set is
// the source of truth — the same approach hod-common.js already documents.
//
// These are CODES, not database ids. The search endpoint accepts either
// (AchievementController: categoryId or categoryCode), and a code cannot drift:
// the previous version of this file guessed the ids 1..5 and would have filtered
// by the wrong category had the seed data ever been inserted in another order.
const ADMIN_CATEGORY_OPTIONS = [
  { code: 'PUBLICATION',    label: 'Research Publication' },
  { code: 'PATENT',         label: 'Patent / Intellectual Property' },
  { code: 'RESEARCH_GRANT', label: 'Research & Consultancy Grant' },
  { code: 'WORKSHOP_FDP',   label: 'Workshop / FDP / Certification' },
  { code: 'AWARD',          label: 'Award & Recognition' }
];

async function loadAdminCategoryOptions() {
  const sel = document.getElementById('adminFilterCategory');
  if (!sel) return;
  ADMIN_CATEGORY_OPTIONS.forEach(c => {
    const opt = document.createElement('option');
    opt.value = c.code;
    opt.textContent = c.label;
    sel.appendChild(opt);
  });
}

async function loadAdminDepartmentOptions() {
  const sel = document.getElementById('adminFilterDept');
  if (!sel) return;
  const res = await ApiClient.get('/departments');
  if (res.success && Array.isArray(res.data)) {
    res.data.forEach(d => {
      const opt = document.createElement('option');
      opt.value = d.id; opt.textContent = d.name;
      sel.appendChild(opt);
    });
  }
}

function loadAdminYearOptions() {
  const sel = document.getElementById('adminFilterYear');
  if (!sel) return;
  const y = new Date().getFullYear();
  for (let i = y; i >= y - 6; i--) {
    const opt = document.createElement('option');
    opt.value = `${i}-${i+1}`; opt.textContent = `${i}-${i+1}`;
    sel.appendChild(opt);
  }
}

async function adminExportCsv() {
  showToast('Preparing institutional CSV export…', 'info');
  const params = buildAdminSearchParams(0);
  params.delete('page'); params.delete('size'); params.delete('sortBy'); params.delete('sortDir');
  params.delete('adminDeptFilter');

  const res = await ApiClient.downloadBlob('/achievements/export/csv?' + params.toString());
  if (res.success && res.objectUrl) {
    const a = document.createElement('a');
    a.href = res.objectUrl;
    a.download = 'institutional_achievements_' + new Date().toISOString().slice(0,10) + '.csv';
    document.body.appendChild(a); a.click(); document.body.removeChild(a);
    showToast('Institutional CSV exported!', 'success');
  } else {
    showToast(res.message || 'Export failed', 'error');
  }
}

let allDepartments = [];
// Declared up front so the search and filter boxes still work when the roster
// itself failed to load (for example a 403). Without this the variable only
// exists after a successful fetch, and typing in the search box throws.
let allFacultyData = [];

async function initializeFacultyRoster() {
  const tableBody = document.getElementById('facultyRosterTableBody');
  const searchInput = document.getElementById('searchFaculty');
  const deptFilter = document.getElementById('departmentFilter');
  const roleFilter = document.getElementById('roleFilter');
  const statusFilter = document.getElementById('statusFilter');

  if (!tableBody) return;

  // Show loading state
  tableBody.innerHTML = `<tr><td colspan="5" class="empty-state"><div class="spinner"></div><p style="margin-top:0.5rem;">Loading faculty roster from database...</p></td></tr>`;

  // The Actions column asks can('MANAGE_PERMISSIONS'), so make sure the real
  // permission list has arrived before the rows are drawn.
  if (typeof ensurePermissionsLoaded === 'function') {
    try { await ensurePermissionsLoaded(); } catch (e) { /* fall back to the cached list */ }
  }

  // Hide the "Add User" button unless this account can create somebody.
  if (typeof applyPermissionVisibility === 'function') applyPermissionVisibility(document);

  // Load departments for filter dropdown
  try {
    const deptRes = await ApiClient.get('/departments');
    if (deptRes.success && Array.isArray(deptRes.data)) {
      allDepartments = deptRes.data;
      if (deptFilter) {
        deptFilter.innerHTML = '<option value="">All Departments</option>';
        allDepartments.forEach(d => {
          const opt = document.createElement('option');
          opt.value = d.name;
          opt.textContent = d.name;
          deptFilter.appendChild(opt);
        });
      }
    }
  } catch (e) {
    console.error('Error loading departments:', e);
  }

  // Load faculty roster from real API
  try {
    const res = await ApiClient.get('/users');
    if (res.success && Array.isArray(res.data)) {
      allFacultyData = res.data;
      renderFacultyTable(allFacultyData);
      updateRosterCounts(allFacultyData);
    } else if (res.status === 403) {
      tableBody.innerHTML = `<tr><td colspan="5" class="empty-state"><div class="empty-state-title">Access Denied</div><p class="empty-state-text">You do not have admin privileges to view the faculty roster.</p></td></tr>`;
      showToast('Admin privileges required to view faculty roster.', 'error');
    } else {
      tableBody.innerHTML = `<tr><td colspan="5" class="empty-state"><div class="empty-state-title">Error</div><p class="empty-state-text">${escapeHtml(res.message || 'Failed to load faculty data')}</p></td></tr>`;
    }
  } catch (error) {
    console.error('Error loading faculty:', error);
    tableBody.innerHTML = `<tr><td colspan="5" class="empty-state"><div class="empty-state-title">Connection Error</div><p class="empty-state-text">Unable to connect to the backend server.</p></td></tr>`;
  }

  // Attach search and filter event listeners
  if (searchInput) searchInput.addEventListener('input', filterAndRenderFaculty);
  if (deptFilter) deptFilter.addEventListener('change', filterAndRenderFaculty);
  if (roleFilter) roleFilter.addEventListener('change', filterAndRenderFaculty);
  if (statusFilter) statusFilter.addEventListener('change', filterAndRenderFaculty);

  wireStatusModal();
}

/**
 * The tallies under the filter bar. "Showing" is recalculated on every filter
 * change; the totals always describe the whole roster.
 */
function updateRosterCounts(filtered) {
  const shownEl = document.getElementById('shownFacultyCount');
  const totalEl = document.getElementById('totalFacultyCount');
  const activeEl = document.getElementById('activeFacultyCount');
  const inactiveEl = document.getElementById('inactiveFacultyCount');

  if (shownEl) shownEl.textContent = (filtered || []).length;
  if (totalEl) totalEl.textContent = allFacultyData.length;
  if (activeEl) activeEl.textContent = allFacultyData.filter(f => f.status === 'ACTIVE').length;
  if (inactiveEl) inactiveEl.textContent = allFacultyData.filter(f => f.status !== 'ACTIVE').length;
}

function filterAndRenderFaculty() {
  const keyword = (document.getElementById('searchFaculty')?.value || '').toLowerCase().trim();
  const deptValue = document.getElementById('departmentFilter')?.value || '';
  const roleValue = document.getElementById('roleFilter')?.value || '';
  const statusValue = document.getElementById('statusFilter')?.value || '';

  let filtered = allFacultyData;

  if (keyword) {
    filtered = filtered.filter(f =>
      (f.fullName || '').toLowerCase().includes(keyword) ||
      (f.employeeId || '').toLowerCase().includes(keyword) ||
      (f.email || '').toLowerCase().includes(keyword)
    );
  }

  if (deptValue) {
    filtered = filtered.filter(f => f.departmentName === deptValue);
  }

  if (roleValue) {
    filtered = filtered.filter(f => normaliseRoleName(f.role) === roleValue);
  }

  if (statusValue) {
    filtered = filtered.filter(f => f.status === statusValue);
  }

  renderFacultyTable(filtered);
  updateRosterCounts(filtered);
}

/** The backend accepts the role with or without the ROLE_ prefix; compare with it. */
function normaliseRoleName(role) {
  if (!role) return '';
  const upper = String(role).toUpperCase();
  return upper.startsWith('ROLE_') ? upper : 'ROLE_' + upper;
}

const ROSTER_ROLE_LABEL = {
  ROLE_FACULTY: 'Faculty',
  ROLE_HOD: 'Head of Dept.',
  ROLE_ADMIN: 'Administrator'
};

function renderFacultyTable(roster) {
  const tableBody = document.getElementById('facultyRosterTableBody');
  if (!tableBody) return;

  tableBody.innerHTML = '';

  if (roster.length === 0) {
    tableBody.innerHTML = `<tr><td colspan="5" class="empty-state"><div class="empty-state-title">No Faculty Found</div><p class="empty-state-text">No faculty members match your search criteria.</p></td></tr>`;
    return;
  }

  // Read once rather than per row. These decide which buttons are OFFERED; the
  // API re-checks every permission from the database on each request, so hiding
  // a button is a courtesy to the user, never the security boundary.
  const canManagePermissions = typeof can === 'function' ? can('MANAGE_PERMISSIONS') : false;
  const canManageStatus = typeof can === 'function' ? can('MANAGE_USER_STATUS') : false;
  const canEditFaculty = typeof can === 'function' ? can('EDIT_FACULTY') : false;
  const canEditHod = typeof can === 'function' ? can('EDIT_HOD') : false;

  let sessionUserId = '';
  try {
    sessionUserId = String(JSON.parse(sessionStorage.getItem('currentUser') || '{}').userId || '');
  } catch (e) { /* not signed in with a parsable session */ }

  const sessionIsAdmin = (() => {
    try {
      const role = normaliseRoleName(JSON.parse(sessionStorage.getItem('currentUser') || '{}').role);
      return role === 'ROLE_ADMIN';
    } catch (e) { return false; }
  })();

  roster.forEach(f => {
    const statusBadge = f.status === 'ACTIVE' ? 'badge-approved' :
                        f.status === 'INACTIVE' ? 'badge-pending' : 'badge-rejected';
    const statusSymbol = f.status === 'ACTIVE' ? '✓' : f.status === 'INACTIVE' ? '○' : '✕';

    const roleValue = normaliseRoleName(f.role);
    const isSelf = sessionUserId && String(f.id) === sessionUserId;

    // Mirrors requirePermissionToEdit() in UserManagementServiceImpl: an
    // Administrator account can only be edited by an administrator, and there is
    // deliberately no EDIT_ADMIN permission to hand out.
    const canEditThisUser = roleValue === 'ROLE_ADMIN' ? sessionIsAdmin
                          : roleValue === 'ROLE_HOD' ? canEditHod
                          : canEditFaculty;

    const actions = [];

    if (canEditThisUser) {
      actions.push(`<a class="btn btn-outline btn-sm" href="add-user.html?userId=${encodeURIComponent(f.id)}">Edit</a>`);
    }

    // Nobody may change their own status — the server refuses it, so the button
    // is not offered either.
    if (canManageStatus && !isSelf) {
      const goingActive = f.status !== 'ACTIVE';
      actions.push(
        `<button type="button" class="btn ${goingActive ? 'btn-outline' : 'btn-danger'} btn-sm js-status-btn"` +
        ` data-user-id="${escapeHtml(String(f.id))}"` +
        ` data-user-name="${escapeHtml(f.fullName || '')}"` +
        ` data-current-status="${escapeHtml(f.status || '')}">` +
        `${goingActive ? 'Reactivate' : 'Deactivate'}</button>`
      );
    }

    if (canManagePermissions) {
      actions.push(`<a class="btn btn-ghost btn-sm" href="user-permissions.html?userId=${encodeURIComponent(f.id)}">Permissions</a>`);
    }

    const actionsHtml = actions.length
      ? `<div class="action-btn-group">${actions.join('')}</div>`
      : '<span class="table-subtext">No actions available</span>';

    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td data-label="Name & Employee ID">
        <div class="table-title-cell">${escapeHtml(f.fullName)}${isSelf ? ' <span class="table-subtext">(you)</span>' : ''}</div>
        <div class="table-subtext">${escapeHtml(f.employeeId)} &middot; ${escapeHtml(f.email)}</div>
      </td>
      <td data-label="Department & Designation">
        <div>${escapeHtml(f.departmentName || '—')}</div>
        <div class="table-subtext">${escapeHtml(f.designation || '')}</div>
      </td>
      <td data-label="Role">${escapeHtml(ROSTER_ROLE_LABEL[roleValue] || f.role || '—')}</td>
      <td data-label="Status">
        <span class="badge ${statusBadge}"><span class="badge-symbol">${statusSymbol}</span> ${escapeHtml(f.status || '')}</span>
      </td>
      <td data-label="Actions">${actionsHtml}</td>
    `;
    tableBody.appendChild(tr);
  });
}

// ─── Activate / deactivate ──────────────────────────────────────────────────

// Which account the status modal is currently about.
let statusModalUser = null;

function wireStatusModal() {
  const tableBody = document.getElementById('facultyRosterTableBody');
  const confirmBtn = document.getElementById('statusModalConfirmBtn');
  if (!tableBody || !confirmBtn) return;

  // One delegated listener, so it keeps working after every re-render.
  tableBody.addEventListener('click', event => {
    const btn = event.target.closest('.js-status-btn');
    if (!btn) return;
    openStatusModal({
      id: btn.getAttribute('data-user-id'),
      name: btn.getAttribute('data-user-name'),
      status: btn.getAttribute('data-current-status')
    });
  });

  confirmBtn.addEventListener('click', submitStatusChange);
}

function openStatusModal(user) {
  statusModalUser = user;

  const goingActive = user.status !== 'ACTIVE';
  const select = document.getElementById('statusModalTarget');
  const reason = document.getElementById('statusModalReason');

  select.value = goingActive ? 'ACTIVE' : 'INACTIVE';
  reason.value = '';

  document.getElementById('statusModalMessage').innerHTML = goingActive
    ? `<strong>${escapeHtml(user.name)}</strong> currently cannot sign in. Setting the account back to Active restores their access immediately.`
    : `<strong>${escapeHtml(user.name)}</strong> can sign in right now. Deactivating blocks them from their very next request onwards, even though their current session has not expired. Nothing they have submitted is deleted.`;

  document.getElementById('statusModalConfirmBtn').textContent = goingActive ? 'Reactivate Account' : 'Deactivate Account';

  openModal('statusModal');
}

async function submitStatusChange() {
  if (!statusModalUser) return;

  const confirmBtn = document.getElementById('statusModalConfirmBtn');
  const status = document.getElementById('statusModalTarget').value;
  const reason = document.getElementById('statusModalReason').value.trim();

  const body = { status };
  if (reason) body.reason = reason;

  const originalLabel = confirmBtn.textContent;
  confirmBtn.disabled = true;
  confirmBtn.textContent = 'Saving…';

  const res = await ApiClient.patch(`/users/${encodeURIComponent(statusModalUser.id)}/status`, body);

  confirmBtn.disabled = false;
  confirmBtn.textContent = originalLabel;

  if (res.success) {
    closeModal('statusModal');
    showToast(
      status === 'ACTIVE'
        ? `${statusModalUser.name} can sign in again.`
        : `${statusModalUser.name} can no longer sign in.`,
      status === 'ACTIVE' ? 'success' : 'warning'
    );

    // Update the row in place from the server's own response, so the table shows
    // what was actually saved rather than what was requested.
    const index = allFacultyData.findIndex(f => String(f.id) === String(statusModalUser.id));
    if (index !== -1 && res.data) allFacultyData[index] = res.data;
    filterAndRenderFaculty();
    statusModalUser = null;
    return;
  }

  // 409 is the last-active-administrator guard. It is a refusal, not a failure,
  // so it is worth stating plainly instead of showing a generic error.
  if (res.status === 409) {
    showToast(res.message || 'That change would leave the portal with no active administrator.', 'error');
  } else if (res.status === 403) {
    showToast(res.message || 'You do not have permission to change this account’s status.', 'error');
  } else {
    showToast(res.message || 'The status could not be changed.', 'error');
  }
}

function renderDepartmentComparisonTable(depts) {
  const tbody = document.getElementById('adminDeptComparisonBody');
  if (!tbody) return;

  tbody.innerHTML = '';

  if (depts.length === 0) {
    tbody.innerHTML = `<tr><td colspan="6" class="empty-state"><div class="empty-state-title">No Department Data</div><p class="empty-state-text">No departments recorded in the system.</p></td></tr>`;
    return;
  }

  depts.forEach(d => {
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td data-label="Department">
        <div class="table-title-cell">${escapeHtml(d.departmentName)}</div>
        <div class="table-subtext">${escapeHtml(d.departmentCode)}</div>
      </td>
      <td data-label="Faculty Count"><strong>${d.facultyCount}</strong></td>
      <td data-label="Total Submissions"><strong>${d.totalAchievements}</strong></td>
      <td data-label="Approved"><span class="badge badge-approved">✓ ${d.approvedCount}</span></td>
      <td data-label="Pending"><span class="badge badge-pending">● ${d.pendingCount}</span></td>
      <td data-label="Rejected"><span class="badge badge-rejected">! ${d.rejectedCount}</span></td>
    `;
    tbody.appendChild(tr);
  });
}

function renderDistributionBars(containerId, distMap, total, barColor) {
  const container = document.getElementById(containerId);
  if (!container) return;

  if (!distMap || Object.keys(distMap).length === 0) {
    container.innerHTML = `<div class="empty-state" style="padding: 1rem;"><p class="empty-state-text">No analytics data recorded yet.</p></div>`;
    return;
  }

  let html = '<div style="display: flex; flex-direction: column; gap: 0.85rem;">';
  for (const [key, count] of Object.entries(distMap)) {
    const percentage = total > 0 ? Math.round((count / total) * 100) : 0;
    html += `
      <div>
        <div style="display: flex; justify-content: space-between; font-size: 0.85rem; font-weight: 500; margin-bottom: 0.25rem;">
          <span style="color: #1E293B;">${escapeHtml(key)}</span>
          <span style="color: #64748B;">${count} (${percentage}%)</span>
        </div>
        <div style="width: 100%; height: 8px; background-color: #E2E8F0; border-radius: 4px; overflow: hidden;">
          <div style="width: ${percentage}%; height: 100%; background-color: ${barColor}; border-radius: 4px; transition: width 0.4s ease;"></div>
        </div>
      </div>
    `;
  }
  html += '</div>';

  container.innerHTML = html;
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
