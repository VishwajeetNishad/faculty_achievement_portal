/**
 * HOD Dashboard Controller — Live API Integration with /api/dashboard/hod
 */

document.addEventListener('DOMContentLoaded', () => {
  initializeHodDashboard();
});

async function initializeHodDashboard() {
  const tableBody = document.getElementById('hodRecentTableBody');
  if (tableBody) {
    tableBody.innerHTML = `<tr><td colspan="5" class="empty-state"><div class="spinner"></div><p style="margin-top: 0.5rem;">Loading department analytics...</p></td></tr>`;
  }

  // 1. Fetch Current HOD Profile Details
  const userRes = await ApiClient.get('/auth/me');
  if (userRes.success && userRes.data) {
    const user = userRes.data;
    const nameElem = document.getElementById('hodUserName');
    const deptTag = document.getElementById('hodDeptTag');
    const deptBadge = document.getElementById('hodDeptBadge');

    if (nameElem) nameElem.textContent = user.fullName;
    if (deptTag) deptTag.textContent = `${user.departmentName || 'Department'} (${user.departmentCode || 'HOD'})`;
    if (deptBadge) deptBadge.textContent = user.departmentCode || 'HOD';
  }

  // 2. Fetch HOD Department Analytics: GET /api/dashboard/hod
  const res = await ApiClient.get('/dashboard/hod');

  if (res.success && res.data) {
    const data = res.data;

    // Header title update
    const titleElem = document.getElementById('hodHeaderTitle');
    if (titleElem) titleElem.textContent = `${data.departmentName} Department Analytics`;

    // Update Stat Widgets
    const facultyCountElem = document.getElementById('hodFacultyCount');
    const pendingElem = document.getElementById('hodPendingCount');
    const approvedElem = document.getElementById('hodApprovedCount');
    const rejectedElem = document.getElementById('hodRejectedCount');

    if (facultyCountElem) facultyCountElem.textContent = data.facultyCount;
    if (pendingElem) pendingElem.textContent = data.pendingCount;
    if (approvedElem) approvedElem.textContent = data.approvedCount;
    if (rejectedElem) rejectedElem.textContent = data.rejectedCount;

    // Render Category Distribution
    renderDistributionBars('hodCategoryContainer', data.categoryDistribution, data.totalAchievements, '#0284C7');

    // Render Academic Year Distribution
    renderDistributionBars('hodYearContainer', data.academicYearDistribution, data.totalAchievements, '#F2A900');

    // Render Recent Department Submissions
    renderRecentSubmissions(data.recentSubmissions || []);
  } else if (res.status === 403) {
    showToast('Access denied. HOD privileges required.', 'error');
    if (tableBody) {
      tableBody.innerHTML = `<tr><td colspan="5" class="empty-state"><div class="empty-state-title">Access Denied</div><p class="empty-state-text">You do not have HOD privileges to view department analytics.</p></td></tr>`;
    }
  } else {
    showToast(res.message || 'Failed to load department analytics', 'error');
    if (tableBody) {
      tableBody.innerHTML = `<tr><td colspan="5" class="empty-state"><div class="empty-state-title">Error Loading Analytics</div><p class="empty-state-text">${escapeHtml(res.message || 'Unable to fetch analytics')}</p></td></tr>`;
    }
  }
}

function renderDistributionBars(containerId, distMap, total, barColor) {
  const container = document.getElementById(containerId);
  if (!container) return;

  if (!distMap || Object.keys(distMap).length === 0) {
    container.innerHTML = `<div class="empty-state" style="padding: 1rem;"><p class="empty-state-text">No data recorded for this department.</p></div>`;
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
  const tableBody = document.getElementById('hodRecentTableBody');
  if (!tableBody) return;

  tableBody.innerHTML = '';

  if (items.length === 0) {
    tableBody.innerHTML = `
      <tr>
        <td colspan="5" class="empty-state">
          <div class="empty-state-title">No department submissions found</div>
          <p class="empty-state-text">Faculty members in your department have not submitted achievements yet.</p>
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
      <td data-label="Faculty & Title">
        <div class="table-title-cell">${escapeHtml(item.title)}</div>
        <div class="table-subtext">${escapeHtml(item.facultyName)} (${escapeHtml(item.employeeId)})</div>
      </td>
      <td data-label="Category">${escapeHtml(item.categoryName || 'Achievement')}</td>
      <td data-label="Academic Year">${escapeHtml(item.academicYear)}</td>
      <td data-label="Submission Date">${formatDate(item.achievementDate)}</td>
      <td data-label="Status">
        <span class="badge ${badgeClass}">
          <span class="badge-symbol">${badgeSymbol}</span> ${item.status}
        </span>
      </td>
    `;
    tableBody.appendChild(tr);
  });
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
