/**
 * Faculty Achievements Controller — PDF Design Matching (Pages 09-22)
 * Handles Search, Filtering, Pagination, CSV Export, Adding Achievements, and Details Modal.
 */

let pendingDeleteId = null;
let currentPage = 0;
const PAGE_SIZE = 10;

document.addEventListener('DOMContentLoaded', () => {
  if (document.getElementById('achievementsTableBody')) {
    initializeAchievementsPage();
  }

  if (document.getElementById('addAchievementForm')) {
    initializeAddAchievementForm();
  }
});

// ─── Achievements List Page Controller ──────────────────────────────────────────

async function initializeAchievementsPage() {
  await loadCategoryOptions();
  
  // Search input with debounce / Enter key
  const searchInput = document.getElementById('searchInput') || document.getElementById('searchKeyword');
  if (searchInput) {
    searchInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') runSearch(0);
    });
    searchInput.addEventListener('input', () => {
      clearTimeout(window.searchDebounce);
      window.searchDebounce = setTimeout(() => runSearch(0), 400);
    });
  }

  // Filter dropdowns
  const statusFilter = document.getElementById('statusFilter') || document.getElementById('filterStatus');
  const catFilter = document.getElementById('categoryFilter') || document.getElementById('filterCategory');

  if (statusFilter) statusFilter.addEventListener('change', () => runSearch(0));
  if (catFilter) catFilter.addEventListener('change', () => runSearch(0));

  // CSV Export
  const exportBtn = document.getElementById('exportCsvBtn');
  if (exportBtn) exportBtn.addEventListener('click', exportCsv);

  // Manual reload
  const refreshBtn = document.getElementById('refreshBtn');
  if (refreshBtn) refreshBtn.addEventListener('click', refreshList);

  // Visibility modal save
  const saveVisibilityBtn = document.getElementById('saveVisibilityBtn');
  if (saveVisibilityBtn) saveVisibilityBtn.addEventListener('click', saveVisibilityChange);

  // Initial load
  await runSearch(0);
}

/**
 * Reloads the list without changing what the user is looking at.
 *
 * <p>Deliberately `runSearch(currentPage)` and not `runSearch(0)`: the filters
 * and the page number are part of what the person is currently reading, and a
 * refresh that silently jumped them back to page 1 would lose their place.
 *
 * <p>The button is disabled for the duration. Two clicks would fire two
 * searches whose replies can arrive out of order, and the slower one would win
 * and paint stale rows over fresh ones.
 */
async function refreshList() {
  const btn = document.getElementById('refreshBtn');

  if (btn) {
    if (btn.dataset.busy === 'true') return;
    btn.dataset.busy = 'true';
    btn.disabled = true;
  }

  try {
    await runSearch(currentPage);
  } finally {
    // In a finally block so a failed search cannot leave the button stuck
    // spinning with no way to try again.
    if (btn) {
      btn.dataset.busy = 'false';
      btn.disabled = false;
    }
  }
}

