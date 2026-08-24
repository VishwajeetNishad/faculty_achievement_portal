/**
 * Faculty Achievement Portal — Reusable UI Components, Toast Engine, and Authentication Session Helpers
 */

function escapeHtml(str) {
  if (str === null || str === undefined) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

// Toast Notification System
function showToast(message, type = 'info', duration = 4000) {
  let toastContainer = document.getElementById('toastContainer');
  if (!toastContainer) {
    toastContainer = document.createElement('div');
    toastContainer.id = 'toastContainer';
    toastContainer.style.cssText = `
      position: fixed;
      top: 1.5rem;
      right: 1.5rem;
      z-index: 9999;
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
      max-width: 360px;
    `;
    document.body.appendChild(toastContainer);
  }

  const toast = document.createElement('div');
  toast.className = `alert alert-${type === 'error' ? 'danger' : type}`;
  toast.style.cssText = `
    box-shadow: 0 10px 15px -3px rgba(0,0,0,0.1);
    margin-bottom: 0;
    animation: slideIn 0.3s ease;
  `;
  toast.innerHTML = `<div>${escapeHtml(message)}</div>`;

  toastContainer.appendChild(toast);

  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transition = 'opacity 0.3s ease';
    setTimeout(() => toast.remove(), 300);
  }, duration);
}

// Modal Helpers
function openModal(modalId) {
  const modal = document.getElementById(modalId);
  if (modal) {
    modal.classList.add('active');
    modal.setAttribute('aria-hidden', 'false');
  }
}

function closeModal(modalId) {
  const modal = document.getElementById(modalId);
  if (modal) {
    modal.classList.remove('active');
    modal.setAttribute('aria-hidden', 'true');
  }
}

// ─────────────────────────────────────────────────────────────────────────────
// Permission helpers — FOR SHOWING AND HIDING UI ONLY, NEVER FOR SECURITY
//
// The signed-in user's permission codes come from GET /api/auth/me. They let us
// hide buttons a user cannot use, so the interface does not offer actions that
// would only fail with "Access denied".
//
// This is NOT a security boundary. Anything in the browser can be edited by the
// person using it — someone could set CURRENT_PERMISSIONS to every code in the
// console. It would change nothing: the backend re-checks the real permission,
// read fresh from the database, on every single request. Hiding a button is a
// courtesy; the server is what actually says no.
// ─────────────────────────────────────────────────────────────────────────────

const PERMISSIONS_STORAGE_KEY = 'currentPermissions';

let permissionsLoadPromise = null;

window.CURRENT_PERMISSIONS = (() => {
  try {
    const stored = JSON.parse(sessionStorage.getItem(PERMISSIONS_STORAGE_KEY) || '[]');
    return Array.isArray(stored) ? stored : [];
  } catch (e) {
    return [];
  }
})();

/**
 * True if the signed-in user holds this permission code.
 * Administrators receive all codes from the backend, so no special case here.
 */
function can(permissionCode) {
  return Array.isArray(window.CURRENT_PERMISSIONS)
    && window.CURRENT_PERMISSIONS.indexOf(permissionCode) !== -1;
}

/**
 * Refreshes the cached permission list from the backend.
 *
 * <p>The cached copy is read synchronously above so `can()` works the instant a
 * page script runs. This call then brings it up to date, which matters because
 * an administrator can grant or revoke a permission at any time — the backend
 * honours the change immediately, and this keeps the buttons in step.
 *
 * Pages that must have accurate permissions before their first render should
 * `await ensurePermissionsLoaded()` before drawing.
 */
async function ensurePermissionsLoaded() {
  if (!sessionStorage.getItem('accessToken')) {
    window.CURRENT_PERMISSIONS = [];
    return window.CURRENT_PERMISSIONS;
  }

  // Several scripts on a page may ask for this at once. Sharing one in-flight
  // request means /auth/me is called only once per page load.
  if (!permissionsLoadPromise) {
    permissionsLoadPromise = ApiClient.get('/auth/me').then(res => {
      if (res.success && res.data) {
        // Keep the whole profile, not just the permission list. The header
        // identity widget needs the real name, role and department, and asking
        // /auth/me a second time for information we already have would be waste.
        window.CURRENT_USER_PROFILE = res.data;
        applyIdentityWidget();

        if (Array.isArray(res.data.permissions)) {
          window.CURRENT_PERMISSIONS = res.data.permissions;
          sessionStorage.setItem(PERMISSIONS_STORAGE_KEY, JSON.stringify(res.data.permissions));
        }
      }
      return window.CURRENT_PERMISSIONS;
    });
  }

  return permissionsLoadPromise;
}

