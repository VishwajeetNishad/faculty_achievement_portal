/**
 * HOD Verification Queue controller.
 * GET /achievements/search?status=PENDING&... — department-scoped server-side
 * (departmentId is forced null for non-admins, so the UI cannot widen scope).
 * Review action opens the shared modal (hod-common.js); on decision, the queue reloads.
 */

const hodQueueState = { page: 0, size: 10, keyword: '', categoryCode: '', academicYear: '' };

document.addEventListener('DOMContentLoaded', initHodQueue);

async function initHodQueue() {
  const me = await window.HOD.ready;
  if (!me) return; // guard already rendered by hod-common.js

  hodPopulateCategoryFilter('hodCategoryFilter');
  await hodPopulateYearFilter('hodYearFilter');
  hodBindQueueFilters();
  await loadHodQueue();
}

function hodBindQueueFilters() {
  const search = document.getElementById('hodSearchInput');
  const cat = document.getElementById('hodCategoryFilter');
  const year = document.getElementById('hodYearFilter');
  const reset = document.getElementById('hodClearFilters');

  if (search) search.addEventListener('input', hodDebounce((e) => {
    hodQueueState.keyword = e.target.value.trim();
    hodQueueState.page = 0;
    loadHodQueue();
  }, 350));

  if (cat) cat.addEventListener('change', (e) => {
    hodQueueState.categoryCode = e.target.value;
    hodQueueState.page = 0;
    loadHodQueue();
  });

  if (year) year.addEventListener('change', (e) => {
    hodQueueState.academicYear = e.target.value;
    hodQueueState.page = 0;
    loadHodQueue();
  });

  if (reset) reset.addEventListener('click', () => {
    hodQueueState.keyword = ''; hodQueueState.categoryCode = ''; hodQueueState.academicYear = ''; hodQueueState.page = 0;
    if (search) search.value = '';
    if (cat) cat.value = '';
    if (year) year.value = '';
    loadHodQueue();
  });
}

async function loadHodQueue() {
  const body = document.getElementById('hodQueueBody');
  const pager = document.getElementById('hodQueuePagination');
  if (body) body.innerHTML = `<tr><td colspan="5"><div style="padding:32px 0;"><div class="hod-spinner"></div></div></td></tr>`;
  if (pager) pager.innerHTML = '';

  const query = hodBuildQuery({
    status: 'PENDING',
    keyword: hodQueueState.keyword,
    categoryCode: hodQueueState.categoryCode,
    academicYear: hodQueueState.academicYear,
    page: hodQueueState.page,
    size: hodQueueState.size,
    sortBy: 'createdAt',
    sortDir: 'desc'
  });

  const res = await ApiClient.get(`/achievements/search?${query}`);

  if (!res.success) {
    hodSetQueueCount(null);
    if (body) {
      const denied = res.status === 403;
      body.innerHTML = `<tr><td colspan="5"><div class="hod-state"><span class="material-symbols-outlined">${denied ? 'lock' : 'error'}</span><div class="hod-state-title">${denied ? 'Access denied' : 'Something went wrong'}</div><p class="hod-state-text">${escapeHtml(denied ? 'You do not have HOD privileges for this department.' : (res.message || 'Unable to load the verification queue.'))}</p></div></td></tr>`;
    }
    return;
  }

  const data = res.data || {};
  const items = data.content || [];
  hodSetQueueCount(data.totalElements || 0);

  // If a review cleared the last row on this page, step back a page.
  if (items.length === 0 && hodQueueState.page > 0 && (data.totalElements || 0) > 0) {
    hodQueueState.page = Math.max(0, (data.totalPages || 1) - 1);
    return loadHodQueue();
  }

  hodRenderQueueRows(items);
  hodRenderPagination(pager, data, (p) => { hodQueueState.page = p; loadHodQueue(); });
}

function hodSetQueueCount(n) {
  const pill = document.getElementById('hodQueueCount');
  if (!pill) return;
  if (n === null) { pill.innerHTML = `<span class="material-symbols-outlined">schedule</span> — pending`; return; }
  pill.innerHTML = `<span class="material-symbols-outlined">schedule</span> ${n} pending`;
}

function hodRenderQueueRows(items) {
  const body = document.getElementById('hodQueueBody');
  if (!body) return;

  const hasFilters = hodQueueState.keyword || hodQueueState.categoryCode || hodQueueState.academicYear;
  if (!items.length) {
    body.innerHTML = hasFilters
      ? `<tr><td colspan="5"><div class="hod-state"><span class="material-symbols-outlined">search_off</span><div class="hod-state-title">No matching submissions</div><p class="hod-state-text">Try adjusting or resetting your filters.</p></div></td></tr>`
      : `<tr><td colspan="5"><div class="hod-state"><span class="material-symbols-outlined">task_alt</span><div class="hod-state-title">All caught up</div><p class="hod-state-text">There are no pending submissions awaiting your review.</p></div></td></tr>`;
    return;
  }

  body.innerHTML = '';
  items.forEach((item) => {
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
      <td data-label="Submitted">${hodFormatDate(item.createdAt)}</td>
      <td data-label="Action" class="hod-td-right">
        <button class="hod-btn hod-btn-primary hod-btn-sm" data-id="${item.id}">
          <span class="material-symbols-outlined">rate_review</span> Review
        </button>
      </td>`;
    body.appendChild(tr);
    tr.querySelector('button[data-id]').addEventListener('click', () => openHodReviewModal(item.id, loadHodQueue));
  });
}
