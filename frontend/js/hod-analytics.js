/**
 * HOD Department Analytics controller.
 * GET /dashboard/hod (department-scoped server-side).
 * Renders a verification-status donut + category bar (Chart.js) and an
 * academic-year distribution. Degrades to bar lists if Chart.js is unavailable.
 */

/* Chart.js paints into a <canvas>, which cannot resolve CSS var() — so the chart
   colours have to be read out of the stylesheet as real values. Resolved lazily
   (on render, not at parse time) so the stylesheets are guaranteed to have
   applied, and every lookup carries a fallback literal: a missing token would
   otherwise hand Chart.js an empty string and paint an invisible chart. */
function hodCssColor(token, fallback) {
  const value = getComputedStyle(document.body).getPropertyValue(token).trim();
  return value || fallback;
}

function hodStatusColors() {
  return {
    approved: hodCssColor('--hod-approved', '#047857'),
    pending:  hodCssColor('--hod-pending',  '#B45309'),
    rejected: hodCssColor('--hod-rejected', '#B91C1C')
  };
}

/* First five entries follow the brand/status palette; the last three are neutral
   categorical hues that carry no brand meaning. */
function hodChartPalette() {
  return [
    hodCssColor('--primary-color', '#CF0012'),
    hodCssColor('--hod-tertiary',  '#00567b'),
    hodCssColor('--primary-hover', '#AA000F'),
    hodCssColor('--hod-approved',  '#047857'),
    hodCssColor('--hod-pending',   '#B45309'),
    '#6d28d9', '#0891b2', '#0F766E'
  ];
}

let hodStatusChart = null;
let hodCategoryChart = null;

document.addEventListener('DOMContentLoaded', initHodAnalytics);

async function initHodAnalytics() {
  const me = await window.HOD.ready;
  if (!me) return;
  await loadHodAnalytics();
}

async function loadHodAnalytics() {
  const res = await ApiClient.get('/dashboard/hod');

  if (!res.success) {
    ['hodAnFaculty', 'hodAnTotal', 'hodAnApprovalRate', 'hodAnPending'].forEach((id) => { const el = document.getElementById(id); if (el) el.textContent = '—'; });
    const denied = res.status === 403;
    const msg = denied ? 'You do not have HOD privileges to view department analytics.' : (res.message || 'Unable to load analytics.');
    hodAnChartError('hodStatusChartWrap', denied, msg);
    hodAnChartError('hodCategoryChartWrap', denied, msg);
    const yc = document.getElementById('hodYearContainer');
    if (yc) yc.innerHTML = `<div class="hod-state"><span class="material-symbols-outlined">${denied ? 'lock' : 'error'}</span><div class="hod-state-title">${denied ? 'Access denied' : 'Something went wrong'}</div><p class="hod-state-text">${escapeHtml(msg)}</p></div>`;
    return;
  }

  const d = res.data;
  const total = d.totalAchievements || 0;
  const approved = d.approvedCount || 0;

  const set = (id, v) => { const el = document.getElementById(id); if (el) el.textContent = v; };
  set('hodAnFaculty', d.facultyCount ?? '—');
  set('hodAnTotal', total);
  set('hodAnApprovalRate', total > 0 ? `${Math.round((approved / total) * 100)}%` : '—');
  set('hodAnPending', d.pendingCount ?? '—');

  hodRenderStatusChart(approved, d.pendingCount || 0, d.rejectedCount || 0);
  hodRenderCategoryChart(d.categoryDistribution || {});
  hodAnBars('hodYearContainer', d.academicYearDistribution, total);
}

function hodAnChartError(wrapId, denied, msg) {
  const wrap = document.getElementById(wrapId);
  if (wrap) wrap.innerHTML = `<div class="hod-state"><span class="material-symbols-outlined">${denied ? 'lock' : 'error'}</span><p class="hod-state-text">${escapeHtml(msg)}</p></div>`;
}

function hodRenderStatusChart(approved, pending, rejected) {
  const total = approved + pending + rejected;
  const legend = document.getElementById('hodStatusLegend');
  const wrap = document.getElementById('hodStatusChartWrap');

  if (total === 0) {
    if (wrap) wrap.innerHTML = `<div class="hod-state"><span class="material-symbols-outlined">donut_large</span><p class="hod-state-text">No achievements recorded yet.</p></div>`;
    if (legend) legend.innerHTML = '';
    return;
  }

  const statusColors = hodStatusColors();
  const rows = [
    { label: 'Approved', value: approved, color: statusColors.approved },
    { label: 'Pending', value: pending, color: statusColors.pending },
    { label: 'Rejected', value: rejected, color: statusColors.rejected }
  ];

  if (legend) {
    legend.innerHTML = rows.map((r) => {
      const pct = total > 0 ? Math.round((r.value / total) * 100) : 0;
      return `<div class="hod-legend-item"><span class="hod-legend-dot" style="background:${r.color};"></span><span class="hod-legend-label">${r.label}</span><span class="hod-legend-value">${r.value} · ${pct}%</span></div>`;
    }).join('');
  }

  const canvas = document.getElementById('hodStatusChart');
  if (!window.Chart || !canvas) { hodAnBars('hodStatusChartWrap', { Approved: approved, Pending: pending, Rejected: rejected }, total); return; }

  if (hodStatusChart) hodStatusChart.destroy();
  hodStatusChart = new Chart(canvas, {
    type: 'doughnut',
    data: { labels: rows.map((r) => r.label), datasets: [{ data: rows.map((r) => r.value), backgroundColor: rows.map((r) => r.color), borderWidth: 2, borderColor: '#fff' }] },
    options: { responsive: true, maintainAspectRatio: false, cutout: '62%', plugins: { legend: { display: false } } }
  });
}

function hodRenderCategoryChart(distMap) {
  const entries = Object.entries(distMap).sort((a, b) => b[1] - a[1]);
  const wrap = document.getElementById('hodCategoryChartWrap');

  if (!entries.length) {
    if (wrap) wrap.innerHTML = `<div class="hod-state"><span class="material-symbols-outlined">bar_chart</span><p class="hod-state-text">No category data recorded yet.</p></div>`;
    return;
  }

  const total = entries.reduce((s, [, v]) => s + v, 0);
  const canvas = document.getElementById('hodCategoryChart');
  if (!window.Chart || !canvas) { hodAnBars('hodCategoryChartWrap', distMap, total); return; }

  if (hodCategoryChart) hodCategoryChart.destroy();
  const palette = hodChartPalette();
  hodCategoryChart = new Chart(canvas, {
    type: 'bar',
    data: {
      labels: entries.map((e) => e[0]),
      datasets: [{ data: entries.map((e) => e[1]), backgroundColor: entries.map((_, i) => palette[i % palette.length]), borderRadius: 6, maxBarThickness: 34 }]
    },
    options: {
      indexAxis: 'y', responsive: true, maintainAspectRatio: false,
      plugins: { legend: { display: false } },
      scales: { x: { beginAtZero: true, ticks: { precision: 0 }, grid: { color: '#eee' } }, y: { grid: { display: false } } }
    }
  });
}

/** Lightweight horizontal-bar list — used for the year distribution and as a Chart.js fallback. */
function hodAnBars(containerId, distMap, total) {
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
    html += `<div><div class="hod-bar-row-top"><span>${escapeHtml(key)}</span><span class="hod-muted">${count} · ${pct}%</span></div><div class="hod-bar-track"><div class="hod-bar-fill" style="width:${pct}%;"></div></div></div>`;
  }
  html += '</div>';
  c.innerHTML = html;
}
