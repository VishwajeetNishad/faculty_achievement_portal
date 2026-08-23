/**
 * HOD Faculty Profile controller — faculty-profile.html?id=<userId>.
 *
 * Security: the id from the query string is validated against the HOD's own
 * /users/department roster BEFORE any per-user achievements call. If the id is
 * not in this department's roster, we render "not found" and never call
 * /achievements/user/{id} — so the page can only ever show in-department data.
 */

let hodProfileUser = null;

document.addEventListener('DOMContentLoaded', initHodFacultyProfile);

async function initHodFacultyProfile() {
  const me = await window.HOD.ready;
  if (!me) return;

  const id = new URLSearchParams(window.location.search).get('id');
  if (!id) { hodRenderProfileError('No faculty selected', 'Return to the directory and choose a faculty member.'); return; }

  const res = await ApiClient.get('/users/department');
  if (!res.success) {
    const denied = res.status === 403;
    hodRenderProfileError(denied ? 'Access denied' : 'Something went wrong',
      denied ? 'You do not have HOD privileges for this department.' : (res.message || 'Unable to load the faculty directory.'));
    return;
  }

  const member = (res.data || []).find((m) => String(m.id) === String(id));
  if (!member) {
    hodRenderProfileError('Faculty not found', 'This faculty member is not part of your department.');
    return;
  }

  hodProfileUser = member;
  hodRenderProfileShell(member);
  await loadHodFacultyAchievements(member.id);
}

function hodRenderProfileError(title, text) {
  const c = document.getElementById('hodProfileContainer');
  if (!c) return;
  c.innerHTML = `
    <a href="faculty.html" class="hod-back-link"><span class="material-symbols-outlined">arrow_back</span> Back to Faculty Directory</a>
    <div class="hod-card"><div class="hod-card-pad"><div class="hod-state"><span class="material-symbols-outlined">person_off</span><div class="hod-state-title">${escapeHtml(title)}</div><p class="hod-state-text">${escapeHtml(text)}</p></div></div></div>`;
}

function hodRenderProfileShell(u) {
  const c = document.getElementById('hodProfileContainer');
  if (!c) return;
  const isActive = String(u.status || '').toUpperCase() === 'ACTIVE';
  const isHod = String(u.role || '').toUpperCase().includes('HOD');

  c.innerHTML = `
    <a href="faculty.html" class="hod-back-link"><span class="material-symbols-outlined">arrow_back</span> Back to Faculty Directory</a>

    <div class="hod-card hod-profile-header">
      <div class="hod-card-pad">
        <div class="hod-profile-id">
          <div class="hod-profile-avatar">${hodInitials(u.fullName)}</div>
          <div class="hod-profile-id-text">
            <div class="hod-profile-name-row">
              <h1 class="hod-page-title" style="margin:0;">${escapeHtml(u.fullName || '—')}</h1>
              <span class="hod-badge ${isActive ? 'hod-badge-approved' : 'hod-badge-rejected'}">
                <span class="material-symbols-outlined">${isActive ? 'check_circle' : 'do_not_disturb_on'}</span>${isActive ? 'Active' : 'Inactive'}
              </span>
            </div>
            <p class="hod-page-sub" style="margin:0;">${escapeHtml(u.designation || (isHod ? 'Head of Department' : 'Faculty Member'))}</p>
            <div class="hod-profile-meta">
              <span class="hod-profile-meta-row"><span class="material-symbols-outlined">badge</span>${escapeHtml(u.employeeId || '—')}</span>
              <span class="hod-profile-meta-row"><span class="material-symbols-outlined">mail</span>${escapeHtml(u.email || '—')}</span>
              <span class="hod-profile-meta-row"><span class="material-symbols-outlined">call</span>${escapeHtml(u.phone || '—')}</span>
              <span class="hod-profile-meta-row"><span class="material-symbols-outlined">apartment</span>${escapeHtml(u.departmentName || u.departmentCode || '—')}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="hod-grid hod-stat-grid" style="margin-bottom:24px;">
      <div class="hod-kpi"><div class="hod-kpi-top"><div class="hod-kpi-icon primary"><span class="material-symbols-outlined">workspace_premium</span></div></div><p class="hod-kpi-label">Total</p><p class="hod-kpi-value" id="hodPfTotal">—</p></div>
      <div class="hod-kpi"><div class="hod-kpi-top"><div class="hod-kpi-icon approved"><span class="material-symbols-outlined">check_circle</span></div></div><p class="hod-kpi-label">Approved</p><p class="hod-kpi-value approved" id="hodPfApproved">—</p></div>
      <div class="hod-kpi"><div class="hod-kpi-top"><div class="hod-kpi-icon pending"><span class="material-symbols-outlined">schedule</span></div></div><p class="hod-kpi-label">Pending</p><p class="hod-kpi-value pending" id="hodPfPending">—</p></div>
      <div class="hod-kpi"><div class="hod-kpi-top"><div class="hod-kpi-icon rejected"><span class="material-symbols-outlined">cancel</span></div></div><p class="hod-kpi-label">Rejected</p><p class="hod-kpi-value rejected" id="hodPfRejected">—</p></div>
    </div>

    <div class="hod-card">
      <div class="hod-card-header"><h3 class="hod-card-title">Achievements</h3></div>
      <div class="hod-table-wrap">
        <table class="hod-table reflow">
          <thead>
            <tr>
              <th>Achievement</th>
              <th>Category</th>
              <th>Date</th>
              <th>Status</th>
              <th class="hod-th-right">Action</th>
            </tr>
          </thead>
          <tbody id="hodPfBody">
            <tr><td colspan="5"><div style="padding:32px 0;"><div class="hod-spinner"></div></div></td></tr>
          </tbody>
        </table>
      </div>
    </div>`;
}

