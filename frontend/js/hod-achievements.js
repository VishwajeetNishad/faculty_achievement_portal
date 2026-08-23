/**
 * HOD Department Achievements controller.
 * GET /achievements/search?keyword&status&categoryCode&academicYear&page&size
 * — department-scoped server-side. Rows open the shared review modal (read-only
 * for verified items; review controls for PENDING).
 */

const hodAchState = { page: 0, size: 10, keyword: '', status: '', categoryCode: '', academicYear: '' };

document.addEventListener('DOMContentLoaded', initHodAchievements);

async function initHodAchievements() {
  const me = await window.HOD.ready;
  if (!me) return;

  hodPopulateCategoryFilter('hodCategoryFilter');
  await hodPopulateYearFilter('hodYearFilter');
  hodBindAchFilters();
  await loadHodAchievements();
}

function hodBindAchFilters() {
  const search = document.getElementById('hodSearchInput');
  const status = document.getElementById('hodStatusFilter');
  const cat = document.getElementById('hodCategoryFilter');
  const year = document.getElementById('hodYearFilter');
  const reset = document.getElementById('hodClearFilters');

  if (search) search.addEventListener('input', hodDebounce((e) => {
    hodAchState.keyword = e.target.value.trim(); hodAchState.page = 0; loadHodAchievements();
  }, 350));
  if (status) status.addEventListener('change', (e) => { hodAchState.status = e.target.value; hodAchState.page = 0; loadHodAchievements(); });
  if (cat) cat.addEventListener('change', (e) => { hodAchState.categoryCode = e.target.value; hodAchState.page = 0; loadHodAchievements(); });
  if (year) year.addEventListener('change', (e) => { hodAchState.academicYear = e.target.value; hodAchState.page = 0; loadHodAchievements(); });
  if (reset) reset.addEventListener('click', () => {
    hodAchState.keyword = ''; hodAchState.status = ''; hodAchState.categoryCode = ''; hodAchState.academicYear = ''; hodAchState.page = 0;
    if (search) search.value = ''; if (status) status.value = ''; if (cat) cat.value = ''; if (year) year.value = '';
    loadHodAchievements();
  });
}

async function loadHodAchievements() {
  const body = document.getElementById('hodAchBody');
  const pager = document.getElementById('hodAchPagination');
  if (body) body.innerHTML = `<tr><td colspan="6"><div style="padding:32px 0;"><div class="hod-spinner"></div></div></td></tr>`;
  if (pager) pager.innerHTML = '';

  const query = hodBuildQuery({
    keyword: hodAchState.keyword,
    status: hodAchState.status,
    categoryCode: hodAchState.categoryCode,
    academicYear: hodAchState.academicYear,
    page: hodAchState.page,
    size: hodAchState.size,
    sortBy: 'createdAt',
    sortDir: 'desc'
  });

  const res = await ApiClient.get(`/achievements/search?${query}`);

  if (!res.success) {
    hodSetAchCount(null);
    if (body) {
      const denied = res.status === 403;
      body.innerHTML = `<tr><td colspan="6"><div class="hod-state"><span class="material-symbols-outlined">${denied ? 'lock' : 'error'}</span><div class="hod-state-title">${denied ? 'Access denied' : 'Something went wrong'}</div><p class="hod-state-text">${escapeHtml(denied ? 'You do not have HOD privileges for this department.' : (res.message || 'Unable to load achievements.'))}</p></div></td></tr>`;
    }
    return;
  }

  const data = res.data || {};
  const items = data.content || [];
  hodSetAchCount(data.totalElements || 0);

  if (items.length === 0 && hodAchState.page > 0 && (data.totalElements || 0) > 0) {
    hodAchState.page = Math.max(0, (data.totalPages || 1) - 1);
    return loadHodAchievements();
  }

  hodRenderAchRows(items);
  hodRenderPagination(pager, data, (p) => { hodAchState.page = p; loadHodAchievements(); });
}

function hodSetAchCount(n) {
  const pill = document.getElementById('hodAchCount');
  if (!pill) return;
  pill.innerHTML = n === null
    ? `<span class="material-symbols-outlined">workspace_premium</span> — total`
    : `<span class="material-symbols-outlined">workspace_premium</span> ${n} total`;
}

function hodRenderAchRows(items) {
  const body = document.getElementById('hodAchBody');
  if (!body) return;

  const hasFilters = hodAchState.keyword || hodAchState.status || hodAchState.categoryCode || hodAchState.academicYear;
  if (!items.length) {
    body.innerHTML = hasFilters
      ? `<tr><td colspan="6"><div class="hod-state"><span class="material-symbols-outlined">search_off</span><div class="hod-state-title">No matching achievements</div><p class="hod-state-text">Try adjusting or resetting your filters.</p></div></td></tr>`
      : `<tr><td colspan="6"><div class="hod-state"><span class="material-symbols-outlined">workspace_premium</span><div class="hod-state-title">No achievements yet</div><p class="hod-state-text">Achievements submitted by your department will appear here.</p></div></td></tr>`;
    return;
  }

  body.innerHTML = '';
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
        <button class="hod-btn ${isPending ? 'hod-btn-primary' : 'hod-btn-outline'} hod-btn-sm" data-id="${item.id}">
          <span class="material-symbols-outlined">${isPending ? 'rate_review' : 'visibility'}</span>${isPending ? 'Review' : 'View'}
        </button>
      </td>`;
    body.appendChild(tr);
    tr.querySelector('button[data-id]').addEventListener('click', () => openHodReviewModal(item.id, loadHodAchievements));
  });
}
