/**
 * Login Page Controller — Connected to Spring Boot Security POST /api/auth/login
 */

document.addEventListener('DOMContentLoaded', () => {
  const loginForm = document.getElementById('loginForm');
  const togglePasswordBtn = document.getElementById('togglePasswordBtn');
  const passwordInput = document.getElementById('password');

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

      const usernameVal = document.getElementById('username').value.trim();
      const passwordVal = passwordInput.value;

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

      // Execute Real Authentication API Request
      const res = await ApiClient.post('/auth/login', {
        email: usernameVal,
        password: passwordVal
      });

      if (res.success && res.data && res.data.accessToken) {
        // Store JWT token and User Info in sessionStorage
        sessionStorage.setItem('accessToken', res.data.accessToken);
        sessionStorage.setItem('currentUser', JSON.stringify(res.data));

        showToast(`Welcome back, ${res.data.fullName || 'User'}!`, 'success');

        setTimeout(() => {
          if (res.data.role === 'ROLE_ADMIN' || res.data.role === 'ADMIN') {
            window.location.href = 'admin/dashboard.html';
          } else {
            window.location.href = 'dashboard.html';
          }
        }, 800);
      } else {
        showToast(res.message || 'Invalid email/employee ID or password', 'error');
        if (submitBtn) {
          submitBtn.disabled = false;
          submitBtn.textContent = 'Sign In';
        }
      }
    });
  }
});