async function runSearch(page) {
  currentPage = page;
  const tableBody = document.getElementById('achievementsTableBody');
  if (!tableBody) return;

  tableBody.innerHTML = `<tr><td colspan="7" class="empty-state"><div class="spinner"></div><p style="margin-top:0.5rem; font-size:0.8rem; color:var(--text-secondary);">Loading achievements...</p></td></tr>`;

  const searchInput = document.getElementById('searchInput') || document.getElementById('searchKeyword');
  const statusFilter = document.getElementById('statusFilter') || document.getElementById('filterStatus');
  const catFilter = document.getElementById('categoryFilter') || document.getElementById('filterCategory');

  const params = new URLSearchParams();
  if (searchInput && searchInput.value.trim()) params.set('keyword', searchInput.value.trim());
  if (statusFilter && statusFilter.value) params.set('status', statusFilter.value);
  if (catFilter && catFilter.value) params.set('categoryId', catFilter.value);
  params.set('page', String(page));
  params.set('size', String(PAGE_SIZE));
  params.set('sortBy', 'createdAt');
  params.set('sortDir', 'desc');

  const res = await ApiClient.get('/achievements/search?' + params.toString());

  if (!res.success) {
    tableBody.innerHTML = `
      <tr>
        <td colspan="7" class="empty-state">
          <div class="empty-state-title" style="color: var(--color-danger);">Error Loading Records</div>
          <p class="empty-state-text">${escapeHtml(res.message || 'Unable to fetch achievements')}</p>
        </td>
      </tr>`;
    hidePagination();
    return;
  }

  const data = res.data;
  const list = Array.isArray(data.content) ? data.content : [];
  const totalCountElem = document.getElementById('tableTotalCount');
  if (totalCountElem) totalCountElem.textContent = `${data.totalElements || list.length} total achievements`;

  // The sidebar badge is written into the markup as a literal 0 and only
  // dashboard.js ever filled it — so on this page it sat at 0 while the card
  // header right next to it said "1 total achievements". Updated only when no
  // filter is applied, because a filtered total is not the record count the
  // nav badge claims to show.
  const sidebarCount = document.getElementById('sidebarAchievementCount');
  const filtersActive = params.has('keyword') || params.has('status') || params.has('categoryId');
  if (sidebarCount && !filtersActive && typeof data.totalElements === 'number') {
    sidebarCount.textContent = String(data.totalElements);
  }

  tableBody.innerHTML = '';

  if (list.length === 0) {
    tableBody.innerHTML = `
      <tr>
        <td colspan="7" class="empty-state">
          <div class="empty-state-title">No Achievements Found</div>
          <p class="empty-state-text">No records match the current filter. Try adjusting your search query.</p>
          <a href="add-achievement.html" class="btn btn-primary btn-sm">+ Add Achievement</a>
        </td>
      </tr>`;
    hidePagination();
    return;
  }

  list.forEach(item => {
    const tr = document.createElement('tr');
    const statusClass = (item.status || 'PENDING').toLowerCase();
    const statusLabel = item.status === 'APPROVED' ? '• Approved' : item.status === 'REJECTED' ? '• Rejected' : '• Pending';

    tr.innerHTML = `
      <td data-label="Achievement">
        <div class="table-title-cell">${escapeHtml(item.title)}</div>
        <div class="table-subtext">${escapeHtml(item.categoryName || item.categoryCode || '')}</div>
      </td>
      <td data-label="Category">${escapeHtml(item.categoryName || item.categoryCode || '—')}</td>
      <td data-label="Academic Year">${escapeHtml(item.academicYear || '—')}</td>
      <td data-label="Date">${formatDate(item.achievementDate)}</td>
      <td data-label="Status">
        <span class="badge-status-dot ${statusClass}">${statusLabel}</span>
      </td>
      <td data-label="Visibility">${renderVisibilityBadge(item.visibility)}</td>
      <td data-label="Actions" style="text-align: right;">
        <div style="display: inline-flex; gap: 0.35rem; justify-content: flex-end;">
          <button class="btn btn-outline btn-sm view-item-btn" data-id="${item.id}">View</button>
          <button class="btn btn-outline btn-sm visibility-item-btn" data-id="${item.id}" title="Change who can see this record">Visibility</button>
          ${item.proofDocumentUrl ? `<button class="btn btn-outline btn-sm view-proof-btn" data-id="${item.id}" title="View Proof PDF">📄</button>` : ''}
        </div>
      </td>
    `;
    tableBody.appendChild(tr);
  });

  // Attach handlers
  tableBody.querySelectorAll('.view-item-btn').forEach(btn =>
    btn.addEventListener('click', () => showAchievementDetailsModal(btn.getAttribute('data-id'))));
  tableBody.querySelectorAll('.visibility-item-btn').forEach(btn =>
    btn.addEventListener('click', () => openVisibilityModal(btn.getAttribute('data-id'))));
  tableBody.querySelectorAll('.view-proof-btn').forEach(btn =>
    btn.addEventListener('click', () => openProtectedProofPdf(btn.getAttribute('data-id'))));

  renderPagination(data);
}

