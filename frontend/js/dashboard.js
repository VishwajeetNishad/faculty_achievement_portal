/**
 * Faculty Dashboard Controller — PDF Design Implementation (Page 07)
 * Connected to GET /api/dashboard/faculty and GET /api/auth/me
 */

document.addEventListener('DOMContentLoaded', () => {
  initializeDashboard();
});

async function initializeDashboard() {
  const recentContainer = document.getElementById('recentSubmissionsBody');
  if (recentContainer) {
    recentContainer.innerHTML = `<div style="text-align: center; padding: 2rem;"><div class="spinner"></div><p style="margin-top: 0.5rem; font-size: 0.8rem; color: var(--text-secondary);">Loading dashboard...</p></div>`;
  }

  // 1. Fetch Current User Profile
  const userRes = await ApiClient.get('/auth/me');
  if (userRes.success && userRes.data) {
    const user = userRes.data;
    
    // Welcome message
    const welcomeElem = document.getElementById('dashboardWelcome');
    if (welcomeElem) welcomeElem.textContent = `Welcome back, ${user.fullName || 'Faculty Member'}`;

    // Initials calculation
    const names = (user.fullName || 'User').split(' ');
    const initials = names.length >= 2 ? (names[0][0] + names[names.length - 1][0]).toUpperCase() : user.fullName.substring(0, 2).toUpperCase();

    // Header avatar
    const headerAvatar = document.getElementById('headerAvatar');
    if (headerAvatar) headerAvatar.textContent = initials;

    // Sidebar footer user widget
    const sidebarAvatar = document.getElementById('sidebarAvatar');
    const sidebarName = document.getElementById('sidebarUserName');
    const sidebarRole = document.getElementById('sidebarUserRole');

    if (sidebarAvatar) sidebarAvatar.textContent = initials;
    if (sidebarName) sidebarName.textContent = user.fullName || 'Dr. Faculty';
    if (sidebarRole) sidebarRole.textContent = `${user.designation || 'Faculty'} • ${user.departmentCode || 'CSE'}`;
  }

  // 2. Fetch Real Dashboard Analytics from GET /api/dashboard/faculty
  const res = await ApiClient.get('/dashboard/faculty');

  if (res.success && res.data) {
    const data = res.data;

    // Update 4 Stat Widgets
    const totalElem = document.getElementById('statTotal');
    const approvedElem = document.getElementById('statApproved');
    const pendingElem = document.getElementById('statPending');
    const rejectedElem = document.getElementById('statRejected');

    const total = data.totalAchievements || 0;
    const approved = data.approvedCount || 0;
    const pending = data.pendingCount || 0;
    const rejected = data.rejectedCount || 0;

    if (totalElem) totalElem.textContent = total;
    if (approvedElem) approvedElem.textContent = approved;
    if (pendingElem) pendingElem.textContent = pending;
    if (rejectedElem) rejectedElem.textContent = rejected;

    // Update Sidebar badge count
    const sidebarCount = document.getElementById('sidebarAchievementCount');
    if (sidebarCount) sidebarCount.textContent = total;

    // Update Donut Chart
    const donutTotal = document.getElementById('donutTotalCount');
    const legendApp = document.getElementById('legendApprovedCount');
    const legendPend = document.getElementById('legendPendingCount');
    const legendRej = document.getElementById('legendRejectedCount');
    const donutVisual = document.getElementById('statusDonutVisual');

    if (donutTotal) donutTotal.textContent = total;
    if (legendApp) legendApp.textContent = approved;
    if (legendPend) legendPend.textContent = pending;
    if (legendRej) legendRej.textContent = rejected;

    if (donutVisual && total > 0) {
      const appDeg = (approved / total) * 360;
      const pendDeg = appDeg + (pending / total) * 360;
      donutVisual.style.background = `conic-gradient(#10B981 0deg ${appDeg}deg, #F59E0B ${appDeg}deg ${pendDeg}deg, #EF4444 ${pendDeg}deg 360deg)`;
    }

    // Render Category Distribution Progress Bars
    renderCategoryBars(data.categoryDistribution || {}, total);

    // Render Recent Submissions
    renderRecentSubmissionsList(data.recentAchievements || []);
  } else {
    showToast(res.message || 'Failed to load dashboard data', 'error');
    if (recentContainer) {
      recentContainer.innerHTML = `<div class="empty-state" style="padding: 1.5rem;"><div class="empty-state-title">No Data</div><p class="empty-state-text">${escapeHtml(res.message || 'Unable to fetch analytics')}</p></div>`;
    }
  }
}

function renderCategoryBars(categoryDist, total) {
  const container = document.getElementById('categoryBarsList');
  if (!container) return;

  const entries = Object.entries(categoryDist);
  if (entries.length === 0) {
    container.innerHTML = `<p style="font-size: 0.75rem; color: var(--text-secondary); text-align: center; padding: 1rem;">No category data yet.</p>`;
    return;
  }

  container.innerHTML = '';
  entries.forEach(([catName, count]) => {
    const pct = total > 0 ? Math.round((count / total) * 100) : 0;
    const row = document.createElement('div');
    row.className = 'category-grid-item category-row';
    row.innerHTML = `
      <span class="cat-name-lbl category-name" title="${escapeHtml(catName)}">${escapeHtml(catName)}</span>
      <div class="cat-bar-track category-bar-bg">
        <div class="cat-bar-progress category-bar-fill" style="width: ${pct}%;"></div>
      </div>
      <span class="cat-count-val category-count">${count}</span>
    `;
    container.appendChild(row);
  });
}

function renderRecentSubmissionsList(items) {
  const container = document.getElementById('recentSubmissionsBody');
  if (!container) return;

  if (items.length === 0) {
    container.innerHTML = `
      <div class="empty-state" style="padding: 1.5rem;">
        <div class="empty-state-title" style="font-size: 0.95rem;">No Submissions Yet</div>
        <p class="empty-state-text" style="font-size: 0.75rem; margin-bottom: 1rem;">Click below to submit your first faculty achievement record.</p>
        <a href="add-achievement.html" class="btn btn-primary btn-sm">+ Submit Achievement</a>
      </div>`;
    return;
  }

  container.innerHTML = '';
  items.slice(0, 5).forEach(item => {
    const row = document.createElement('div');
    row.className = 'recent-feed-row recent-item-row';

    const statusClass = item.status === 'APPROVED' ? 'approved' : item.status === 'REJECTED' ? 'rejected' : 'pending';
    const statusLabel = item.status === 'APPROVED' ? '• Approved' : item.status === 'REJECTED' ? '• Rejected' : '• Pending';
    const dateFormatted = item.achievementDate ? new Date(item.achievementDate).toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' }) : '—';

    row.innerHTML = `
      <div class="recent-feed-info recent-item-info">
        <h4>${escapeHtml(item.title)}</h4>
        <p>${escapeHtml(item.categoryName || item.categoryCode || 'Achievement')} &bull; ${dateFormatted}</p>
      </div>
      <span class="badge-status-dot ${statusClass}">${statusLabel}</span>
    `;
    container.appendChild(row);
  });
}
