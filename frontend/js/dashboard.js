/**
 * Faculty Dashboard Controller — Live API Integration with /api/dashboard/faculty
 */

document.addEventListener('DOMContentLoaded', () => {
  initializeDashboard();
});

async function initializeDashboard() {
  const tableBody = document.getElementById('recentSubmissionsBody');
  if (tableBody) {
    tableBody.innerHTML = `<tr><td colspan="5" class="empty-state"><div class="spinner"></div><p style="margin-top: 0.5rem;">Loading dashboard analytics...</p></td></tr>`;
  }

  // 1. Fetch Current User Profile Details
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
    if (userRoleTag) userRoleTag.textContent = `${user.designation || 'Faculty'} (${user.departmentCode || 'NIET'})`;
  }

  // 2. Fetch Real Dashboard Analytics from GET /api/dashboard/faculty
  const res = await ApiClient.get('/dashboard/faculty');

  if (res.success && res.data) {
    const data = res.data;

    // Update Stat Widgets
    const totalElem = document.getElementById('statTotal');
    const pendingElem = document.getElementById('statPending');
    const approvedElem = document.getElementById('statApproved');
    const rejectedElem = document.getElementById('statRejected');

    if (totalElem) totalElem.textContent = data.totalAchievements;
    if (pendingElem) pendingElem.textContent = data.pendingCount;
    if (approvedElem) approvedElem.textContent = data.approvedCount;
    if (rejectedElem) rejectedElem.textContent = data.rejectedCount;

    // Render Category Distribution
    renderDistributionBars('categoryAnalyticsContainer', data.categoryDistribution, data.totalAchievements, '#002147');

    // Render Academic Year Distribution
    renderDistributionBars('yearAnalyticsContainer', data.academicYearDistribution, data.totalAchievements, '#F2A900');

    // Render Recent Submissions Table
    renderRecentSubmissions(data.recentAchievements || []);
  } else {
    showToast(res.message || 'Failed to load faculty dashboard analytics', 'error');
    if (tableBody) {
      tableBody.innerHTML = `<tr><td colspan="5" class="empty-state"><div class="empty-state-title">Error Loading Dashboard</div><p class="empty-state-text">Unable to fetch analytics from backend server.</p></td></tr>`;
    }
  }
}

function renderDistributionBars(containerId, distMap, total, barColor) {
  const container = document.getElementById(containerId);
  if (!container) return;

  if (!distMap || Object.keys(distMap).length === 0) {
    container.innerHTML = `<div class="empty-state" style="padding: 1rem;"><p class="empty-state-text">No category data recorded yet.</p></div>`;
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

function renderRecentSubmissions(items) {
  const tableBody = document.getElementById('recentSubmissionsBody');
  if (!tableBody) return;

  tableBody.innerHTML = '';

  if (items.length === 0) {
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

  items.forEach(item => {
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
