/**
 * HOD Notifications controller.
 * GET /notifications (paged), PATCH /notifications/{id}/read, PATCH /notifications/read-all.
 * Reads/updates the shared bell badge via refreshUnreadBadge() (common.js).
 */

const hodNotifState = { page: 0, size: 10 };

document.addEventListener('DOMContentLoaded', initHodNotifications);

async function initHodNotifications() {
  const me = await window.HOD.ready;
  if (!me) return;

  const markAll = document.getElementById('hodMarkAllBtn');
  if (markAll) markAll.addEventListener('click', hodMarkAllRead);

  await loadHodNotifications();
}

function hodNotifMeta(type) {
  switch (String(type || '').toUpperCase()) {
    case 'ACHIEVEMENT_APPROVED': return { icon: 'check_circle', cls: 'approved' };
    case 'ACHIEVEMENT_REJECTED': return { icon: 'cancel', cls: 'rejected' };
    case 'VERIFICATION_REQUIRED': return { icon: 'fact_check', cls: 'pending' };
    case 'ACHIEVEMENT_SUBMITTED': return { icon: 'upload_file', cls: 'tertiary' };
    default: return { icon: 'notifications', cls: 'muted' };
  }
}

async function loadHodNotifications() {
  const list = document.getElementById('hodNotifList');
  const pager = document.getElementById('hodNotifPagination');
  if (list) list.innerHTML = `<div style="padding:32px 0;"><div class="hod-spinner"></div></div>`;
  if (pager) pager.innerHTML = '';

  const res = await ApiClient.getNotifications(hodNotifState.page, hodNotifState.size);

  if (!res.success) {
    if (list) list.innerHTML = `<div class="hod-state"><span class="material-symbols-outlined">error</span><div class="hod-state-title">Something went wrong</div><p class="hod-state-text">${escapeHtml(res.message || 'Unable to load notifications.')}</p></div>`;
    return;
  }

  const data = res.data || {};
  const items = data.content || [];

  if (items.length === 0 && hodNotifState.page > 0 && (data.totalElements || 0) > 0) {
    hodNotifState.page = Math.max(0, (data.totalPages || 1) - 1);
    return loadHodNotifications();
  }

  hodRenderNotifications(items);
  hodRenderPagination(pager, data, (p) => { hodNotifState.page = p; loadHodNotifications(); });
}

function hodRenderNotifications(items) {
  const list = document.getElementById('hodNotifList');
  if (!list) return;

  if (!items.length) {
    list.innerHTML = `<div class="hod-state"><span class="material-symbols-outlined">notifications_off</span><div class="hod-state-title">No notifications</div><p class="hod-state-text">You're all caught up. New updates will appear here.</p></div>`;
    return;
  }

  list.innerHTML = '';
  items.forEach((n) => {
    const meta = hodNotifMeta(n.notificationType);
    const isRead = n.isRead === true;
    const timeAgo = (typeof formatTimeAgo === 'function') ? formatTimeAgo(n.createdAt) : hodFormatDateTime(n.createdAt);
    const hasAch = n.achievementId !== null && n.achievementId !== undefined;

    const row = document.createElement('div');
    row.className = `hod-notif${isRead ? '' : ' unread'}`;
    row.innerHTML = `
      <div class="hod-notif-icon ${meta.cls}"><span class="material-symbols-outlined">${meta.icon}</span></div>
      <div class="hod-notif-body">
        <div class="hod-notif-top">
          <span class="hod-notif-title">${escapeHtml(n.title || 'Notification')}</span>
          <span class="hod-notif-time">${escapeHtml(timeAgo)}</span>
        </div>
        <p class="hod-notif-msg">${escapeHtml(n.message || '')}</p>
        ${hasAch ? `<span class="hod-notif-link"><span class="material-symbols-outlined">visibility</span> View achievement</span>` : ''}
      </div>
      ${isRead ? '' : '<span class="hod-notif-dot" aria-label="Unread"></span>'}`;

    row.addEventListener('click', async () => {
      if (!isRead) await hodMarkOneRead(n.id, row);
      if (hasAch) openHodReviewModal(n.achievementId, null);
    });

    list.appendChild(row);
  });
}

async function hodMarkOneRead(id, row) {
  const res = await ApiClient.markNotificationRead(id);
  if (!res.success) { showToast(res.message || 'Could not mark as read.', 'error'); return; }
  if (row) {
    row.classList.remove('unread');
    const dot = row.querySelector('.hod-notif-dot');
    if (dot) dot.remove();
  }
  if (typeof refreshUnreadBadge === 'function') refreshUnreadBadge();
}

async function hodMarkAllRead() {
  const btn = document.getElementById('hodMarkAllBtn');
  if (btn) { btn.disabled = true; }
  const res = await ApiClient.markAllNotificationsRead();
  if (btn) { btn.disabled = false; }

  if (!res.success) { showToast(res.message || 'Could not mark all as read.', 'error'); return; }
  showToast('All notifications marked as read.', 'success');
  if (typeof refreshUnreadBadge === 'function') refreshUnreadBadge();
  await loadHodNotifications();
}