async function loadHodFacultyAchievements(userId) {
  const body = document.getElementById('hodPfBody');
  if (body) body.innerHTML = `<tr><td colspan="5"><div style="padding:32px 0;"><div class="hod-spinner"></div></div></td></tr>`;

  const res = await ApiClient.get(`/achievements/user/${userId}`);

  if (!res.success) {
    ['hodPfTotal', 'hodPfApproved', 'hodPfPending', 'hodPfRejected'].forEach((id) => { const el = document.getElementById(id); if (el) el.textContent = '—'; });
    if (body) body.innerHTML = `<tr><td colspan="5"><div class="hod-state"><span class="material-symbols-outlined">error</span><div class="hod-state-title">Something went wrong</div><p class="hod-state-text">${escapeHtml(res.message || 'Unable to load achievements for this faculty member.')}</p></div></td></tr>`;
    return;
  }

  const items = res.data || [];
  const counts = { total: items.length, APPROVED: 0, PENDING: 0, REJECTED: 0 };
  items.forEach((a) => { const s = String(a.status || '').toUpperCase(); if (counts[s] !== undefined) counts[s]++; });
  const set = (id, v) => { const el = document.getElementById(id); if (el) el.textContent = v; };
  set('hodPfTotal', counts.total); set('hodPfApproved', counts.APPROVED); set('hodPfPending', counts.PENDING); set('hodPfRejected', counts.REJECTED);

  if (!items.length) {
    if (body) body.innerHTML = `<tr><td colspan="5"><div class="hod-state"><span class="material-symbols-outlined">workspace_premium</span><div class="hod-state-title">No achievements yet</div><p class="hod-state-text">This faculty member has not submitted any achievements.</p></div></td></tr>`;
    return;
  }

  // Newest first by achievement date, then creation.
  items.sort((a, b) => String(b.achievementDate || b.createdAt || '').localeCompare(String(a.achievementDate || a.createdAt || '')));

  if (body) {
    body.innerHTML = '';
    items.forEach((item) => {
      const isPending = String(item.status).toUpperCase() === 'PENDING';
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td data-label="Achievement"><div class="hod-cell-truncate" title="${escapeHtml(item.title || '')}">${escapeHtml(item.title || '—')}</div></td>
        <td data-label="Category">${hodCategoryChip(item.categoryName, item.categoryCode)}</td>
        <td data-label="Date">${hodFormatDate(item.achievementDate)}</td>
        <td data-label="Status">${hodStatusBadge(item.status)}</td>
        <td data-label="Action" class="hod-td-right">
          <button class="hod-btn ${isPending ? 'hod-btn-primary' : 'hod-btn-outline'} hod-btn-sm" data-id="${item.id}">
            <span class="material-symbols-outlined">${isPending ? 'rate_review' : 'visibility'}</span>${isPending ? 'Review' : 'View'}
          </button>
        </td>`;
      body.appendChild(tr);
      tr.querySelector('button[data-id]').addEventListener('click', () => openHodReviewModal(item.id, () => loadHodFacultyAchievements(userId)));
    });
  }
}