function renderPagination(data) {
  const container = document.getElementById('paginationContainer') || document.getElementById('paginationBar');
  const info = document.getElementById('paginationInfo');
  const controls = document.getElementById('paginationControls');
  if (!container || !info || !controls) return;

  const { page, size, totalElements, totalPages, first, last } = data;
  if (totalElements === 0) { hidePagination(); return; }

  container.style.display = 'flex';
  const start = page * size + 1;
  const end = Math.min(page * size + size, totalElements);
  info.textContent = `Showing ${start}–${end} of ${totalElements} achievements`;

  controls.innerHTML = '';

  const prevBtn = document.createElement('button');
  prevBtn.innerHTML = '&lsaquo;';
  prevBtn.disabled = first;
  prevBtn.addEventListener('click', () => runSearch(page - 1));
  controls.appendChild(prevBtn);

  const startPage = Math.max(0, page - 2);
  const endPage = Math.min(totalPages - 1, page + 2);
  for (let i = startPage; i <= endPage; i++) {
    const pageBtn = document.createElement('button');
    pageBtn.textContent = String(i + 1);
    if (i === page) pageBtn.classList.add('active');
    pageBtn.addEventListener('click', () => runSearch(i));
    controls.appendChild(pageBtn);
  }

  const nextBtn = document.createElement('button');
  nextBtn.innerHTML = '&rsaquo;';
  nextBtn.disabled = last;
  nextBtn.addEventListener('click', () => runSearch(page + 1));
  controls.appendChild(nextBtn);
}

function hidePagination() {
  const container = document.getElementById('paginationContainer') || document.getElementById('paginationBar');
  if (container) container.style.display = 'none';
}

async function loadCategoryOptions() {
  const sel = document.getElementById('categoryFilter') || document.getElementById('filterCategory');
  if (!sel) return;

  const res = await ApiClient.get('/categories');
  if (res.success && Array.isArray(res.data)) {
    res.data.forEach(cat => {
      const opt = document.createElement('option');
      opt.value = cat.id;
      opt.textContent = cat.categoryName || cat.name;
      sel.appendChild(opt);
    });
  } else {
    [['1','Research Paper'],['2','Patent'],['3','Research Grant'],['4','Workshop / FDP'],['5','Award']].forEach(([v, t]) => {
      const opt = document.createElement('option'); opt.value = v; opt.textContent = t; sel.appendChild(opt);
    });
  }
}

async function exportCsv() {
  showToast('Preparing CSV export...', 'info');
  const searchInput = document.getElementById('searchInput') || document.getElementById('searchKeyword');
  const statusFilter = document.getElementById('statusFilter') || document.getElementById('filterStatus');
  const catFilter = document.getElementById('categoryFilter') || document.getElementById('filterCategory');

  const params = new URLSearchParams();
  if (searchInput && searchInput.value.trim()) params.set('keyword', searchInput.value.trim());
  if (statusFilter && statusFilter.value) params.set('status', statusFilter.value);
  if (catFilter && catFilter.value) params.set('categoryId', catFilter.value);

  const res = await ApiClient.downloadBlob('/achievements/export/csv?' + params.toString());
  if (res.success && res.objectUrl) {
    const a = document.createElement('a');
    a.href = res.objectUrl;
    a.download = 'my_achievements_' + new Date().toISOString().slice(0, 10) + '.csv';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    showToast('CSV exported successfully!', 'success');
  } else {
    showToast(res.message || 'Export failed', 'error');
  }
}

// ─── Modal Details View (Pages 19-22 Design) ──────────────────────────────────

