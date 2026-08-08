/**
 * Achievement Form Dynamic Fieldset Toggle (UI Step 9 Mock Architecture)
 */

document.addEventListener('DOMContentLoaded', () => {
  const categorySelect = document.getElementById('categorySelect');
  const extensionSections = document.querySelectorAll('.fieldset-section');

  if (categorySelect) {
    categorySelect.addEventListener('change', (e) => {
      const selectedCategory = e.target.value;

      // Hide all dynamic category sections first
      extensionSections.forEach(section => {
        section.style.display = 'none';
      });

      // Display selected category fieldset if exists
      if (selectedCategory) {
        const targetSection = document.getElementById(`section-${selectedCategory.toLowerCase()}`);
        if (targetSection) {
          targetSection.style.display = 'block';
        }
      }
    });
  }
});
