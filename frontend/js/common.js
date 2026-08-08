/**
 * Common UI Scripts & Sidebar Toggle Helpers
 */

document.addEventListener('DOMContentLoaded', () => {
  // Mobile Sidebar Toggle
  const mobileToggleBtn = document.getElementById('mobileToggleBtn');
  const sidebar = document.getElementById('appSidebar');

  if (mobileToggleBtn && sidebar) {
    mobileToggleBtn.addEventListener('click', () => {
      sidebar.classList.toggle('active');
    });
  }

  // Highlight Active Nav Link
  const currentPath = window.location.pathname;
  const navLinks = document.querySelectorAll('.nav-link');

  navLinks.forEach(link => {
    const href = link.getAttribute('href');
    if (href && currentPath.includes(href)) {
      link.classList.add('active');
    }
  });
});
