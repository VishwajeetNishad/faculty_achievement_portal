/**
 * HOD Portal — Audit Logs (permission-gated by VIEW_AUDIT_LOGS).
 *
 * This screen only appears for an HOD whose Admin has granted VIEW_AUDIT_LOGS.
 * It reuses the SAME endpoint the Admin portal uses: GET /api/audit-logs.
 * The backend enforces "ROLE_ADMIN or VIEW_AUDIT_LOGS" on every request — this
 * page never decides access on its own; it only chooses whether to *show* data.
 *
 * Load order (from audit-logs.html): config → api → common → hod-common → hod-audit.
 * common.js already provides escapeHtml/showToast globally — do NOT redefine them.
 */

let hodAuditPage = 0;
const HOD_AUDIT_SIZE = 15;

document.addEventListener('DOMContentLoaded', async () => {
  // hod-common resolves this to the HOD user, or null if not an HOD / load failed.
  const me = await window.HOD.ready;
  if (!me) return;

  const body = document.getElementById('hodAuditBody');
  if (!body) return;

  // UX gate. If the HOD wasn't granted the permission, show a friendly panel
  // instead of firing a request that the backend would reject with 403 anyway.
  if (!hodCan('VIEW_AUDIT_LOGS')) {
    hodAuditDenied();
    return;
  }

  document.getElementById('hodAuditApply')?.addEventListener('click', () => hodAuditSearch(0));
  document.getElementById('hodAuditClear')?.addEventListener('click', () => {
    ['hodAuditAction', 'hodAuditEntity', 'hodAuditFrom', 'hodAuditTo'].forEach((id) => {
      const el = document.getElementById(id);
      if (el) el.value = '';
    });
    hodAuditSearch(0);
  });

  hodAuditSearch(0);
});

async function hodAuditSearch(page) {
  hodAuditPage = page;
  const body = document.getElementById('hodAuditBody');
  const pager = document.getElementById('hodAuditPager');
  if (!body) return;

  body.innerHTML = `<tr><td colspan="6"><div style="padding:40px 0;text-align:center;">
    <div class="hod-spinner"></div>
    <p style="margin-top:12px;color:var(--hod-on-surface-variant);">Loading audit records…</p>
  </div></td></tr>`;

  const params = new URLSearchParams();
  const action = document.getElementById('hodAuditAction')?.value || '';
  const entity = document.getElementById('hodAuditEntity')?.value || '';
  const from = document.getElementById('hodAuditFrom')?.value || '';
  const to = document.getElementById('hodAuditTo')?.value || '';
  if (action) params.set('action', action);
  if (entity) params.set('entityType', entity);
  if (from) params.set('fromDate', from);
  if (to) params.set('toDate', to);
  params.set('page', String(page));
  params.set('size', String(HOD_AUDIT_SIZE));
  params.set('sortBy', 'createdAt');
  params.set('sortDir', 'desc');

  const res = await ApiClient.get('/audit-logs?' + params.toString());

  if (!res.success) {
    const msg = res.status === 403
      ? 'Your account no longer has permission to view the audit trail.'
      : (res.message || 'Failed to load audit records.');
    body.innerHTML = `<tr><td colspan="6"><div class="hod-state">
      <span class="material-symbols-outlined">error</span>
      <div class="hod-state-title">Unable to load</div>
      <p class="hod-state-text">${escapeHtml(msg)}</p>
    </div></td></tr>`;
    hodAuditSetCount(0);
    if (pager) pager.innerHTML = '';
    return;
  }

  const data = res.data || {};
  const list = Array.isArray(data.content) ? data.content : [];
  hodAuditSetCount(data.totalElements || 0);

  if (list.length === 0) {
    body.innerHTML = `<tr><td colspan="6"><div class="hod-state">
      <span class="material-symbols-outlined">inbox</span>
      <div class="hod-state-title">No audit entries</div>
      <p class="hod-state-text">No records match the selected filters.</p>
    </div></td></tr>`;
    if (pager) pager.innerHTML = '';
    return;
  }

  body.innerHTML = list.map((item) => `
    <tr>
      <td data-label="Timestamp" style="white-space:nowrap;">${hodFormatDateTime(item.createdAt)}</td>
      <td data-label="Actor">
        <div style="font-weight:600;">${escapeHtml(item.actorName || 'System')}</div>
        <div class="hod-muted" style="font-size:12px;">${escapeHtml(item.actorEmail || '—')}</div>
      </td>
      <td data-label="Action">${hodAuditActionBadge(item.action)}</td>
      <td data-label="Entity & ID">
        <div style="font-weight:600;">${escapeHtml(item.entityType || 'SYSTEM')}</div>
        <div class="hod-muted" style="font-size:12px;">${item.entityId ? 'ID: ' + escapeHtml(String(item.entityId)) : '—'}</div>
      </td>
      <td data-label="Description" style="max-width:340px;word-break:break-word;">${escapeHtml(item.description || '—')}</td>
      <td data-label="IP Address" style="font-family:monospace;font-size:12px;color:var(--hod-on-surface-variant);">${escapeHtml(item.ipAddress || '—')}</td>
    </tr>`).join('');

  if (pager) hodRenderPagination(pager, data, (p) => hodAuditSearch(p));
}

function hodAuditSetCount(n) {
  const el = document.getElementById('hodAuditCount');
  if (el) el.textContent = `${n} record${n === 1 ? '' : 's'}`;
}

/**
 * Colour-code the action using the HOD badge palette:
 *  green  = a successful create/approve/grant, amber = neutral update, red = delete/reject/failure.
 * The action label itself is always the real AuditAction name (escaped).
 */
function hodAuditActionBadge(action) {
  const act = String(action || '').toUpperCase();
  const good = ['LOGIN_SUCCESS', 'ACHIEVEMENT_APPROVED', 'ACHIEVEMENT_CREATED', 'PROOF_UPLOADED', 'USER_CREATED', 'PERMISSION_GRANTED', 'DEPARTMENT_CREATED'];
  const bad = ['LOGIN_FAILURE', 'ACHIEVEMENT_DELETED', 'ACHIEVEMENT_REJECTED', 'PROOF_DELETED', 'PERMISSION_REVOKED', 'DEPARTMENT_DELETED'];
  let cls = 'hod-badge-pending';
  if (good.includes(act)) cls = 'hod-badge-approved';
  else if (bad.includes(act)) cls = 'hod-badge-rejected';
  return `<span class="hod-badge ${cls}">${escapeHtml(act || 'UNKNOWN')}</span>`;
}

function hodAuditDenied() {
  const body = document.getElementById('hodAuditBody');
  if (body) {
    body.innerHTML = `<tr><td colspan="6"><div class="hod-state">
      <span class="material-symbols-outlined">lock</span>
      <div class="hod-state-title">Permission required</div>
      <p class="hod-state-text">Viewing the institutional audit trail requires the <strong>VIEW_AUDIT_LOGS</strong> permission. Ask an administrator to grant it.</p>
    </div></td></tr>`;
  }
  hodAuditSetCount(0);
}
