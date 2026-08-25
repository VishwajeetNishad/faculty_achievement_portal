/* ====================================================================
   public-faculty.js — the public Faculty Directory
   --------------------------------------------------------------------
   Drives public/faculty.html: search by name, filter by department and
   designation, sort, and page through the results.

   How the data gets here:
   The page reads GET /api/public/faculty — ACTIVE users only, each with
   the count of their public achievements. If that call fails the page
   shows an error state; it never invents a directory.

   Why filtering happens in the browser:
   The whole directory is fetched once, then filtered, sorted and paged
   locally. For a single institution — six departments, on the order of a
   few hundred staff — that is one request instead of one per keystroke,
   and every filter feels instant. The planned endpoint also accepts
   keyword= and departmentCode=, so if NIET's directory ever grows large
   enough for that to matter, moving the work server-side is a change to
   loadFaculty() alone and nothing else on this page.
   ==================================================================== */

(function () {

  const PAGE_SIZE = 9;   /* 3 × 3 on a desktop grid */

  let allFaculty = [];
  let filtered = [];
  let currentPage = 0;

  /* Elements, looked up once. */
  let elForm, elKeyword, elDepartment, elDesignation, elSort,
      elGrid, elCount, elPagination;

  /* ================================================================
     Data loading
     ================================================================ */

  async function loadFaculty() {
    /* getOrFail — the directory is the whole point of the page, so a
       failure here is fatal and load()'s catch block draws the error. */
    const live = await PublicApi.getOrFail('/public/faculty', { page: 0, size: 500 });
    return Array.isArray(live) ? live : (live.content || []);
  }

  async function loadDepartments() {
    /* tryGet — the department dropdown is a convenience, not the point of
       the page. If it cannot load, fillDepartmentOptions falls back to
       the departments actually present in the directory, so the filter
       still works. */
    const live = await PublicApi.tryGet('/public/departments');
    if (live) {
      const rows = Array.isArray(live) ? live : (live.content || []);
      return rows.map(function (d) {
        return { code: d.code || d.departmentCode, name: d.name || d.departmentName };
      });
    }
    return [];
  }

  /* ================================================================
     Filter option lists

     Departments come from the department table. Designations do not —
     designation is a free-text column on users, so the only honest way
     to build that dropdown is to read the values actually present in
     the directory. A designation nobody holds never appears as an
     option, so the filter can never return an empty list.
     ================================================================ */

  function fillDepartmentOptions(departments) {
    /* Show a department only if somebody in the directory is in it. */
    const present = {};
    allFaculty.forEach(function (p) {
      if (p.departmentCode) present[p.departmentCode] = true;
    });

    const usable = departments.filter(function (d) { return present[d.code]; });
    const list = usable.length ? usable : departments;

    elDepartment.innerHTML =
      '<option value="">All departments</option>' +
      list.map(function (d) {
        return '<option value="' + PublicUI.escapeHtml(d.code) + '">' +
               PublicUI.escapeHtml(d.name) + ' (' + PublicUI.escapeHtml(d.code) + ')' +
               '</option>';
      }).join('');
  }

  function fillDesignationOptions() {
    const seen = {};
    allFaculty.forEach(function (p) {
      if (p.designation) seen[p.designation] = true;
    });

    const list = Object.keys(seen).sort();

    elDesignation.innerHTML =
      '<option value="">All designations</option>' +
      list.map(function (value) {
        return '<option value="' + PublicUI.escapeHtml(value) + '">' +
               PublicUI.escapeHtml(value) + '</option>';
      }).join('');
  }

  /* ================================================================
     Filtering and sorting
     ================================================================ */

  function applyFilters() {
    const keyword = (elKeyword.value || '').trim().toLowerCase();
    const department = elDepartment.value;
    const designation = elDesignation.value;

    filtered = allFaculty.filter(function (person) {
      if (department && person.departmentCode !== department) return false;
      if (designation && person.designation !== designation) return false;

      if (keyword) {
        /* Name and designation are the two things a visitor would type.
           Email and employee id are deliberately not searchable here —
           they are never exposed on the public side at all. */
        const haystack = [
          person.fullName,
          person.designation,
          person.departmentName,
          person.departmentCode
        ].join(' ').toLowerCase();
        if (haystack.indexOf(keyword) === -1) return false;
      }

      return true;
    });

    sortFiltered();
    currentPage = 0;
    render();
  }

  function sortFiltered() {
    const mode = elSort.value;

    filtered.sort(function (a, b) {
      if (mode === 'name') {
        return String(a.fullName || '').localeCompare(String(b.fullName || ''));
      }
      if (mode === 'department') {
        const byDept = String(a.departmentCode || '').localeCompare(String(b.departmentCode || ''));
        if (byDept !== 0) return byDept;
        return String(a.fullName || '').localeCompare(String(b.fullName || ''));
      }
      /* Default: most published first, then alphabetically so the order
         is stable rather than depending on the source order. */
      const byCount = (b.publicAchievementCount || 0) - (a.publicAchievementCount || 0);
      if (byCount !== 0) return byCount;
      return String(a.fullName || '').localeCompare(String(b.fullName || ''));
    });
  }

  /* ================================================================
     Rendering
     ================================================================ */

  function render() {
    renderCount();
    renderGrid();
    renderPagination();
  }

  function renderCount() {
    const total = filtered.length;
    const hasFilters = (elKeyword.value || '').trim() || elDepartment.value || elDesignation.value;

    if (total === 0) {
      elCount.textContent = 'No faculty match these filters';
      return;
    }

    const from = currentPage * PAGE_SIZE + 1;
    const to = Math.min(total, (currentPage + 1) * PAGE_SIZE);
    const noun = total === 1 ? 'faculty member' : 'faculty members';

    elCount.innerHTML =
      'Showing <strong>' + from + '–' + to + '</strong> of <strong>' + total + '</strong> ' +
      noun + (hasFilters ? ' matching your filters' : '');
  }

  function renderGrid() {
    if (filtered.length === 0) {
      PublicUI.showEmpty(
        elGrid,
        'No faculty match these filters',
        'Try a shorter search term, or clear the department and designation filters to see the whole directory.',
        '<button class="pub-btn pub-btn-outline" type="button" id="emptyClearBtn">Clear all filters</button>'
      );
      const clearBtn = document.getElementById('emptyClearBtn');
      if (clearBtn) clearBtn.addEventListener('click', resetFilters);
      return;
    }

    const start = currentPage * PAGE_SIZE;
    const rows = filtered.slice(start, start + PAGE_SIZE);

    elGrid.removeAttribute('aria-busy');
    elGrid.innerHTML = rows.map(PublicUI.facultyCard).join('');
  }

  function renderPagination() {
    const pageCount = Math.ceil(filtered.length / PAGE_SIZE);

    /* One page needs no controls. */
    if (pageCount <= 1) {
      elPagination.innerHTML = '';
      return;
    }

    let html =
      '<button class="pub-page-btn" type="button" data-page="' + (currentPage - 1) + '"' +
      (currentPage === 0 ? ' disabled' : '') + '>Previous</button>';

    for (let i = 0; i < pageCount; i++) {
      html += '<button class="pub-page-btn' + (i === currentPage ? ' is-active' : '') + '"' +
              ' type="button" data-page="' + i + '"' +
              (i === currentPage ? ' aria-current="page"' : '') + '>' + (i + 1) + '</button>';
    }

    html +=
      '<button class="pub-page-btn" type="button" data-page="' + (currentPage + 1) + '"' +
      (currentPage >= pageCount - 1 ? ' disabled' : '') + '>Next</button>';

    elPagination.innerHTML = html;

    elPagination.querySelectorAll('.pub-page-btn').forEach(function (button) {
      button.addEventListener('click', function () {
        const page = parseInt(button.dataset.page, 10);
        if (isNaN(page) || page < 0 || page >= pageCount) return;
        currentPage = page;
        render();
        /* Jump back to the top of the results, not the top of the page —
           the visitor has already read the filters. */
        elGrid.scrollIntoView({ behavior: 'smooth', block: 'start' });
      });
    });
  }

  /* ================================================================
     Filter wiring
     ================================================================ */

  function resetFilters() {
    elKeyword.value = '';
    elDepartment.value = '';
    elDesignation.value = '';
    applyFilters();
  }

  function initFilters() {
    /* A dropdown applies the moment it changes — that is what a filter
       control is expected to do. */
    elDepartment.addEventListener('change', applyFilters);
    elDesignation.addEventListener('change', applyFilters);
    elSort.addEventListener('change', function () {
      sortFiltered();
      currentPage = 0;
      render();
    });

    /* Typing filters as you go, but only after a short pause, so a long
       name does not re-render on every character. */
    let debounce = null;
    elKeyword.addEventListener('input', function () {
      clearTimeout(debounce);
      debounce = setTimeout(applyFilters, 220);
    });

    elForm.addEventListener('submit', function (event) {
      /* There is nothing to submit — the filters are already applied. */
      event.preventDefault();
      clearTimeout(debounce);
      applyFilters();
    });

    elForm.addEventListener('reset', function (event) {
      event.preventDefault();
      resetFilters();
    });
  }

  /** Carry the home page's search box over: ?q= prefills the keyword,
      ?dept= prefills the department. */
  function applyUrlFilters() {
    const keyword = PublicUI.queryParam('q');
    if (keyword) elKeyword.value = keyword;

    const department = PublicUI.queryParam('dept');
    if (department) {
      /* Only honour it if the option actually exists, otherwise the
         select silently shows "All departments" while the URL claims
         something else. */
      const match = Array.prototype.some.call(elDepartment.options, function (option) {
        return option.value === department;
      });
      if (match) elDepartment.value = department;
    }
  }

  /* ================================================================
     Boot
     ================================================================ */

  async function load() {
    PublicUI.showSkeletons(elGrid, 6);
    elCount.textContent = 'Loading the directory…';

    try {
      const results = await Promise.all([loadFaculty(), loadDepartments()]);
      allFaculty = results[0] || [];

      fillDepartmentOptions(results[1] || []);
      fillDesignationOptions();
      applyUrlFilters();
      applyFilters();

    } catch (error) {
      console.error('[public-faculty] failed to load the directory', error);
      elCount.textContent = '';
      PublicUI.showError(
        elGrid,
        'Could not load the faculty directory',
        'Something went wrong while fetching the directory. Please refresh the page, or try again in a few minutes.',
        '<button class="pub-btn pub-btn-outline" type="button" onclick="window.location.reload()">Reload page</button>'
      );
    }
  }

  document.addEventListener('DOMContentLoaded', function () {
    elForm        = document.getElementById('facultyFilterForm');
    elKeyword     = document.getElementById('facultyKeyword');
    elDepartment  = document.getElementById('facultyDepartment');
    elDesignation = document.getElementById('facultyDesignation');
    elSort        = document.getElementById('facultySort');
    elGrid        = document.getElementById('facultyGrid');
    elCount       = document.getElementById('facultyResultCount');
    elPagination  = document.getElementById('facultyPagination');

    if (!elGrid) return;

    initFilters();
    load();
  });

})();
