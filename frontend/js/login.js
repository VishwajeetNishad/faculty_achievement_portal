/**
 * Login Page Controller — Pure Frontend Validation & Demo Authentication Alert
 */

document.addEventListener('DOMContentLoaded', () => {
  const loginForm = document.getElementById('loginForm');

  if (loginForm) {
    loginForm.addEventListener('submit', (e) => {
      e.preventDefault();

      const emailInput = document.getElementById('loginEmail');
      const passwordInput = document.getElementById('loginPassword');

      const email = emailInput?.value.trim();
      const password = passwordInput?.value;

      if (!FormValidator.validateRequired(email) || !FormValidator.validateRequired(password)) {
        showToast('Please enter your Employee ID / Email and password.', 'error');
        return;
      }

      showToast('Authentication is not connected yet (Step 11 Demo Mode). Redirecting to Dashboard UI...', 'info', 3000);

      setTimeout(() => {
        if (email.toLowerCase().includes('admin')) {
          window.location.href = 'admin/dashboard.html';
        } else {
          window.location.href = 'dashboard.html';
        }
      }, 1200);
    });
  }
});