/**
 * Fills the header identity widget from the signed-in user's real profile.
 *
 * <p>A page marks its widget up with data-identity="name" / "role" / "initials"
 * and leaves the text blank. Nothing is hardcoded: if the profile has not
 * arrived yet the widget simply stays empty rather than showing an invented
 * name that belongs to nobody.
 */
function applyIdentityWidget(root) {
  const me = window.CURRENT_USER_PROFILE;
  if (!me) return;

  const scope = root || document;
  const roleLabel = ROLE_DISPLAY_NAME[String(me.role || '').toUpperCase()] || me.role || '';
  const department = me.departmentCode ? ` · ${me.departmentCode}` : '';

  scope.querySelectorAll('[data-identity]').forEach(el => {
    switch (el.getAttribute('data-identity')) {
      case 'name':     el.textContent = me.fullName || me.email || ''; break;
      case 'role':     el.textContent = roleLabel + department; break;
      case 'initials': el.textContent = initialsFrom(me.fullName || me.email); break;
      case 'email':    el.textContent = me.email || ''; break;
    }
  });
}

/** Role names as a person would say them, not as the database stores them. */
const ROLE_DISPLAY_NAME = {
  ROLE_ADMIN: 'Administrator',
  ADMIN: 'Administrator',
  ROLE_HOD: 'Head of Department',
  HOD: 'Head of Department',
  ROLE_FACULTY: 'Faculty',
  FACULTY: 'Faculty'
};

/** "Rajesh Kumar" → "RK". Falls back to the first letter for a single word. */
function initialsFrom(name) {
  const parts = String(name || '').trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '';
  if (parts.length === 1) return parts[0].charAt(0).toUpperCase();
  return (parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
}

/**
 * Hides every element carrying data-requires-permission="CODE" that the current
 * user does not hold. Lets a page declare its gating in HTML instead of JS.
 *
 * <p>Several codes may be listed, separated by commas, and the element is shown
 * when the user holds ANY of them — for example the "Add User" button is useful
 * to somebody who can create only Heads of Department, not just to somebody who
 * can create Faculty.
 *
 * <p>This is presentation only. Hiding a button stops it being offered by
 * mistake; it does not stop anybody reaching the endpoint, which is why the
 * backend re-checks the permission from the database on every request.
 */
function applyPermissionVisibility(root) {
  const scope = root || document;
  scope.querySelectorAll('[data-requires-permission]').forEach(el => {
    const required = el.getAttribute('data-requires-permission');
    if (!required) return;

    const codes = required.split(',').map(c => c.trim()).filter(Boolean);
    const allowed = codes.some(code => can(code));

    if (!allowed) {
      el.style.display = 'none';
    }
  });
}

// DOM Ready Event Attachments
document.addEventListener('DOMContentLoaded', () => {

  // Global Sign Out Listener
  const signOutBtn = document.getElementById('signOutBtn') || document.querySelector('a[href*="login.html"]');
  if (signOutBtn) {
    signOutBtn.addEventListener('click', async (e) => {
      e.preventDefault();
      try {
        await ApiClient.post('/auth/logout', {});
      } catch (err) {
        // Ignore network errors during logout
      }
      sessionStorage.removeItem('accessToken');
      sessionStorage.removeItem('currentUser');
      sessionStorage.removeItem(PERMISSIONS_STORAGE_KEY);
      window.CURRENT_PERMISSIONS = [];
      window.CURRENT_USER_PROFILE = null;
      showToast('You have been signed out.', 'info');
      setTimeout(() => {
        const isSubdir = window.location.pathname.includes('/admin/') || window.location.pathname.includes('/hod/');
        window.location.href = isSubdir ? '../login.html' : 'login.html';
      }, 400);
    });
  }

  // Mobile Sidebar Drawer
  const mobileToggleBtn = document.getElementById('mobileToggleBtn');
  const sidebar = document.getElementById('appSidebar');

  if (mobileToggleBtn && sidebar) {
    mobileToggleBtn.addEventListener('click', () => {
      sidebar.classList.toggle('active');
    });

    document.addEventListener('click', (e) => {
      if (window.innerWidth <= 768 && sidebar.classList.contains('active')) {
        if (!sidebar.contains(e.target) && !mobileToggleBtn.contains(e.target)) {
          sidebar.classList.remove('active');
        }
      }
    });
  }

  // Password Visibility Toggles
  const passwordToggles = document.querySelectorAll('.password-toggle-btn');
  passwordToggles.forEach(toggle => {
    toggle.addEventListener('click', () => {
      const targetId = toggle.getAttribute('data-target');
      const passwordInput = document.getElementById(targetId);
      if (passwordInput) {
        const isPassword = passwordInput.type === 'password';
        passwordInput.type = isPassword ? 'text' : 'password';
        toggle.setAttribute('aria-label', isPassword ? 'Hide password' : 'Show password');
      }
    });
  });

  // Modal Backdrop Click & ESC Key Handling
  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      const activeModal = document.querySelector('.modal-backdrop.active');
      if (activeModal) {
        closeModal(activeModal.id);
      }
    }
  });

  const backdrops = document.querySelectorAll('.modal-backdrop');
  backdrops.forEach(backdrop => {
    backdrop.addEventListener('click', (e) => {
      if (e.target === backdrop) {
        closeModal(backdrop.id);
      }
    });
  });

  // Active Link Highlighting
  const currentPath = window.location.pathname;
  const navLinks = document.querySelectorAll('.nav-link');
  navLinks.forEach(link => {
    const href = link.getAttribute('href');
    if (href && currentPath.endsWith(href)) {
      link.classList.add('active');
    }
  });

  // Step 19 Notification Bell & In-App UI Setup
  if (sessionStorage.getItem('accessToken')) {
    initializeNotificationUI();

    // Refresh the cached permission list, then hide anything the user cannot
    // use. Runs in the background so it never delays the page.
    ensurePermissionsLoaded()
      .then(() => applyPermissionVisibility())
      .catch(() => { /* offline or backend down — leave the cached list in place */ });
  }
});

