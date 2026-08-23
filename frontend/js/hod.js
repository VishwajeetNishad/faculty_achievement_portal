/**
 * HOD Dashboard controller — GET /dashboard/hod (department-scoped server-side).
 * Identity + role guard come from hod-common.js (window.HOD.ready).
 */

document.addEventListener('DOMContentLoaded', initHodDashboard);

async function initHodDashboard() {
  const me = await window.HOD.ready;
  if (!me) return; // guard (non-HOD / load error) already rendered by hod-common.js

  // Greeting
  const greetEl = document.getElementById('hodGreeting');
  if (greetEl) greetEl.textContent = `${hodGreetingText()}, ${me.fullName || 'Professor'}.`;

  await loadHodDashboardData();
}

function hodGreetingText() {
  const h = new Date().getHours();
  if (h < 12) return 'Good morning';
  if (h < 17) return 'Good afternoon';
  return 'Good evening';
}

async function loadHodDashboardData() {
  const res = await ApiClient.get('/dashboard/hod');

  if (!res.success) {
    const msg = res.status === 403
      ? 'You do not have HOD privileges to view department analytics.'
      : (res.message || 'Unable to load department analytics.');
    const tb = document.getElementById('hodRecentTableBody');
    if (tb) tb.innerHTML = `<tr><td colspan="6"><div class="hod-state"><span class="material-symbols-outlined">error</span><div class="hod-state-title">${res.status === 403 ? 'Access denied' : 'Something went wrong'}</div><p class="hod-state-text">${escapeHtml(msg)}</p></div></td></tr>`;
    ['hodFacultyCount', 'hodTotalAchievements', 'hodPendingCount', 'hodApprovedCount'].forEach((id) => {
      const el = document.getElementById(id); if (el) el.textContent = '—';
    });
    return;
  }

  const d = res.data;

  setText('hodFacultyCount', d.facultyCount);
  setText('hodTotalAchievements', d.totalAchievements);
  setText('hodPendingCount', d.pendingCount);
  setText('hodApprovedCount', d.approvedCount);

  // "Review Pending (N)" call to action
  const btn = document.getElementById('hodReviewPendingBtn');
  if (btn) {
    const n = d.pendingCount || 0;
    btn.innerHTML = n > 0
      ? `<span class="material-symbols-outlined">fact_check</span> Review Pending (${n})`
      : `<span class="material-symbols-outlined">check_circle</span> All caught up`;
  }

  hodRenderBars('hodCategoryContainer', d.categoryDistribution, d.totalAchievements);
  hodRenderBars('hodYearContainer', d.academicYearDistribution, d.totalAchievements);
  hodRenderRecent(d.recentSubmissions || []);
}

function setText(id, value) {
  const el = document.getElementById(id);
  if (el) el.textContent = (value === null || value === undefined) ? '—' : value;
}

function hodRenderBars(containerId, distMap, total) {
  const c = document.getElementById(containerId);
  if (!c) return;

  const entries = distMap ? Object.entries(distMap) : [];
  if (!entries.length) {
    c.innerHTML = `<div class="hod-state" style="padding:20px 8px;"><span class="material-symbols-outlined">bar_chart</span><p class="hod-state-text">No data recorded yet.</p></div>`;
    return;
  }

  entries.sort((a, b) => b[1] - a[1]);
  let html = '<div class="hod-bars">';
  for (const [key, count] of entries) {
    const pct = total > 0 ? Math.round((count / total) * 100) : 0;
    html += `
      <div>
        <div class="hod-bar-row-top"><span>${escapeHtml(key)}</span><span class="hod-muted">${count} · ${pct}%</span></div>
        <div class="hod-bar-track"><div class="hod-bar-fill" style="width:${pct}%;"></div></div>
      </div>`;
  }
  html += '</div>';
  c.innerHTML = html;
}

function hodRenderRecent(items) {
  const tb = document.getElementById('hodRecentTableBody');
  if (!tb) return;

  if (!items.length) {
    tb.innerHTML = `<tr><td colspan="6"><div class="hod-state"><span class="material-symbols-outlined">inbox</span><div class="hod-state-title">No submissions yet</div><p class="hod-state-text">Achievements submitted by your department will appear here.</p></div></td></tr>`;
    return;
  }

  tb.innerHTML = '';
  items.forEach((item) => {
    const isPending = String(item.status).toUpperCase() === 'PENDING';
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td data-label="Faculty">
        <div class="hod-faculty-cell">
          <div class="hod-cell-avatar">${hodInitials(item.facultyName)}</div>
          <div>
            <div class="hod-cell-title">${escapeHtml(item.facultyName || '—')}</div>
            <div class="hod-cell-sub">${escapeHtml(item.employeeId || '')}</div>
          </div>
        </div>
      </td>
      <td data-label="Achievement"><div class="hod-cell-truncate" title="${escapeHtml(item.title || '')}">${escapeHtml(item.title || '—')}</div></td>
      <td data-label="Category">${hodCategoryChip(item.categoryName, item.categoryCode)}</td>
      <td data-label="Date">${hodFormatDate(item.achievementDate)}</td>
      <td data-label="Status">${hodStatusBadge(item.status)}</td>
      <td data-label="Action" class="hod-td-right">
        <button class="hod-btn hod-btn-outline hod-btn-sm" data-id="${item.id}">
          <span class="material-symbols-outlined">${isPending ? 'rate_review' : 'visibility'}</span>${isPending ? 'Review' : 'View'}
        </button>
      </td>`;
    tb.appendChild(tr);
    tr.querySelector('button[data-id]').addEventListener('click', () => openHodReviewModal(item.id, loadHodDashboardData));
  });
}