async function showAchievementDetailsModal(id) {
  const res = await ApiClient.get(`/achievements/${id}`);
  if (!res.success || !res.data) {
    showToast(res.message || 'Failed to load details', 'error');
    return;
  }

  const item = res.data;
  const modalBody = document.getElementById('modalDetailContent') || document.getElementById('viewModalContent');
  if (!modalBody) return;

  const statusClass = (item.status || 'PENDING').toLowerCase();
  const statusLabel = item.status === 'APPROVED' ? '• Approved' : item.status === 'REJECTED' ? '• Rejected' : '• Pending Review';

  modalBody.innerHTML = `
    <div style="margin-bottom: 1.25rem;">
      <div style="display: flex; justify-content: space-between; align-items: flex-start; gap: 1rem; margin-bottom: 0.5rem;">
        <h4 style="font-size: 1.05rem; font-weight: 700; color: var(--text-primary); line-height: 1.3;">${escapeHtml(item.title)}</h4>
        <span class="badge-status-dot ${statusClass}">${statusLabel}</span>
      </div>
      <p style="font-size: 0.75rem; color: var(--text-secondary);">Record ID: ACH-${item.id} &bull; Submitted on ${formatDate(item.createdAt)}</p>
    </div>

    <!-- Overview Details Table -->
    <div style="background: var(--bg-main); border: 1px solid var(--border-color); border-radius: var(--radius-md); padding: 1rem; margin-bottom: 1rem;">
      <div style="display: grid; grid-template-columns: 140px 1fr; gap: 0.5rem; font-size: 0.8rem; margin-bottom: 0.4rem;">
        <span style="color: var(--text-secondary); font-weight: 600;">Category:</span>
        <span style="color: var(--text-primary); font-weight: 500;">${escapeHtml(item.categoryName || item.categoryCode || '—')}</span>
      </div>
      <div style="display: grid; grid-template-columns: 140px 1fr; gap: 0.5rem; font-size: 0.8rem; margin-bottom: 0.4rem;">
        <span style="color: var(--text-secondary); font-weight: 600;">Academic Year:</span>
        <span style="color: var(--text-primary);">${escapeHtml(item.academicYear || '—')}</span>
      </div>
      <div style="display: grid; grid-template-columns: 140px 1fr; gap: 0.5rem; font-size: 0.8rem; margin-bottom: 0.4rem;">
        <span style="color: var(--text-secondary); font-weight: 600;">Achievement Date:</span>
        <span style="color: var(--text-primary);">${formatDate(item.achievementDate)}</span>
      </div>
      <!-- Visibility belongs in the detail view too. This modal used to omit it
           entirely, so the only place it appeared was one badge in the table. -->
      <div style="display: grid; grid-template-columns: 140px 1fr; gap: 0.5rem; font-size: 0.8rem; margin-bottom: 0.4rem; align-items: center;">
        <span style="color: var(--text-secondary); font-weight: 600;">Visibility:</span>
        <span style="display: inline-flex; align-items: center; gap: 0.5rem; flex-wrap: wrap;">
          ${renderVisibilityBadge(item.visibility)}
          <button class="btn btn-outline btn-sm" onclick="closeModal('${document.getElementById('viewDetailModal') ? 'viewDetailModal' : 'viewModal'}'); openVisibilityModal(${item.id});">Change</button>
        </span>
      </div>
      ${item.description ? `
      <div style="display: grid; grid-template-columns: 140px 1fr; gap: 0.5rem; font-size: 0.8rem; margin-top: 0.5rem; border-top: 1px solid var(--border-color); padding-top: 0.5rem;">
        <span style="color: var(--text-secondary); font-weight: 600;">Description:</span>
        <span style="color: var(--text-primary); line-height: 1.4;">${escapeHtml(item.description)}</span>
      </div>` : ''}
    </div>

    <!-- Proof Document Card -->
    ${item.proofDocumentUrl ? `
    <div style="background: #FFF; border: 1px solid var(--border-color); border-radius: var(--radius-md); padding: 0.85rem 1rem; display: flex; align-items: center; justify-content: space-between; margin-bottom: 1rem;">
      <div style="display: flex; align-items: center; gap: 0.65rem;">
        <span style="font-size: 1.5rem;">📄</span>
        <div>
          <div style="font-size: 0.8rem; font-weight: 600; color: var(--text-primary);">Proof Document</div>
          <div style="font-size: 0.7rem; color: var(--text-secondary);">Attached certificate / approval</div>
        </div>
      </div>
      <button class="btn btn-outline btn-sm" onclick="openProtectedProofPdf(${item.id})">View PDF &rarr;</button>
    </div>` : ''}

    <!-- Reviewer Feedback / Rejection Note (Pages 19 & 21 Design) -->
    ${item.verificationComment ? `
    <div style="background: rgba(239, 68, 68, 0.08); border: 1px solid rgba(239, 68, 68, 0.3); border-radius: var(--radius-md); padding: 0.85rem 1rem; margin-top: 1rem;">
      <div style="font-size: 0.775rem; font-weight: 700; color: #B91C1C; margin-bottom: 0.25rem;">Reviewer / HOD Feedback:</div>
      <div style="font-size: 0.8rem; color: #7F1D1D; line-height: 1.4;">${escapeHtml(item.verificationComment)}</div>
    </div>` : ''}
  `;

  const modalId = document.getElementById('viewDetailModal') ? 'viewDetailModal' : 'viewModal';
  openModal(modalId);
}

async function openProtectedProofPdf(id) {
  showToast('Loading document...', 'info');
  const res = await ApiClient.downloadBlob(`/achievements/${id}/proof`);
  if (res.success && res.objectUrl) {
    window.open(res.objectUrl, '_blank');
  } else {
    showToast(res.message || 'Unable to open proof document', 'error');
  }
}

// ─── Change Visibility ────────────────────────────────────────────────────────

