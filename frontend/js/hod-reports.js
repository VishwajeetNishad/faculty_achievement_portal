/**
 * HOD Portal — Institution Reports (permission-gated by VIEW_REPORTS).
 *
 * This screen only appears for an HOD whose Admin granted VIEW_REPORTS. It reuses the
 * SAME endpoint the Admin dashboard uses: GET /api/dashboard/admin, which the backend
 * guards with "ROLE_ADMIN or VIEW_REPORTS" and which returns institution-wide numbers
 * (every department), not just this HOD's department.
 *
 * The "Export CSV" button is shown only if the HOD also holds EXPORT_REPORTS. IMPORTANT:
 * EXPORT_REPORTS is a UI-only toggle — the export endpoint (/api/achievements/export/csv)
 * is scoped by role on the backend, so a HOD always exports *their own department only*.
 * We never change that server behaviour, and we say so on the page.
 *
 * Load order (from reports.html): config → api → common → hod-common → hod-reports.
 * escapeHtml / showToast come from common.js — do NOT redefine them.
 */

document.addEventListener('DOMContentLoaded', async () => {
  const me = await window.HOD.ready;
  if (!me) return;

  // UX gate. Backend still enforces VIEW_REPORTS on /dashboard/admin regardless.
  if (!hodCan('VIEW_REPORTS')) {
    hodReportsDenied();
    return;
  }

  // Export is a separate permission — only offer the button if the HOD also holds it.
  if (hodCan('EXPORT_REPORTS')) {
    const wrap = document.getElementById('hodReportExportWrap');
    const btn = document.getElementById('hodReportExport');
    if (wrap) wrap.style.display = 'flex';
    if (btn) btn.addEventListener('click', hodReportsExport);
  }

  hodReportsLoad();
});

async function hodReportsLoad() {
  const res = await ApiClient.get('/dashboard/admin');
  if (!res.success) {
    hodReportsError(res.status === 403
      ? 'Your account no longer has permission to view institution reports.'
      : (res.message || 'Failed to load institution reports.'));
    return;
  }

  const d = res.data || {};
  const set = (id, v) => { const el = document.getElementById(id); if (el) el.textContent = (v ?? 0); };
  set('hodRepFaculty', d.totalFaculty);
  set('hodRepTotal', d.totalAchievements);
  set('hodRepApproved', d.approvedCount);
  set('hodRepPending', d.pendingCount);
  set('hodRepRejected', d.rejectedCount);

  hodReportsBars('hodRepCategory', d.categoryDistribution);
  hodReportsBars('hodRepYear', d.academicYearDistribution);
  hodReportsDeptTable(Array.isArray(d.departmentComparison) ? d.departmentComparison : []);
}

/** Render a simple horizontal bar list from a {label: count} map. */
function hodReportsBars(containerId, map) {
  const el = document.getElementById(containerId);
  if (!el) return;

  const entries = map ? Object.entries(map) : [];
  if (!entries.length) {
    el.innerHTML = `<p class="hod-muted" style="margin:0;">No data available.</p>`;
    return;
  }

  const max = Math.max(...entries.map(([, v]) => Number(v) || 0), 1);

  // Prettify a category code (PUBLICATION → Research Publication) using the seeded list.
  const labelFor = (k) => {
    const opt = (typeof HOD_CATEGORY_OPTIONS !== 'undefined') && HOD_CATEGORY_OPTIONS.find((o) => o.code === k);
    return opt ? opt.label : k;
  };

  el.innerHTML = entries
    .sort((a, b) => (Number(b[1]) || 0) - (Number(a[1]) || 0))
    .map(([k, v]) => {
      const count = Number(v) || 0;
      const pct = Math.round((count / max) * 100);
      return `
        <div style="margin-bottom:14px;">
          <div style="display:flex;justify-content:space-between;font-size:13px;margin-bottom:6px;">
            <span>${escapeHtml(labelFor(k))}</span><strong>${count}</strong>
          </div>
          <div style="height:8px;background:var(--hod-surface-low);border-radius:6px;overflow:hidden;">
            <div style="height:100%;width:${pct}%;background:var(--hod-primary);border-radius:6px;"></div>
          </div>
        </div>`;
    }).join('');
}

function hodReportsDeptTable(list) {
  const body = document.getElementById('hodRepDeptBody');
  if (!body) return;

  if (!list.length) {
    body.innerHTML = `<tr><td colspan="6"><div class="hod-state">
      <span class="material-symbols-outlined">inbox</span>
      <div class="hod-state-title">No department data</div>
      <p class="hod-state-text">No departments have recorded achievements yet.</p>
    </div></td></tr>`;
    return;
  }

  body.innerHTML = list.map((d) => `
    <tr>
      <td data-label="Department">
        <div style="font-weight:600;">${escapeHtml(d.departmentName || '—')}</div>
        <div class="hod-muted" style="font-size:12px;">${escapeHtml(d.departmentCode || '')}</div>
      </td>
      <td data-label="Faculty"><strong>${d.facultyCount ?? 0}</strong></td>
      <td data-label="Total"><strong>${d.totalAchievements ?? 0}</strong></td>
      <td data-label="Approved"><span class="hod-badge hod-badge-approved">${d.approvedCount ?? 0}</span></td>
      <td data-label="Pending"><span class="hod-badge hod-badge-pending">${d.pendingCount ?? 0}</span></td>
      <td data-label="Rejected"><span class="hod-badge hod-badge-rejected">${d.rejectedCount ?? 0}</span></td>
    </tr>`).join('');
}

async function hodReportsExport() {
  const btn = document.getElementById('hodReportExport');
  const original = btn ? btn.innerHTML : '';
  if (btn) { btn.disabled = true; btn.innerHTML = `<span class="material-symbols-outlined">hourglass_top</span> Preparing…`; }
  showToast('Preparing your department CSV…', 'info');

  const res = await ApiClient.downloadBlob('/achievements/export/csv');

  if (btn) { btn.disabled = false; btn.innerHTML = original; }

  if (res.success && res.objectUrl) {
    const a = document.createElement('a');
    a.href = res.objectUrl;
    a.download = 'achievements-export.csv';
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(res.objectUrl);
    showToast('CSV downloaded (your department only).', 'success');
  } else {
    showToast(res.message || 'Export failed. Please try again.', 'error');
  }
}

function hodReportsDenied() {
  const c = document.getElementById('hodRepContent');
  if (c) {
    c.innerHTML = `<div class="hod-card"><div class="hod-state">
      <span class="material-symbols-outlined">lock</span>
      <div class="hod-state-title">Permission required</div>
      <p class="hod-state-text">Viewing institution-wide reports requires the <strong>VIEW_REPORTS</strong> permission. Ask an administrator to grant it.</p>
    </div></div>`;
  }
  const wrap = document.getElementById('hodReportExportWrap');
  if (wrap) wrap.style.display = 'none';
}

function hodReportsError(msg) {
  const c = document.getElementById('hodRepContent');
  if (c) {
    c.innerHTML = `<div class="hod-card"><div class="hod-state">
      <span class="material-symbols-outlined">error</span>
      <div class="hod-state-title">Unable to load</div>
      <p class="hod-state-text">${escapeHtml(msg)}</p>
    </div></div>`;
  }
}
