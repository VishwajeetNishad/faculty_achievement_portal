/**
 * Faculty Achievement Portal — Admin Audit Log Controller (Step 20)
 * Connected to GET /api/audit-logs (ROLE_ADMIN strictly enforced on backend)
 */

let auditCurrentPage = 0;
const AUDIT_PAGE_SIZE = 15;

document.addEventListener('DOMContentLoaded', () => {
  if (document.getElementById('auditLogTableBody')) {
    initializeAuditLogs();
  }
});

async function initializeAuditLogs() {
  // Check user role from session
  const currentUser = JSON.parse(sessionStorage.getItem('currentUser') || '{}');
  const role = currentUser.role || '';
  if (!role.toUpperCase().includes('ADMIN')) {
    const tbody = document.getElementById('auditLogTableBody');
    if (tbody) {
      tbody.innerHTML = `<tr><td colspan="6" class="empty-state"><div class="empty-state-title" style="color:var(--danger-color);">Access Denied</div><p class="empty-state-text">Institutional security audit logs require Administrator privileges.</p></td></tr>`;
    }
    showToast('Administrator privileges required to view audit logs.', 'error');
    return;
  }

  // Initial fetch
  await runAuditSearch(0);

  // Filter handlers
  document.getElementById('auditApplyFiltersBtn')?.addEventListener('click', () => runAuditSearch(0));
  document.getElementById('auditClearFiltersBtn')?.addEventListener('click', () => {
    document.getElementById('auditFilterAction').value   = '';
    document.getElementById('auditFilterEntity').value   = '';
    document.getElementById('auditFilterFromDate').value = '';
    document.getElementById('auditFilterToDate').value   = '';
    runAuditSearch(0);
    showToast('Audit filters cleared', 'info');
  });
}