const VISIBILITY_LABELS = { PUBLIC: 'Public', UNLISTED: 'Unlisted Link', PRIVATE: 'Private' };

/** The four fields PUT /api/achievements/{id} validates as required. */
const REQUIRED_UPDATE_FIELDS = ['categoryId', 'title', 'achievementDate', 'academicYear'];

/**
 * The record the visibility modal is currently editing.
 *
 * <p>The whole record is kept, not just its id, because the backend has no
 * visibility-only endpoint — the one route is PUT /api/achievements/{id}, and
 * that is a <b>full replace</b>. It overwrites category, title, description,
 * keywords, date, academic year and proof URL from whatever the request body
 * holds. So a body carrying only the new visibility does not leave the rest
 * alone; it wipes it. Keeping the server's own copy is what lets every other
 * field go back untouched.
 */
let visibilityRecord = null;

/**
 * Loads a record and opens the visibility modal for it.
 *
 * <p>Re-fetched rather than read out of the table row: the row carries only the
 * columns the list draws, and the update needs the whole record. Re-reading also
 * means a change someone made in another tab is picked up instead of being
 * silently overwritten with values from a stale row.
 */
async function openVisibilityModal(id) {
  const modal = document.getElementById('visibilityModal');
  if (!modal) return;

  const res = await ApiClient.get(`/achievements/${id}`);
  if (!res.success || !res.data) {
    showToast(res.message || 'Failed to load this record', 'error');
    return;
  }

  const item = res.data;

  /* If the server did not return one of the required fields, resending the
     record would either be rejected as invalid or — the worse outcome — succeed
     with that field blanked. Stop here and say which field is missing, rather
     than send a body that damages the record. */
  const missing = REQUIRED_UPDATE_FIELDS.filter(
    (field) => item[field] === null || item[field] === undefined || item[field] === ''
  );

  if (missing.length > 0) {
    showToast(
      `This record cannot be updated from here — the server did not return: ${missing.join(', ')}.`,
      'error'
    );
    return;
  }

  visibilityRecord = item;
  const current = (item.visibility || 'PRIVATE').toUpperCase();

  const titleEl = document.getElementById('visibilityModalTitle');
  const metaEl = document.getElementById('visibilityModalMeta');
  if (titleEl) titleEl.textContent = item.title;
  if (metaEl) metaEl.textContent = ` — currently ${VISIBILITY_LABELS[current] || current}`;

  // Preselect what the record already is, so opening the modal and pressing
  // Save without touching anything is a no-op instead of a silent reset.
  // Assigned to .onchange rather than addEventListener: this runs again every
  // time the modal opens, and addEventListener would stack a duplicate handler
  // on each open.
  modal.querySelectorAll('input[name="editVisibility"]').forEach((radio) => {
    radio.checked = radio.value === current;
    radio.onchange = updateVisibilityNotes;
  });

  updateVisibilityNotes();
  openModal('visibilityModal');
}

/**
 * Shows the consequences of the selected option before it is saved, not after.
 *
 * <p>Two of them are easy to get wrong: choosing Public on a record that is not
 * approved yet does nothing visible, and moving off Unlisted Link really does
 * kill every share link that was handed out.
 */
function updateVisibilityNotes() {
  const modal = document.getElementById('visibilityModal');
  const pendingNote = document.getElementById('visibilityPendingNote');
  const revokeNote = document.getElementById('visibilityRevokeNote');
  if (!modal || !visibilityRecord) return;

  const selected = modal.querySelector('input[name="editVisibility"]:checked');
  const chosen = selected ? selected.value : null;
  const current = (visibilityRecord.visibility || 'PRIVATE').toUpperCase();
  const status = visibilityRecord.status || 'PENDING';

  if (pendingNote) {
    if (chosen === 'PUBLIC' && status === 'APPROVED') {
      pendingNote.textContent = 'This record is approved, so saving Public lists it on the public NIET '
        + 'research site straight away, together with your name and department.';
      pendingNote.style.display = 'block';
    } else if (chosen === 'PUBLIC' && status === 'REJECTED') {
      pendingNote.textContent = 'This record was rejected, so Public will not put it on the public site. '
        + 'Only approved records are ever listed there.';
      pendingNote.style.display = 'block';
    } else if (chosen === 'PUBLIC') {
      pendingNote.textContent = 'This record is still awaiting review, so Public will not show it on the '
        + 'public site yet. It appears there once your HOD approves it.';
      pendingNote.style.display = 'block';
    } else {
      pendingNote.style.display = 'none';
    }
  }

  if (revokeNote) {
    if (current === 'UNLISTED' && chosen && chosen !== 'UNLISTED') {
      revokeNote.textContent = 'Moving away from Unlisted Link revokes every share link you created for '
        + 'this record. Anyone still holding one will no longer be able to open it.';
      revokeNote.style.display = 'block';
    } else {
      revokeNote.style.display = 'none';
    }
  }
}