// ─────────────────────────────────────────────────────────────────────────────
// Step 19 Notification Bell UI Controller
// ─────────────────────────────────────────────────────────────────────────────

async function initializeNotificationUI() {
  const headerRight = document.querySelector('.header-right');
  if (headerRight && !document.getElementById('notificationBellBtn')) {
    const bellContainer = document.createElement('div');
    bellContainer.style.cssText = 'position: relative; display: inline-flex; align-items: center; margin-right: 0.75rem;';
    bellContainer.innerHTML = `
      <button id="notificationBellBtn" class="btn btn-outline btn-sm" aria-label="Notifications" style="position: relative; display: flex; align-items: center; gap: 0.35rem; padding: 0.4rem 0.75rem; border-radius: 20px;">
        <svg style="width: 18px; height: 18px;" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9"/>
        </svg>
        <span>Notifications</span>
        <span id="unreadNotifBadge" class="badge badge-pending" style="display: none; background: #DC2626; color: #FFF; font-size: 0.75rem; padding: 0.15rem 0.45rem; border-radius: 10px;">0</span>
      </button>
    `;
    headerRight.insertBefore(bellContainer, headerRight.firstChild);
  }

  // Inject Notification Modal into DOM if missing
  if (!document.getElementById('notificationModal')) {
    const modalHtml = `
      <div class="modal-backdrop" id="notificationModal" aria-hidden="true" role="dialog" aria-labelledby="notifModalTitle">
        <div class="modal-container" style="max-width: 540px;">
          <div class="modal-header">
            <h3 class="modal-title" id="notifModalTitle">In-App Notifications</h3>
            <div style="display: flex; gap: 0.5rem; align-items: center;">
              <button class="btn btn-outline btn-sm" id="markAllReadBtn" style="font-size: 0.8rem;">Mark All Read</button>
              <button class="modal-close-btn" onclick="closeModal('notificationModal')" aria-label="Close modal">&times;</button>
            </div>
          </div>
          <div class="modal-body" style="padding: 1rem 1.25rem; max-height: 420px; overflow-y: auto;">
            <div id="notificationListContainer">
              <div class="spinner"></div>
            </div>
          </div>
          <div class="modal-footer">
            <button class="btn btn-outline" onclick="closeModal('notificationModal')">Close</button>
          </div>
        </div>
      </div>
    `;
    document.body.insertAdjacentHTML('beforeend', modalHtml);
  }

  // Attach Bell Click Listener
  document.getElementById('notificationBellBtn')?.addEventListener('click', () => {
    openModal('notificationModal');
    loadNotificationPanelList();
  });

  // Attach Mark All Read Listener
  document.getElementById('markAllReadBtn')?.addEventListener('click', async () => {
    const res = await ApiClient.markAllNotificationsRead();
    if (res.success) {
      showToast('All notifications marked as read.', 'info');
      await refreshUnreadBadge();
      await loadNotificationPanelList();
    } else {
      showToast(res.message || 'Failed to mark notifications read', 'error');
    }
  });

  // Initial Badge Refresh
  await refreshUnreadBadge();
}

