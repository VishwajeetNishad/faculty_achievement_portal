/**
 * Login Page Controller — Connected to Spring Boot Security POST /api/auth/login
 */

/* ─── Left showcase metrics ───────────────────────────────────────────────────
   The four cards in the branding panel used to carry hardcoded digits, which
   made the sign-in page assert figures the portal had never measured. They are
   now live counts from the public achievements API.

   Three constraints shape how this is done:

     · ApiClient is deliberately NOT used. It redirects to this very page on a
       401, so a stray auth failure here could bounce the user in a loop. These
       are public, unauthenticated reads, so a plain fetch is both correct and
       safer.
     · Nothing here may delay or break signing in. The call is fired without
       being awaited and swallows its own failures.
     · A number is printed only when the API actually returned one. If any
       request fails or a payload is malformed the grid simply stays hidden —
       showing 0 would be inventing a statistic, not reporting one, which is the
       exact problem the hardcoded digits had. */

const SHOWCASE_METRICS = [
  { elementId: 'showcaseCountPublication', categoryCode: 'PUBLICATION' },
  { elementId: 'showcaseCountPatent',      categoryCode: 'PATENT' },
  { elementId: 'showcaseCountGrant',       categoryCode: 'RESEARCH_GRANT' },
  { elementId: 'showcaseCountAward',       categoryCode: 'AWARD' }
];

const SHOWCASE_METRICS_TIMEOUT_MS = 6000;

/**
 * Count the public achievements in one category.
 *
 * Asks for a single row and reads the pager's own totalElements, so the figure
 * stays exact however large the portal grows. Counting rows out of a capped
 * page client-side would silently under-report once the portal passed that cap.
 */
const countPublicAchievements = async (categoryCode) => {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), SHOWCASE_METRICS_TIMEOUT_MS);

  try {
    const url = `${CONFIG.API_BASE_URL}/public/achievements`
      + `?page=0&size=1&categoryCode=${encodeURIComponent(categoryCode)}`;

    const response = await fetch(url, {
      headers: { Accept: 'application/json' },
      signal: controller.signal
    });

    if (!response.ok) {
      throw new Error(`HTTP ${response.status} counting ${categoryCode}`);
    }

    const body = await response.json();

    // Guard the field instead of coercing it: `Number(undefined) || 0` would
    // quietly turn a malformed response into a displayed "0".
    if (typeof body.totalElements !== 'number') {
      throw new Error(`totalElements missing from the ${categoryCode} response`);
    }

    return body.totalElements;
  } finally {
    clearTimeout(timer);
  }
};

const loadShowcaseMetrics = async () => {
  const grid = document.getElementById('showcaseMetrics');
  if (!grid) return;

  // The showcase panel is desktop-only — CSS hides it below 960px. Reading the
  // panel's computed display, rather than repeating that breakpoint here, keeps
  // this in step with the stylesheet if the width ever changes, and saves four
  // requests nobody would see the result of.
  const panel = grid.closest('.login-left-showcase');
  if (panel && getComputedStyle(panel).display === 'none') return;

  try {
    /* Promise.all rejects on the first failure, which is what we want: a grid
       with one blank card looks broken, and filling that card with a 0 would be
       a lie. It is all four real numbers or nothing. */
    const counts = await Promise.all(
      SHOWCASE_METRICS.map((metric) => countPublicAchievements(metric.categoryCode))
    );

    SHOWCASE_METRICS.forEach((metric, index) => {
      const el = document.getElementById(metric.elementId);
      if (el) el.textContent = counts[index].toLocaleString('en-IN');
    });

    grid.hidden = false;
  } catch (err) {
    // Non-fatal and intentionally invisible: the panel is decorative, and a
    // failed count must never distract from the task of signing in.
    console.warn('[login] left showcase metrics unavailable:', err.message);
  }
};

document.addEventListener('DOMContentLoaded', () => {
  const loginForm = document.getElementById('loginForm');
  const togglePasswordBtn = document.getElementById('togglePasswordBtn');
  const passwordInput = document.getElementById('password');

  // Fire-and-forget on purpose — the showcase counts must never hold up sign-in.
  loadShowcaseMetrics();

  // Check URL query parameters for session expiration notice
  const urlParams = new URLSearchParams(window.location.search);
  if (urlParams.get('session') === 'expired') {
    showToast('Your session has expired. Please sign in again.', 'warning');
  }

  // Password Visibility Toggle
  if (togglePasswordBtn && passwordInput) {
    togglePasswordBtn.addEventListener('click', () => {
      const isPassword = passwordInput.type === 'password';
      passwordInput.type = isPassword ? 'text' : 'password';
      togglePasswordBtn.textContent = isPassword ? 'Hide' : 'Show';
    });
  }

  // Login Form Submission Handler
  if (loginForm) {
    loginForm.addEventListener('submit', async (e) => {
      e.preventDefault();

      const usernameInput = document.getElementById('loginEmail') || document.getElementById('username');
      const passwordInput = document.getElementById('loginPassword') || document.getElementById('password');

      const usernameVal = usernameInput ? usernameInput.value.trim() : '';
      const passwordVal = passwordInput ? passwordInput.value : '';

      if (!FormValidator.validateRequired(usernameVal)) {
        showToast('Please enter your Email or Employee ID', 'error');
        return;
      }

      if (!FormValidator.validateRequired(passwordVal)) {
        showToast('Please enter your password', 'error');
        return;
      }

      const submitBtn = loginForm.querySelector('button[type="submit"]');
      if (submitBtn) {
        submitBtn.disabled = true;
        submitBtn.textContent = 'Authenticating...';
      }

      try {
        // Execute Real Authentication API Request
        const res = await ApiClient.post('/auth/login', {
          email: usernameVal,
          password: passwordVal
        });

        if (res && res.success && res.data && res.data.accessToken) {
          // Store JWT token and User Info in sessionStorage
          sessionStorage.setItem('accessToken', res.data.accessToken);
          sessionStorage.setItem('currentUser', JSON.stringify(res.data));

          // Drop any permission list cached for a previously signed-in user, so
          // the new user never sees buttons belonging to someone else. The real
          // list is fetched from /api/auth/me on the next page load.
          sessionStorage.removeItem('currentPermissions');

          showToast(`Welcome back, ${res.data.fullName || 'User'}!`, 'success');

          setTimeout(() => {
            if (res.data.role === 'ROLE_ADMIN' || res.data.role === 'ADMIN') {
              window.location.href = 'admin/dashboard.html';
            } else if (res.data.role === 'ROLE_HOD' || res.data.role === 'HOD') {
              window.location.href = 'hod/dashboard.html';
            } else {
              window.location.href = 'dashboard.html';
            }
          }, 800);
        } else {
          showToast((res && res.message) || 'Invalid email/employee ID or password', 'error');
          if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.textContent = 'Sign In to Portal';
          }
        }
      } catch (err) {
        console.error('Login error:', err);
        showToast('Login request failed. Ensure backend server is running.', 'error');
        if (submitBtn) {
          submitBtn.disabled = false;
          submitBtn.textContent = 'Sign In to Portal';
        }
      }
    });
  }
});