async function saveVisibilityChange() {
  if (!visibilityRecord) return;

  const modal = document.getElementById('visibilityModal');
  const selected = modal ? modal.querySelector('input[name="editVisibility"]:checked') : null;
  if (!selected) {
    showToast('Please choose a visibility option', 'error');
    return;
  }

  const next = selected.value;
  const current = (visibilityRecord.visibility || 'PRIVATE').toUpperCase();
  const status = visibilityRecord.status || 'PENDING';

  // Nothing changed. Skipping the request keeps the audit log free of entries
  // that record no actual change.
  if (next === current) {
    closeModal('visibilityModal');
    showToast(`Visibility is already set to ${VISIBILITY_LABELS[next] || next}.`, 'info');
    return;
  }

  const btn = document.getElementById('saveVisibilityBtn');
  const originalLabel = btn ? btn.textContent : '';
  if (btn) {
    btn.disabled = true;
    btn.textContent = 'Saving...';
  }

  /* Every field this endpoint replaces is sent back exactly as the server
     returned it, with only `visibility` different. Leaving any of them out of
     the body would not preserve the stored value — it would overwrite it with
     null. See the note on visibilityRecord above. */
  const res = await ApiClient.put(`/achievements/${visibilityRecord.id}`, {
    categoryId: visibilityRecord.categoryId,
    title: visibilityRecord.title,
    description: visibilityRecord.description,
    keywords: visibilityRecord.keywords,
    achievementDate: visibilityRecord.achievementDate,
    academicYear: visibilityRecord.academicYear,
    proofDocumentUrl: visibilityRecord.proofDocumentUrl,
    visibility: next
  });

  if (btn) {
    btn.disabled = false;
    btn.textContent = originalLabel;
  }

  if (!res.success) {
    showToast(res.message || 'Could not change the visibility', 'error');
    return;
  }

  closeModal('visibilityModal');

  const label = VISIBILITY_LABELS[next] || next;
  if (next === 'PUBLIC' && status === 'APPROVED') {
    showToast('Visibility set to Public — this record is now listed on the public research site.', 'success');
  } else if (next === 'PUBLIC') {
    showToast('Visibility set to Public. It will appear on the public site once the record is approved.', 'success');
  } else if (current === 'UNLISTED') {
    showToast(`Visibility set to ${label}. Existing share links for this record have been revoked.`, 'success');
  } else {
    showToast(`Visibility set to ${label}.`, 'success');
  }

  visibilityRecord = null;

  // Repaint so the badge in the table matches what was just saved.
  await runSearch(currentPage);
}

// ─── Add Achievement Form Controller ───────────────────────────────────────────

