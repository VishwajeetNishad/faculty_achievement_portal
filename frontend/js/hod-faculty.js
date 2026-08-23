/**
 * HOD Faculty Directory controller.
 * GET /users/department — returns this HOD's department roster (JWT-scoped, HOD-only).
 * Search is client-side over the returned roster.
 */

let hodFacultyRoster = [];

document.addEventListener('DOMContentLoaded', initHodFaculty);

async function initHodFaculty() {
  const me = await window.HOD.ready;
  if (!me) return;

  const search = document.getElementById('hodFacultySearch');
  if (search) search.addEventListener('input', hodDebounce((e) => hodRenderFaculty(e.target.value.trim().toLowerCase()), 200));

  await loadHodFaculty();
}

async function loadHodFaculty() {
  const grid = document.getElementById('hodFacultyGrid');
  const res = await ApiClient.get('/users/department');

  if (!res.success) {
    hodSetFacultyCount(null);
    const denied = res.status === 403;
    if (grid) grid.innerHTML = `<div class="hod-card" style="grid-column:1/-1;"><div class="hod-card-pad"><div class="hod-state"><span class="material-symbols-outlined">${denied ? 'lock' : 'error'}</span><div class="hod-state-title">${denied ? 'Access denied' : 'Something went wrong'}</div><p class="hod-state-text">${escapeHtml(denied ? 'You do not have HOD privileges for this department.' : (res.message || 'Unable to load the faculty directory.'))}</p></div></div></div>`;
    return;
  }

  // Sort by name; keep the full roster for client-side search.
  hodFacultyRoster = (res.data || []).slice().sort((a, b) => (a.fullName || '').localeCompare(b.fullName || ''));
  hodSetFacultyCount(hodFacultyRoster.length);
  hodRenderFaculty('');
}

function hodSetFacultyCount(n) {
  const pill = document.getElementById('hodFacultyCount');
  if (!pill) return;
  pill.innerHTML = n === null
    ? `<span class="material-symbols-outlined">groups</span> — faculty`
    : `<span class="material-symbols-outlined">groups</span> ${n} ${n === 1 ? 'member' : 'members'}`;
}

function hodRenderFaculty(term) {
  const grid = document.getElementById('hodFacultyGrid');
  if (!grid) return;

  const list = !term ? hodFacultyRoster : hodFacultyRoster.filter((f) => {
    return [f.fullName, f.employeeId, f.designation, f.email].some((v) => (v || '').toLowerCase().includes(term));
  });

  if (!list.length) {
    grid.innerHTML = `<div class="hod-card" style="grid-column:1/-1;"><div class="hod-card-pad"><div class="hod-state"><span class="material-symbols-outlined">${term ? 'search_off' : 'groups'}</span><div class="hod-state-title">${term ? 'No matching faculty' : 'No faculty found'}</div><p class="hod-state-text">${term ? 'Try a different search term.' : 'This department has no faculty records yet.'}</p></div></div></div>`;
    return;
  }

  grid.innerHTML = '';
  list.forEach((f) => {
    const isActive = String(f.status || '').toUpperCase() === 'ACTIVE';
    const isHod = String(f.role || '').toUpperCase().includes('HOD');
    const card = document.createElement('div');
    card.className = 'hod-card hod-faculty-card';
    card.innerHTML = `
      <div class="hod-card-pad">
        <div class="hod-faculty-card-top">
          <div class="hod-faculty-avatar">${hodInitials(f.fullName)}</div>
          <span class="hod-badge ${isActive ? 'hod-badge-approved' : 'hod-badge-rejected'}">
            <span class="material-symbols-outlined">${isActive ? 'check_circle' : 'do_not_disturb_on'}</span>${isActive ? 'Active' : 'Inactive'}
          </span>
        </div>
        <h3 class="hod-faculty-name">${escapeHtml(f.fullName || '—')}</h3>
        <p class="hod-faculty-role">${escapeHtml(f.designation || (isHod ? 'Head of Department' : 'Faculty Member'))}</p>
        <div class="hod-faculty-meta">
          <span class="hod-faculty-meta-row"><span class="material-symbols-outlined">badge</span>${escapeHtml(f.employeeId || '—')}</span>
          <span class="hod-faculty-meta-row" title="${escapeHtml(f.email || '')}"><span class="material-symbols-outlined">mail</span><span class="hod-cell-truncate">${escapeHtml(f.email || '—')}</span></span>
        </div>
        <a class="hod-btn hod-btn-outline hod-btn-sm hod-faculty-cta" href="faculty-profile.html?id=${f.id}">
          <span class="material-symbols-outlined">person</span> View Profile
        </a>
      </div>`;
    grid.appendChild(card);
  });
}