async function runAuditSearch(page) {
  auditCurrentPage = page;
  const tbody = document.getElementById('auditLogTableBody');
  if (!tbody) return;

  tbody.innerHTML = `<tr><td colspan="6" class="empty-state"><div class="spinner"></div><p style="margin-top:0.5rem;">Fetching system audit trail...</p></td></tr>`;

  const params = new URLSearchParams();
  const action   = document.getElementById('auditFilterAction')?.value || '';
  const entity   = document.getElementById('auditFilterEntity')?.value || '';
  const fromDate = document.getElementById('auditFilterFromDate')?.value || '';
  const toDate   = document.getElementById('auditFilterToDate')?.value || '';

  if (action)   params.set('action', action);
  if (entity)   params.set('entityType', entity);
  if (fromDate) params.set('fromDate', fromDate);
  if (toDate)   params.set('toDate', toDate);
  params.set('page', String(page));
  params.set('size', String(AUDIT_PAGE_SIZE));
  params.set('sortBy', 'createdAt');
  params.set('sortDir', 'desc');

  const res = await ApiClient.get('/audit-logs?' + params.toString());

  if (!res.success) {
    let msg = res.message || 'Failed to retrieve audit logs';
    if (res.status === 403) msg = 'Access Denied. Admin privileges required.';
    tbody.innerHTML = `<tr><td colspan="6" class="empty-state"><div class="empty-state-title" style="color:var(--danger-color);">Error</div><p class="empty-state-text">${escapeHtml(msg)}</p></td></tr>`;
    hideAuditPagination();
    return;
  }

  const data = res.data;
  const list = Array.isArray(data.content) ? data.content : [];

  const countBadge = document.getElementById('auditLogCountBadge');
  if (countBadge) countBadge.textContent = `${data.totalElements || 0} Records`;

  tbody.innerHTML = '';

  if (list.length === 0) {
    tbody.innerHTML = `<tr><td colspan="6" class="empty-state"><div class="empty-state-title">No Audit Log Entries</div><p class="empty-state-text">No audit log records match the selected search criteria.</p></td></tr>`;
    hideAuditPagination();
    return;
  }

  list.forEach(item => {
    const actionBadge = getActionBadgeHtml(item.action);
    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td data-label="Timestamp" style="white-space: nowrap; font-size: 0.83rem;">${formatAuditTime(item.createdAt)}</td>
      <td data-label="Actor">
        <div class="table-title-cell">${escapeHtml(item.actorName || 'System')}</div>
        <div class="table-subtext">${escapeHtml(item.actorEmail || 'N/A')}</div>
      </td>
      <td data-label="Action">${actionBadge}</td>
      <td data-label="Entity &amp; ID">
        <div class="table-title-cell">${escapeHtml(item.entityType || 'SYSTEM')}</div>
        <div class="table-subtext">${item.entityId ? 'ID: ' + item.entityId : 'N/A'}</div>
      </td>
      <td data-label="Description" style="max-width: 300px; word-break: break-word; font-size: 0.85rem;">${escapeHtml(item.description)}</td>
      <td data-label="IP Address" style="font-family: monospace; font-size: 0.83rem; color: #64748B;">${escapeHtml(item.ipAddress || '127.0.0.1')}</td>
    `;
    tbody.appendChild(tr);
  });

  renderAuditPagination(data);
}

function getActionBadgeHtml(action) {
  if (!action) return '<span class="badge">UNKNOWN</span>';
  const act = String(action).toUpperCase();
  if (act === 'LOGIN_SUCCESS' || act === 'ACHIEVEMENT_APPROVED') {
    return `<span class="badge badge-approved"><span class="badge-symbol">✓</span> ${act}</span>`;
  }
  if (act === 'LOGIN_FAILURE' || act === 'ACHIEVEMENT_DELETED' || act === 'PROOF_DELETED') {
    return `<span class="badge badge-rejected"><span class="badge-symbol">✕</span> ${act}</span>`;
  }
  if (act === 'ACHIEVEMENT_REJECTED' || act === 'ACHIEVEMENT_UPDATED' || act === 'PROFILE_UPDATED') {
    return `<span class="badge badge-pending"><span class="badge-symbol">●</span> ${act}</span>`;
  }
  return `<span class="badge" style="background:#E2E8F0; color:#1E293B;">${act}</span>`;
}

function renderAuditPagination(data) {
  const bar      = document.getElementById('auditPaginationBar');
  const info     = document.getElementById('auditPaginationInfo');
  const controls = document.getElementById('auditPaginationControls');
  if (!bar || !info || !controls) return;

  const { page, size, totalElements, totalPages, first, last } = data;
  if (totalElements === 0) { hideAuditPagination(); return; }

  bar.style.display = 'flex';
  const start = page * size + 1;
  const end   = Math.min(page * size + size, totalElements);
  info.textContent = `Showing ${start}–${end} of ${totalElements} results`;

  controls.innerHTML = '';
  const prevBtn = document.createElement('button');
  prevBtn.textContent = '‹ Prev'; prevBtn.disabled = first;
  prevBtn.addEventListener('click', () => runAuditSearch(page - 1));
  controls.appendChild(prevBtn);

  const startPage = Math.max(0, page - 2);
  const endPage   = Math.min(totalPages - 1, page + 2);
  for (let i = startPage; i <= endPage; i++) {
    const btn = document.createElement('button');
    btn.textContent = String(i + 1);
    if (i === page) btn.classList.add('active');
    btn.addEventListener('click', () => runAuditSearch(i));
    controls.appendChild(btn);
  }

  const nextBtn = document.createElement('button');
  nextBtn.textContent = 'Next ›'; nextBtn.disabled = last;
  nextBtn.addEventListener('click', () => runAuditSearch(page + 1));
  controls.appendChild(nextBtn);
}

function hideAuditPagination() {
  const bar = document.getElementById('auditPaginationBar');
  if (bar) bar.style.display = 'none';
}

function formatAuditTime(dateStr) {
  if (!dateStr) return '';
  const d = new Date(dateStr);
  return d.toLocaleString('en-GB', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

function escapeHtml(str) {
  if (!str) return '';
  return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
