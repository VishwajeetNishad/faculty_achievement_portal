/**
 * Admin Dashboard & Verification Queue Controller (Client-Side Demo State)
 */

let selectedReviewId = null;

document.addEventListener('DOMContentLoaded', () => {
  if (document.getElementById('adminStatsContainer') || document.getElementById('adminQueueTableBody')) {
    initializeAdminDashboard();
  }

  if (document.getElementById('facultyRosterTableBody')) {
    initializeFacultyRoster();
  }
});

function initializeAdminDashboard() {
  renderAdminStats();
  renderAdminQueueTable();

  // Attach Approve / Reject Modal Event Handlers
  const approveBtn = document.getElementById('btnApproveRecord');
  const rejectBtn = document.getElementById('btnRejectRecord');

  if (approveBtn) {
    approveBtn.addEventListener('click', () => {
      if (selectedReviewId) {
        const comment = document.getElementById('verifyComment')?.value || 'Verified and approved.';
        MockStore.updateAchievementStatus(selectedReviewId, 'APPROVED', comment);
        closeModal('reviewModal');
        selectedReviewId = null;
        renderAdminStats();
        renderAdminQueueTable();
        showToast('Demo Verification Action: Record APPROVED', 'success');
      }
    });
  }

  if (rejectBtn) {
    rejectBtn.addEventListener('click', () => {
      if (selectedReviewId) {
        const comment = document.getElementById('verifyComment')?.value || 'Rejected due to incomplete documentation.';
        MockStore.updateAchievementStatus(selectedReviewId, 'REJECTED', comment);
        closeModal('reviewModal');
        selectedReviewId = null;
        renderAdminStats();
        renderAdminQueueTable();
        showToast('Demo Verification Action: Record REJECTED', 'error');
      }
    });
  }
}

function renderAdminStats() {
  const achievements = MockStore.getAchievements();
  const roster = MockStore.getFacultyRoster();

  const totalFacultyElem = document.getElementById('adminTotalFaculty');
  const pendingElem = document.getElementById('adminPendingCount');
  const verifiedElem = document.getElementById('adminVerifiedCount');

  if (totalFacultyElem) totalFacultyElem.textContent = roster.length;
  if (pendingElem) pendingElem.textContent = achievements.filter(a => a.status === 'PENDING').length;
  if (verifiedElem) verifiedElem.textContent = achievements.filter(a => a.status === 'APPROVED').length;
}

function renderAdminQueueTable() {
  const tableBody = document.getElementById('adminQueueTableBody');
  if (!tableBody) return;

  const pendingItems = MockStore.getAchievements().filter(a => a.status === 'PENDING');
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
        <div class="table-subtext">${escapeHtml(item.department)} Department</div>
      </td>
      <td data-label="Title & Category">
        <div class="table-title-cell">${escapeHtml(item.title)}</div>
        <div class="table-subtext">${escapeHtml(item.categoryLabel)}</div>
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
    btn.addEventListener('click', () => {
      selectedReviewId = btn.getAttribute('data-id');
      const item = MockStore.getAchievementById(selectedReviewId);
      if (item) {
        const reviewContent = document.getElementById('reviewModalContent');
        if (reviewContent) {
          reviewContent.innerHTML = `
            <p style="margin-bottom: 0.5rem;"><strong>Faculty Member:</strong> ${escapeHtml(item.facultyName)} (${escapeHtml(item.department)})</p>
            <p style="margin-bottom: 0.5rem;"><strong>Title:</strong> ${escapeHtml(item.title)}</p>
            <p style="margin-bottom: 0.5rem;"><strong>Category:</strong> ${escapeHtml(item.categoryLabel)}</p>
            <p style="margin-bottom: 0.5rem;"><strong>Submission Date:</strong> ${formatDate(item.achievementDate)}</p>
            ${item.description ? `<p style="margin-bottom: 0.5rem;"><strong>Description:</strong> ${escapeHtml(item.description)}</p>` : ''}
            ${item.proofDocumentUrl ? `<p style="margin-top: 0.75rem;"><strong>Document Proof:</strong> <a href="${escapeHtml(item.proofDocumentUrl)}" target="_blank">Open Proof Link</a></p>` : ''}
          `;
        }
        openModal('reviewModal');
      }
    });
  });
}

function initializeFacultyRoster() {
  const tableBody = document.getElementById('facultyRosterTableBody');
  const searchInput = document.getElementById('searchFaculty');

  if (!tableBody) return;

  const renderRoster = () => {
    let roster = MockStore.getFacultyRoster();
    const keyword = (searchInput?.value || '').toLowerCase().trim();

    if (keyword) {
      roster = roster.filter(f => f.name.toLowerCase().includes(keyword) || f.employeeId.toLowerCase().includes(keyword) || f.email.toLowerCase().includes(keyword));
    }

    tableBody.innerHTML = '';
    roster.forEach(f => {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td data-label="Employee ID & Name">
          <div class="table-title-cell">${escapeHtml(f.name)}</div>
          <div class="table-subtext">${escapeHtml(f.employeeId)}</div>
        </td>
        <td data-label="Email">${escapeHtml(f.email)}</td>
        <td data-label="Department">${escapeHtml(f.department)}</td>
        <td data-label="Role">${escapeHtml(f.designation)}</td>
        <td data-label="Status">
          <span class="badge badge-approved"><span class="badge-symbol">✓</span> ${f.status}</span>
        </td>
      `;
      tableBody.appendChild(tr);
    });
  };

  renderRoster();
  if (searchInput) searchInput.addEventListener('input', renderRoster);
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