async function refreshUnreadBadge() {
  const badge = document.getElementById('unreadNotifBadge');
  if (!badge) return;

  const res = await ApiClient.getUnreadNotificationCount();
  if (res.success && res.data && typeof res.data.unreadCount !== 'undefined') {
    const count = res.data.unreadCount;
    if (count > 0) {
      badge.textContent = count > 99 ? '99+' : count;
      badge.style.display = 'inline-block';
    } else {
      badge.style.display = 'none';
    }
  }
}

async function loadNotificationPanelList() {
  const container = document.getElementById('notificationListContainer');
  if (!container) return;

  container.innerHTML = '<div style="text-align:center; padding: 1.5rem;"><div class="spinner"></div><p style="margin-top:0.5rem; font-size:0.85rem;">Loading notifications...</p></div>';

  const res = await ApiClient.getNotifications(0, 15);
  if (!res.success || !res.data) {
    container.innerHTML = `<div class="empty-state" style="padding: 1rem;"><div class="empty-state-title">Error</div><p class="empty-state-text">${escapeHtml(res.message || 'Failed to load notifications')}</p></div>`;
    return;
  }

  const items = Array.isArray(res.data.content) ? res.data.content : [];
  if (items.length === 0) {
    container.innerHTML = `
      <div class="empty-state" style="padding: 2rem 1rem;">
        <div class="empty-state-title" style="font-size: 1.05rem; color: var(--text-muted);">No new notifications.</div>
        <p class="empty-state-text" style="font-size: 0.85rem; margin-top: 0.25rem;">You're all caught up! Activity updates will appear here.</p>
      </div>`;
    return;
  }

  container.innerHTML = '';
  items.forEach(item => {
    const isUnread = !item.isRead;
    const card = document.createElement('div');
    card.style.cssText = `
      padding: 0.85rem 1rem;
      border-radius: 8px;
      margin-bottom: 0.6rem;
      background: ${isUnread ? 'rgba(123, 31, 50, 0.04)' : 'var(--surface-card)'};
      border: 1px solid ${isUnread ? '#7B1F32' : 'var(--border-color)'};
      border-left: 4px solid ${isUnread ? '#7B1F32' : '#94A3B8'};
    `;

    const typeBadge = item.notificationType === 'ACHIEVEMENT_APPROVED' ? '<span class="badge badge-approved" style="font-size:0.75rem;">Approved</span>' :
                      item.notificationType === 'ACHIEVEMENT_REJECTED' ? '<span class="badge badge-rejected" style="font-size:0.75rem;">Rejected</span>' :
                      item.notificationType === 'VERIFICATION_REQUIRED' ? '<span class="badge badge-pending" style="font-size:0.75rem;">Action Needed</span>' :
                      '<span class="badge" style="font-size:0.75rem; background:#E2E8F0; color:#1E293B;">Submitted</span>';

    card.innerHTML = `
      <div style="display: flex; justify-content: space-between; align-items: flex-start; gap: 0.5rem; margin-bottom: 0.35rem;">
        <div style="font-weight: 600; font-size: 0.9rem; color: #7B1F32;">${escapeHtml(item.title)}</div>
        <div>${typeBadge}</div>
      </div>
      <div style="font-size: 0.85rem; color: var(--text-primary); margin-bottom: 0.4rem; line-height: 1.4;">${escapeHtml(item.message)}</div>
      <div style="display: flex; justify-content: space-between; align-items: center; font-size: 0.78rem; color: var(--text-muted);">
        <span>${formatTimeAgo(item.createdAt)}</span>
        ${isUnread ? `<button class="btn btn-outline btn-sm mark-single-read-btn" data-id="${item.id}" style="padding: 0.15rem 0.45rem; font-size: 0.75rem;">Mark Read</button>` : '<span style="color:#10B981;">✓ Read</span>'}
      </div>
    `;
    container.appendChild(card);
  });

  container.querySelectorAll('.mark-single-read-btn').forEach(btn => {
    btn.addEventListener('click', async () => {
      const id = btn.getAttribute('data-id');
      const r = await ApiClient.markNotificationRead(id);
      if (r.success) {
        await refreshUnreadBadge();
        await loadNotificationPanelList();
      }
    });
  });
}

function formatTimeAgo(dateStr) {
  if (!dateStr) return '';
  const d = new Date(dateStr);
  const diffMs = new Date() - d;
  const diffMin = Math.floor(diffMs / 60000);
  if (diffMin < 1) return 'Just now';
  if (diffMin < 60) return `${diffMin}m ago`;
  const diffHr = Math.floor(diffMin / 60);
  if (diffHr < 24) return `${diffHr}h ago`;
  return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short' });
}
