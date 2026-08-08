/**
 * Admin / HOD Verification Queue & Control Center Controller
 * Connected to Live Spring Boot Endpoint:
 * - GET /api/achievements/status/PENDING
 * - PATCH /api/achievements/{id}/verification
 */

let selectedReviewId = null;

document.addEventListener('DOMContentLoaded', () => {
  if (document.getElementById('adminQueueTableBody') || document.getElementById('adminPendingCount')) {
    initializeAdminDashboard();
  }

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
    tableBody.innerHTML = `<tr><td colspan="5" class="empty-state"><div class="spinner"></div><p style="margin-top:0.5rem;">Fetching verification queue from live API...</p></td></tr>`;
  }

  // 1. Fetch PENDING records from API: GET /api/achievements/status/PENDING
  const resPending = await ApiClient.get('/achievements/status/PENDING');
  const resApproved = await ApiClient.get('/achievements/status/APPROVED');
  const resRejected = await ApiClient.get('/achievements/status/REJECTED');

  const pendingItems = (resPending.success && Array.isArray(resPending.data)) ? resPending.data : [];
  const approvedItems = (resApproved.success && Array.isArray(resApproved.data)) ? resApproved.data : [];
  const rejectedItems = (resRejected.success && Array.isArray(resRejected.data)) ? resRejected.data : [];

  // Update Admin Queue Stats
  const pendingElem = document.getElementById('adminPendingCount');
  const verifiedElem = document.getElementById('adminVerifiedCount');
  const rejectedElem = document.getElementById('adminRejectedCount');

  if (pendingElem) pendingElem.textContent = pendingItems.length;
  if (verifiedElem) verifiedElem.textContent = approvedItems.length;
  if (rejectedElem) rejectedElem.textContent = rejectedItems.length;

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
            ${item.proofDocumentUrl ? `<p style="margin-top: 0.75rem;"><strong>Proof Link:</strong> <a href="${escapeHtml(item.proofDocumentUrl)}" target="_blank" rel="noopener">Open Certificate Link</a></p>` : ''}
          `;
        }
        openModal('reviewModal');
      } else {
        showToast(itemRes.message || 'Error fetching achievement details', 'error');
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
