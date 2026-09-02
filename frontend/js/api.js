/**
 * Faculty Achievement Portal — Centralized REST API Client
 * Wraps Vanilla JavaScript fetch() calls for Spring Boot backend communication.
 * Automatically injects Bearer JWT authentication header from sessionStorage.
 */

const ApiClient = (() => {

  /**
   * Idle-timeout settings.
   *
   * <p><b>This is a convenience, not a security control.</b> Everything below runs
   * in the browser and can be stopped by anyone who opens the devtools console.
   * The real guarantee is the backend, which verifies the JWT's signature and its
   * `exp` claim on every single request. What this buys is the ordinary case: a
   * faculty member walks away from a shared lab machine, comes back after lunch,
   * and finds the login screen instead of their dashboard.
   *
   * <p>The JWT itself still lives its full configured lifetime
   * (`app.jwt.expiration-ms`, 24h) — this does not shorten it and does not try to.
   */
  const IDLE_LIMIT_MINUTES = 30;
  const IDLE_LIMIT_MS = IDLE_LIMIT_MINUTES * 60 * 1000;
  const ACTIVITY_KEY = 'lastActivityAt';

  /** How often the timer re-checks. Short enough to be prompt, cheap enough to ignore. */
  const IDLE_POLL_MS = 30 * 1000;

  /** Don't touch sessionStorage on every mousemove; once every 15s is plenty. */
  const ACTIVITY_WRITE_THROTTLE_MS = 15 * 1000;

  const getHeaders = (isMultipart = false) => {
    const headers = {};
    if (!isMultipart) {
      headers['Content-Type'] = 'application/json';
      headers['Accept'] = 'application/json';
    }

    const token = sessionStorage.getItem('accessToken');
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    return headers;
  };

  /**
   * Reads the `exp` claim out of a JWT without verifying it.
   *
   * <p>This is a routing decision and never a security one. The signature is not
   * checked here and could not be — the browser does not hold the signing key.
   * The backend re-validates the token on every single request regardless of what
   * this returns, so the worst a tampered `exp` can do is send its own owner to
   * the login screen early or late.
   *
   * @returns expiry in milliseconds, or null if the token cannot be read.
   */
  const tokenExpiryMs = (token) => {
    try {
      const payload = token.split('.')[1];
      if (!payload) return null;

      // A JWT is base64url encoded, but atob() only accepts standard base64: swap
      // the two characters that differ and restore the padding base64url strips.
      const b64 = payload.replace(/-/g, '+').replace(/_/g, '/');
      const padded = b64 + '='.repeat((4 - (b64.length % 4)) % 4);

      const claims = JSON.parse(atob(padded));
      return typeof claims.exp === 'number' ? claims.exp * 1000 : null;
    } catch (e) {
      return null;
    }
  };

  /** True when the browser is holding no usable session. See the 403 branch. */
  const sessionLooksDead = () => {
    const token = sessionStorage.getItem('accessToken');
    if (!token) return true;

    const expiry = tokenExpiryMs(token);

    // A token that is present but unreadable counts as dead, deliberately. Of the
    // two ways to be wrong here, sending someone to the login screen costs them
    // one click, while leaving them on a page that claims "permission denied"
    // forever gives them no way out at all.
    if (expiry === null) return true;

    return Date.now() >= expiry;
  };

  /**
   * Drops the dead session and sends the user to the login screen.
   *
   * <p>Clears all three keys, matching the manual Sign Out in common.js. The
   * permission list especially must go: leaving it behind would let one user's
   * cached permissions decide which buttons the next user sees on this browser.
   * ('currentPermissions' is spelled out rather than imported because api.js is
   * the bottom layer and must not depend on common.js — login.js:167 writes the
   * same literal for the same reason.)
   *
   * @param reason 'expired' (the JWT's own exp has passed, or the server refused
   *               a dead token) or 'idle' (the browser sat untouched past the
   *               idle limit). It only picks which sentence login.js shows.
   */
  const endSession = (reason = 'expired') => {
    sessionStorage.removeItem('accessToken');
    sessionStorage.removeItem('currentUser');
    sessionStorage.removeItem('currentPermissions');
    sessionStorage.removeItem(ACTIVITY_KEY);
    window.CURRENT_PERMISSIONS = [];
    window.CURRENT_USER_PROFILE = null;

    // Never redirect away from the login page itself, or a failing request there
    // would reload it in a loop.
    const path = window.location.pathname;
    if (path.endsWith('login.html') || path.endsWith('index.html')) return;

    const notice = reason === 'idle' ? 'idle' : 'expired';
    const isSubdir = path.includes('/admin/') || path.includes('/hod/');
    window.location.href = isSubdir
      ? `../login.html?session=${notice}`
      : `login.html?session=${notice}`;
  };

  let lastActivityWrite = 0;

  /**
   * Stamps "the user did something just now".
   *
   * <p>Throttled, because the listeners below fire on scroll and keypress and
   * writing sessionStorage on every one of those would be wasteful. The cost of
   * throttling is that the recorded time can lag reality by up to 15 seconds,
   * which against a 30-minute limit is noise.
   */
  const markActivity = () => {
    if (!sessionStorage.getItem('accessToken')) return;

    const now = Date.now();
    if (now - lastActivityWrite < ACTIVITY_WRITE_THROTTLE_MS) return;

    lastActivityWrite = now;
    sessionStorage.setItem(ACTIVITY_KEY, String(now));
  };

  /** Milliseconds since the last recorded activity, or null if none is recorded. */
  const idleMs = () => {
    const raw = Number(sessionStorage.getItem(ACTIVITY_KEY));
    if (!Number.isFinite(raw) || raw <= 0) return null;

    // A stamp in the future means the clock moved backwards (timezone change,
    // NTP correction). Treat it as "just now" rather than as a huge negative.
    return Math.max(0, Date.now() - raw);
  };

  /**
   * Ends the session if the token has expired or the browser has sat idle too long.
   *
   * <p>Called at page load, on a timer, and whenever the tab becomes visible again.
   * Load time is the one that fixes a real hole: every page guard in this codebase
   * used to check only that a token was <i>present</i>, so a token that had already
   * expired still rendered the whole page and only bounced the user once the first
   * API call came back 403 — a broken half-loaded screen, then a redirect.
   */
  const enforceSession = () => {
    // No token means either the login page or an already-ended session. Either
    // way there is nothing to end, and endSession() would only risk a redirect loop.
    if (!sessionStorage.getItem('accessToken')) return;

    if (sessionLooksDead()) {
      endSession('expired');
      return;
    }

    const idle = idleMs();

    // No stamp yet — this is the first page after signing in. Seed it as "now".
    // Treating an unknown stamp as infinitely idle would log the user out on the
    // very first page they land on, which is the worse of the two failure directions.
    if (idle === null) {
      lastActivityWrite = Date.now();
      sessionStorage.setItem(ACTIVITY_KEY, String(lastActivityWrite));
      return;
    }

    if (idle >= IDLE_LIMIT_MS) endSession('idle');
  };

  /**
   * Wires the idle timeout up. Runs once, when this file is parsed.
   *
   * <p>Deliberately not gated on DOMContentLoaded: the load-time check should
   * happen before the page renders content the user is not entitled to see.
   */
  const armIdleTimeout = () => {
    enforceSession();

    // Passive listeners so scrolling is never blocked; capture so a handler that
    // stops propagation somewhere in the page cannot hide activity from us.
    ['pointerdown', 'keydown', 'scroll', 'wheel', 'touchstart'].forEach((evt) => {
      window.addEventListener(evt, markActivity, { passive: true, capture: true });
    });

    // The timer alone is not enough. Browsers throttle timers in background tabs
    // to roughly once a minute and may freeze them outright, so a tab left open
    // overnight cannot be relied on to have ticked. visibilitychange fires the
    // moment the user comes back to the tab, which is exactly the case that
    // prompted this: "logged in but not using — when they visit again, log in".
    document.addEventListener('visibilitychange', () => {
      if (!document.hidden) enforceSession();
    });

    // Same reason, for a window that was behind another one rather than hidden.
    window.addEventListener('focus', enforceSession);

    // Restoring from the back/forward cache re-uses the old JS state without
    // re-running this file, so the load-time check above would be skipped.
    window.addEventListener('pageshow', (e) => {
      if (e.persisted) enforceSession();
    });

    setInterval(enforceSession, IDLE_POLL_MS);
  };

  const handleResponse = async (response) => {
    // Handle 401 Unauthorized (Expired or invalid token).
    //
    // Spring Security in this project does not actually emit 401 — every auth
    // failure arrives as 403, handled below. This branch is kept so the client
    // still behaves correctly if that ever changes.
    if (response.status === 401) {
      endSession();
      return { success: false, status: 401, message: 'Session expired or unauthorized. Please sign in again.' };
    }

    // Handle 403 Forbidden, which this backend uses for two completely different
    // things: "you are not signed in" and "you are signed in but lack this
    // permission". Spring's authentication entry point and its access-denied
    // handler both answer with a bare 403 and an empty body, so the response
    // itself cannot say which one happened — verified by requesting the same
    // endpoint with no token and with a valid token lacking the permission, and
    // getting byte-identical replies.
    //
    // Telling them apart is the whole point. Before this branch existed, an
    // expired token made every request fail with "HTTP Error 403" and no
    // redirect, so faculty, admin and HOD pages all rendered blank at once with
    // nothing on screen explaining why or offering a way back to the login page.
    // The stored token is the tie-breaker: no token or an expired one means the
    // session is over, while a live token means the server really did refuse
    // this particular action and the page should stay put and say so.
    if (response.status === 403) {
      if (sessionLooksDead()) {
        endSession();
        return { success: false, status: 403, message: 'Session expired. Please sign in again.' };
      }

      // Left generic on purpose. Callers that know which permission they need
      // already supply their own wording; this only replaces the raw
      // "HTTP Error 403" that used to reach the screen.
      return {
        success: false,
        status: 403,
        message: 'You do not have permission for this action.'
      };
    }

    // 204 No Content
    if (response.status === 204) {
      return { success: true, data: null };
    }

    let data;
    try {
      data = await response.json();
    } catch (e) {
      data = null;
    }

    if (response.ok) {
      return { success: true, data: data };
    }

    const errorMessage = (data && data.message) 
      ? data.message 
      : (response.status === 404 ? 'Requested resource not found.' : `HTTP Error ${response.status}`);

    return { 
      success: false, 
      status: response.status, 
      message: errorMessage,
      data: data 
    };
  };

  const handleError = (error) => {
    console.error('API Client Network Failure:', error);

    // The old single message blamed the backend for being down. That reading is
    // wrong in the one case a beginner is most likely to hit: opening the page as
    // a file rather than through a web server. The backend is running, the URL is
    // right, and the browser refuses the request anyway because a file page has no
    // usable origin. Saying "check the backend is running" sends the reader to
    // inspect something that is already fine.
    if (typeof CONFIG !== 'undefined' && CONFIG.IS_FILE_PROTOCOL) {
      return {
        success: false,
        status: 0,
        message: 'This page was opened directly from a folder, so the browser blocks it from '
               + 'reaching the server. Open the portal through the local web server instead '
               + '(http://localhost:5500) rather than by double-clicking the HTML file.'
      };
    }

    return {
      success: false,
      status: 0,
      message: 'Unable to connect to the Faculty Achievement Portal server. Please check that the Spring Boot backend is running.'
    };
  };

  armIdleTimeout();

  return {
    /** Exposed so login.js can name the limit in its message without repeating the number. */
    idleLimitMinutes: IDLE_LIMIT_MINUTES,

    get: async (endpoint) => {
      try {
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), 10000);
        const response = await fetch(`${CONFIG.API_BASE_URL}${endpoint}`, {
          method: 'GET',
          headers: getHeaders(),
          signal: controller.signal
        });
        clearTimeout(timeoutId);
        return await handleResponse(response);
      } catch (error) {
        return handleError(error);
      }
    },

    post: async (endpoint, body) => {
      try {
        const controller = new AbortController();
        const timeoutId = setTimeout(() => controller.abort(), 10000);
        const response = await fetch(`${CONFIG.API_BASE_URL}${endpoint}`, {
          method: 'POST',
          headers: getHeaders(),
          body: JSON.stringify(body),
          signal: controller.signal
        });
        clearTimeout(timeoutId);
        return await handleResponse(response);
      } catch (error) {
        return handleError(error);
      }
    },

    put: async (endpoint, body) => {
      try {
        const response = await fetch(`${CONFIG.API_BASE_URL}${endpoint}`, {
          method: 'PUT',
          headers: getHeaders(),
          body: JSON.stringify(body)
        });
        return await handleResponse(response);
      } catch (error) {
        return handleError(error);
      }
    },

    patch: async (endpoint, body) => {
      try {
        const response = await fetch(`${CONFIG.API_BASE_URL}${endpoint}`, {
          method: 'PATCH',
          headers: getHeaders(),
          body: JSON.stringify(body)
        });
        return await handleResponse(response);
      } catch (error) {
        return handleError(error);
      }
    },

    delete: async (endpoint) => {
      try {
        const response = await fetch(`${CONFIG.API_BASE_URL}${endpoint}`, {
          method: 'DELETE',
          headers: getHeaders()
        });
        return await handleResponse(response);
      } catch (error) {
        return handleError(error);
      }
    },

    /**
     * Multipart/form-data upload method for PDF files.
     * Note: Do NOT set Content-Type header manually; browser generates boundary automatically.
     */
    upload: async (endpoint, formData) => {
      try {
        const response = await fetch(`${CONFIG.API_BASE_URL}${endpoint}`, {
          method: 'POST',
          headers: getHeaders(true),
          body: formData
        });
        return await handleResponse(response);
      } catch (error) {
        return handleError(error);
      }
    },

    /**
     * Protected file download method returning Object URL for authenticated PDF viewing.
     */
    downloadBlob: async (endpoint) => {
      try {
        const response = await fetch(`${CONFIG.API_BASE_URL}${endpoint}`, {
          method: 'GET',
          headers: getHeaders(true)
        });

        // Same two meanings of 403 as handleResponse, and the same tie-breaker.
        // This path used to redirect on 401 only, and with a relative
        // 'login.html' that resolves wrongly from /admin/ and /hod/ — so a CSV
        // download on an expired session produced a 404 instead of a login page.
        if (response.status === 401 || (response.status === 403 && sessionLooksDead())) {
          endSession();
          return { success: false, status: response.status, message: 'Session expired. Please sign in again.' };
        }

        if (response.status === 403) {
          return { success: false, status: 403, message: 'You do not have permission to download this.' };
        }

        if (!response.ok) {
          return { success: false, status: response.status, message: `Failed to download file (HTTP ${response.status})` };
        }

        const blob = await response.blob();
        const objectUrl = URL.createObjectURL(blob);
        return { success: true, objectUrl: objectUrl };
      } catch (error) {
        return handleError(error);
      }
    },

    // Step 19 Notification Helpers
    getNotifications: async (page = 0, size = 10) => {
      return await ApiClient.get(`/notifications?page=${page}&size=${size}`);
    },

    getUnreadNotificationCount: async () => {
      return await ApiClient.get('/notifications/unread-count');
    },

    markNotificationRead: async (id) => {
      return await ApiClient.patch(`/notifications/${id}/read`);
    },

    markAllNotificationsRead: async () => {
      return await ApiClient.patch('/notifications/read-all');
    }
  };

})();