function initializeAddAchievementForm() {
  const form = document.getElementById('addAchievementForm');
  if (!form) return;

  wireSharingControls();

  form.addEventListener('submit', async (e) => {
    e.preventDefault();

    const catInput = document.getElementById('achievementCategory');
    const titleInput = document.getElementById('achievementTitle');
    const dateInput = document.getElementById('achievementDate');
    const yearInput = document.getElementById('academicYear');
    const descInput = document.getElementById('achievementDesc') || document.getElementById('achievementDescription');
    const keywordsInput = document.getElementById('achievementKeywords');
    const fileInput = document.getElementById('proofFileInput');

    const categoryId = catInput ? catInput.value : '1';
    const title = titleInput ? titleInput.value.trim() : '';
    const date = dateInput ? dateInput.value : '';
    const academicYear = yearInput ? yearInput.value : '2024-2025';
    const description = descInput ? descInput.value.trim() : '';
    const keywords = keywordsInput ? keywordsInput.value.trim() : '';
    const visibility = getSelectedVisibility();

    if (!title) {
      showToast('Please enter the achievement title', 'error');
      if (titleInput) titleInput.focus();
      return;
    }

    if (!date) {
      showToast('Please select the achievement date', 'error');
      if (dateInput) dateInput.focus();
      return;
    }

    // A custom expiry must actually be filled in, and must be in the future.
    // The server enforces this too — this check only saves a round trip and
    // gives the message next to the field the person is looking at.
    const durationSel = document.getElementById('shareDuration');
    const customInput = document.getElementById('shareCustomExpiry');
    if (visibility === 'UNLISTED' && durationSel && durationSel.value === 'CUSTOM') {
      if (!customInput || !customInput.value) {
        showToast('Please choose the date and time the link should expire', 'error');
        if (customInput) customInput.focus();
        return;
      }
      if (new Date(customInput.value) <= new Date()) {
        showToast('The expiry date must be in the future', 'error');
        customInput.focus();
        return;
      }
    }

    const submitBtn = document.getElementById('submitBtn') || document.getElementById('submitAchievementBtn');
    if (submitBtn) {
      submitBtn.disabled = true;
      submitBtn.textContent = 'Submitting Record...';
    }

    try {
      const payload = {
        categoryId: parseInt(categoryId, 10),
        title,
        description,
        achievementDate: date,
        academicYear,
        keywords: keywords || null,
        visibility,
        proofDocumentUrl: null
      };

      const res = await ApiClient.post('/achievements', payload);

      if (!res.success || !res.data) {
        showToast(res.message || 'Failed to submit achievement record.', 'error');
        if (submitBtn) {
          submitBtn.disabled = false;
          submitBtn.textContent = 'Submit for Review';
        }
        return;
      }

      const createdId = res.data.id;

      // Handle PDF Proof file upload if selected
      if (fileInput && fileInput.files && fileInput.files.length > 0) {
        const file = fileInput.files[0];
        if (submitBtn) submitBtn.textContent = 'Uploading Proof Document...';

        const formData = new FormData();
        formData.append('file', file);
        const uploadRes = await ApiClient.upload(`/achievements/${createdId}/proof`, formData);

        if (uploadRes.success) {
          showToast('Achievement submitted with proof document!', 'success');
        } else {
          showToast(`Achievement created, but proof upload failed: ${uploadRes.message}`, 'warning');
        }
      } else {
        showToast('Achievement submitted for review successfully!', 'success');
      }

      // The share link is created AFTER the proof upload, so that ticking
      // "include the proof document" actually has a document to point at.
      if (visibility === 'UNLISTED') {
        if (submitBtn) submitBtn.textContent = 'Generating Share Link...';
        const linkRes = await createShareLinkForNewRecord(createdId);
        if (linkRes) {
          // Hand the person their link instead of navigating away from it.
          form.reset();
          showGeneratedShareLink(linkRes);
          if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.textContent = 'Submit for Review';
          }
          return;
        }
      }

      form.reset();
      setTimeout(() => {
        window.location.href = 'achievements.html';
      }, 800);

    } catch (err) {
      console.error('Submit error:', err);
      showToast('Error submitting achievement record', 'error');
      if (submitBtn) {
        submitBtn.disabled = false;
        submitBtn.textContent = 'Submit for Review';
      }
    }
  });
}

/** Which of the three visibility radios is selected; PRIVATE if the fieldset is absent. */
function getSelectedVisibility() {
  const checked = document.querySelector('input[name="visibility"]:checked');
  return checked ? checked.value : 'PRIVATE';
}

/**
 * Show the link options only for "Unlisted", and the custom date box only for
 * "Custom". Purely cosmetic — the backend validates the real values regardless
 * of what this does.
 */
function wireSharingControls() {
  const radios = document.querySelectorAll('input[name="visibility"]');
  const linkOptions = document.getElementById('shareLinkOptions');
  const durationSel = document.getElementById('shareDuration');
  const customWrap = document.getElementById('customExpiryWrap');

  if (radios.length && linkOptions) {
    const sync = () => {
      linkOptions.style.display = getSelectedVisibility() === 'UNLISTED' ? 'block' : 'none';
    };
    radios.forEach(r => r.addEventListener('change', sync));
    sync();
  }

  if (durationSel && customWrap) {
    const syncCustom = () => {
      customWrap.style.display = durationSel.value === 'CUSTOM' ? 'block' : 'none';
    };
    durationSel.addEventListener('change', syncCustom);
    syncCustom();
  }
}

