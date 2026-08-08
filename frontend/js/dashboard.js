/**
 * Faculty Dashboard Controller — Integrated with Authenticated GET /api/achievements/me
 */

document.addEventListener('DOMContentLoaded', () => {
  initializeDashboard();
});

async function initializeDashboard() {
  let achievements = [];

  // Show loading state
  const tableBody = document.getElementById('recentSubmissionsBody');
  if (tableBody) {
    tableBody.innerHTML = `<tr><td colspan="5" class="empty-state"><div class="spinner"></div><p style="margin-top: 0.5rem;">Loading achievement portfolio from server...</p></td></tr>`;
  }

  // 1. Fetch Current User Details from /api/auth/me
  const userRes = await ApiClient.get('/auth/me');
  if (userRes.success && userRes.data) {
    const user = userRes.data;
    const welcomeElem = document.getElementById('dashboardWelcome');
    if (welcomeElem) {
      welcomeElem.textContent = `Welcome back, ${user.fullName}`;
    }

    const userNameTag = document.querySelector('.user-name');
    const userRoleTag = document.querySelector('.user-role-tag');
    if (userNameTag) userNameTag.textContent = user.fullName;
    if (userRoleTag) userRoleTag.textContent = `${user.designation} (${user.departmentCode})`;
  }

  // 2. Fetch Achievements from Authenticated Endpoint: GET /api/achievements/me
  const res = await ApiClient.get('/achievements/me');
  if (res.success && Array.isArray(res.data)) {
    achievements = res.data;
  } else {
    showToast(res.message || 'Failed to load achievements from backend API', 'error');
    achievements = [];
  }

  // Calculate Real Statistics from User Portfolio
  const totalCount = achievements.length;
  const pendingCount = achievements.filter(a => a.status === 'PENDING').length;
  const approvedCount = achievements.filter(a => a.status === 'APPROVED').length;
  const rejectedCount = achievements.filter(a => a.status === 'REJECTED').length;

  // Update Stat Widgets
  const totalElem = document.getElementById('statTotal');
  const pendingElem = document.getElementById('statPending');
  const approvedElem = document.getElementById('statApproved');
  const rejectedElem = document.getElementById('statRejected');

  if (totalElem) totalElem.textContent = totalCount;
  if (pendingElem) pendingElem.textContent = pendingCount;
  if (approvedElem) approvedElem.textContent = approvedCount;
  if (rejectedElem) rejectedElem.textContent = rejectedCount;

  // Render Recent Submissions Table
  if (tableBody) {
    tableBody.innerHTML = '';
    const recentItems = achievements.slice(0, 5);

    if (recentItems.length === 0) {
      tableBody.innerHTML = `
        <tr>
          <td colspan="5" class="empty-state">
            <div class="empty-state-title">No submissions found</div>
            <p class="empty-state-text">Start building your academic portfolio by submitting your first achievement.</p>
            <a href="add-achievement.html" class="btn btn-primary btn-sm">+ Submit Achievement</a>
          </td>
        </tr>
      `;
      return;
    }

    recentItems.forEach(item => {
      const badgeClass = item.status === 'APPROVED' ? 'badge-approved' : (item.status === 'REJECTED' ? 'badge-rejected' : 'badge-pending');
      const badgeSymbol = item.status === 'APPROVED' ? '✓' : (item.status === 'REJECTED' ? '!' : '●');

      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td data-label="Title & Category">
          <div class="table-title-cell">${escapeHtml(item.title)}</div>
          <div class="table-subtext">${escapeHtml(item.categoryName || 'Achievement')}</div>
        </td>
        <td data-label="Academic Year">${escapeHtml(item.academicYear)}</td>
        <td data-label="Submission Date">${formatDate(item.achievementDate)}</td>
        <td data-label="Status">
          <span class="badge ${badgeClass}">
            <span class="badge-symbol">${badgeSymbol}</span> ${item.status}
          </span>
        </td>
        <td data-label="Action">
          <button class="btn btn-outline btn-sm view-details-btn" data-id="${item.id}">View</button>
        </td>
      `;
      tableBody.appendChild(tr);
    });

    tableBody.querySelectorAll('.view-details-btn').forEach(btn => {
      btn.addEventListener('click', () => {
        const id = btn.getAttribute('data-id');
        showAchievementDetailsModal(id);
      });
    });
  }
}

async function showAchievementDetailsModal(id) {
  const res = await ApiClient.get(`/achievements/${id}`);
  if (!res.success || !res.data) {
    showToast(res.message || 'Achievement details not found', 'error');
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