/** POST the share link for a record that was just created. Returns the link, or null. */
async function createShareLinkForNewRecord(achievementId) {
  const durationSel = document.getElementById('shareDuration');
  const customInput = document.getElementById('shareCustomExpiry');
  const includeProof = document.getElementById('shareIncludeProof');

  const duration = durationSel ? durationSel.value : 'TWENTY_FOUR_HOURS';

  const body = {
    duration,
    includeProofDocument: !!(includeProof && includeProof.checked)
  };
  // datetime-local already gives "YYYY-MM-DDTHH:mm", which is exactly what
  // LocalDateTime parses — do not add a timezone or the server rejects it.
  if (duration === 'CUSTOM' && customInput && customInput.value) {
    body.customExpiresAt = customInput.value.length === 16 ? customInput.value + ':00' : customInput.value;
  }

  const res = await ApiClient.post(`/achievements/${achievementId}/share`, body);
  if (!res.success || !res.data) {
    showToast(res.message || 'Record saved, but the share link could not be created. You can create one from My Shared Research.', 'warning');
    return null;
  }
  return res.data;
}

/** Put the finished link on screen with a Copy button. */
function showGeneratedShareLink(link) {
  const host = document.getElementById('shareLinkOptions');
  if (!host) return;

  const expiryText = link.permanent
    ? 'This link never expires. Revoke it when you no longer need it.'
    : `This link expires on ${formatDateTime(link.expiresAt)}.`;

  host.innerHTML = `
    <div class="share-warning" style="background: rgba(16,185,129,0.09); border-color: #A7F3D0; color: #065F46;">
      <span class="share-warning-icon">✅</span>
      <span>
        <strong>Your share link is ready.</strong> ${escapeHtml(expiryText)}
        ${link.includeProofDocument ? ' The proof document is included.' : ' The proof document is <strong>not</strong> included.'}
      </span>
    </div>
    <div class="share-link-box">
      <input type="text" id="generatedShareUrl" class="form-control" readonly value="${escapeHtml(link.shareUrl || '')}">
      <button type="button" class="btn btn-primary btn-sm" id="copyShareUrlBtn">Copy Link</button>
    </div>
    <p style="font-size: 0.72rem; color: var(--text-secondary); margin-top: 0.6rem;">
      You can copy or revoke this link any time from
      <a href="shared-research.html" style="color: var(--primary-color); font-weight: 600;">My Shared Research</a>.
    </p>
  `;
  host.style.display = 'block';
  host.scrollIntoView({ behavior: 'smooth', block: 'center' });

  const copyBtn = document.getElementById('copyShareUrlBtn');
  if (copyBtn) copyBtn.addEventListener('click', () => copyShareUrl(link.shareUrl));
}

/**
 * Copy to the clipboard. navigator.clipboard needs a secure context, which
 * plain http://localhost happens to count as — but a LAN address like
 * http://192.168.x.x does not, so the select-and-execCommand fallback is what
 * actually runs when this is demonstrated from another machine.
 */
async function copyShareUrl(url) {
  if (!url) return;
  try {
    await navigator.clipboard.writeText(url);
    showToast('Link copied to clipboard', 'success');
  } catch (_) {
    const field = document.getElementById('generatedShareUrl');
    if (field) {
      field.select();
      field.setSelectionRange(0, 99999);
      try {
        document.execCommand('copy');
        showToast('Link copied to clipboard', 'success');
        return;
      } catch (_e) { /* fall through */ }
    }
    showToast('Could not copy automatically — select the link and copy it manually.', 'warning');
  }
}

function formatDate(dateStr) {
  if (!dateStr) return '—';
  const d = new Date(dateStr);
  return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
}

/** Date plus time, for link expiry where the hour genuinely matters. */
function formatDateTime(dateStr) {
  if (!dateStr) return '—';
  const d = new Date(dateStr);
  if (isNaN(d.getTime())) return '—';
  return d.toLocaleString('en-GB', {
    day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit'
  });
}

/**
 * The visibility badge for the list. Records created before this feature
 * existed have no visibility set, and the server treats a missing value as
 * PRIVATE — so the badge says the same rather than showing a blank cell.
 */
function renderVisibilityBadge(visibility) {
  const v = (visibility || 'PRIVATE').toUpperCase();
  const label = v === 'PUBLIC' ? 'Public' : v === 'UNLISTED' ? 'Unlisted' : 'Private';
  return `<span class="badge-visibility ${v.toLowerCase()}">${label}</span>`;
}

function escapeHtml(str) {
  if (!str) return '';
  return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
