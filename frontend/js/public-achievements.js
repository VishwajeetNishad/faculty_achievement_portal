/* ====================================================================
   public-achievements.js — the public achievement gallery
   --------------------------------------------------------------------
   Drives public/achievements.html: category tabs, keyword search,
   department and academic-year filters, sorting and pagination.

   Two things arrive from other pages through the query string:
       ?q=federated            ← the home page hero search box
       ?category=PUBLICATION   ← the home page category pills
   Both are honoured on load, so a visitor lands on a pre-filtered list
   rather than on the whole gallery.

   Visibility: every record is passed through PublicUI.filterPublic()
   before it can reach the screen, so only APPROVED + PUBLIC entries are
   ever rendered. That is a display safeguard, not the security control —
   the backend must never send anything else in the first place.
   ==================================================================== */

(function () {

  const PAGE_SIZE = 9;

  let allAchievements = [];
  let facultyBySlug = {};
  let filtered = [];
  let activeCategory = 'ALL';
  let currentPage = 0;
  let usingSampleData = false;

  let elForm, elKeyword, elDepartment, elYear, elSort,
      elTabs, elGrid, elCount, elPagination, elNotice;

  /* ================================================================
     Data loading
     ================================================================ */

  async function loadAchievements() {
    /* TODO(track-b): when GET /api/public/achievements exists this
       returns real rows — already restricted to APPROVED + PUBLIC by the
       service layer — and the fallback below stops running. */
    const live = await PublicApi.tryGet('/public/achievements', { page: 0, size: 500 });
    if (live) {
      const rows = Array.isArray(live) ? live : (live.content || []);
      /* Belt and braces. The backend is the authority here. */
      return PublicUI.filterPublic(rows);
    }
    usingSampleData = true;
    return PublicSampleData.publicAchievements();
  }

  async function loadFaculty() {
    /* Needed for the author line on each card and for the department
       filter, because an achievement carries a faculty slug rather than
       an embedded department.
       TODO(track-b): a live PublicAchievementResponse is planned to embed
       the author's name and department, at which point this second call
       becomes an optimisation rather than a requirement. */
    const live = await PublicApi.tryGet('/public/faculty', { page: 0, size: 500 });
    if (live) {
      return Array.isArray(live) ? live : (live.content || []);
    }
    usingSampleData = true;
    return PublicSampleData.facultyWithCounts();
  }

  async function loadDepartments() {
    const live = await PublicApi.tryGet('/public/departments');
    if (live) {
      const rows = Array.isArray(live) ? live : (live.content || []);
      return rows.map(function (d) {
        return { code: d.code || d.departmentCode, name: d.name || d.departmentName };
      });
    }
    return PublicSampleData.departments;
  }

  /* ================================================================
     Author + department resolution
     ================================================================ */

  function authorFor(achievement) {
    const fromDirectory = facultyBySlug[achievement.facultySlug];
    if (fromDirectory) return fromDirectory;

    if (achievement.facultyName) {
      return {
        fullName: achievement.facultyName,
        designation: achievement.facultyDesignation,
        departmentName: achievement.departmentName,
        departmentCode: achievement.departmentCode,
        slug: achievement.facultySlug
      };
    }
    return null;
  }

  function departmentCodeOf(achievement) {
    if (achievement.departmentCode) return achievement.departmentCode;
    const author = facultyBySlug[achievement.facultySlug];
    return author ? author.departmentCode : null;
  }

  /* ================================================================
     Filter option lists — built from the records actually loaded, so no
     dropdown can offer a choice that returns nothing.
     ================================================================ */

  function fillDepartmentOptions(departments) {
    const present = {};
    allAchievements.forEach(function (a) {
      const code = departmentCodeOf(a);
      if (code) present[code] = true;
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

  function fillYearOptions() {
    const seen = {};
    allAchievements.forEach(function (a) {
      if (a.academicYear) seen[a.academicYear] = true;
    });

    /* Newest academic year first — '2025-26' sorts above '2024-25'. */
    const list = Object.keys(seen).sort().reverse();

    elYear.innerHTML =
      '<option value="">All years</option>' +
      list.map(function (value) {
        return '<option value="' + PublicUI.escapeHtml(value) + '">' +
               PublicUI.escapeHtml(value) + '</option>';
      }).join('');
  }

  /* ================================================================
     Category tabs
     ================================================================ */

  function renderTabs() {
    /* Counts respect the other active filters, so the tab numbers always
       describe what a click would actually show. */
    const base = allAchievements.filter(passesNonCategoryFilters);

    const counts = {};
    base.forEach(function (a) {
      counts[a.categoryCode] = (counts[a.categoryCode] || 0) + 1;
    });

    let html = '<button class="pub-tab' + (activeCategory === 'ALL' ? ' is-active' : '') + '"' +
               ' type="button" role="tab" data-category="ALL"' +
               ' aria-selected="' + (activeCategory === 'ALL') + '">' +
               'All (' + base.length + ')</button>';

    PublicUI.CATEGORY_ORDER.forEach(function (code) {
      const count = counts[code] || 0;
      const isActive = activeCategory === code;

      /* Keep an empty tab only while it is the one selected, so the
         visitor can see why the list is empty and click away from it. */
      if (count === 0 && !isActive) return;

      const meta = PublicUI.categoryMeta(code);
      html += '<button class="pub-tab' + (isActive ? ' is-active' : '') + '"' +
              ' type="button" role="tab" data-category="' + PublicUI.escapeHtml(code) + '"' +
              ' aria-selected="' + isActive + '">' +
              PublicUI.escapeHtml(meta.short) + ' (' + count + ')</button>';
    });

    elTabs.innerHTML = html;

    elTabs.querySelectorAll('.pub-tab').forEach(function (button) {
      button.addEventListener('click', function () {
        activeCategory = button.dataset.category;
        applyFilters();
      });
    });
  }

  /* ================================================================
     Filtering and sorting
     ================================================================ */

  /** Everything except the category tab. Split out so the tab counts can
      reuse it without counting themselves. */
  function passesNonCategoryFilters(achievement) {
    const keyword = (elKeyword.value || '').trim().toLowerCase();
    const department = elDepartment.value;
    const year = elYear.value;

    if (department && departmentCodeOf(achievement) !== department) return false;
    if (year && achievement.academicYear !== year) return false;

    if (keyword) {
      if (searchTextFor(achievement).indexOf(keyword) === -1) return false;
    }
    return true;
  }

  /** Everything a visitor might reasonably type, flattened into one
      lower-case string. Only publicly shown fields are included — a
      reviewer's comment or a faculty email is never searchable, because
      neither is ever sent to this page. */
  function searchTextFor(achievement) {
    if (achievement.__searchText) return achievement.__searchText;

    const author = authorFor(achievement);
    const parts = [
      achievement.title,
      achievement.description,
      achievement.keywords,
      achievement.academicYear,
      PublicUI.categoryMeta(achievement.categoryCode).label,
      author ? author.fullName : '',
      author ? author.departmentName : '',
      author ? author.departmentCode : ''
    ];

    const publication = achievement.publication;
    if (publication) {
      parts.push(publication.journalConferenceName, publication.publisher,
                 publication.doi, publication.isbnIssn, publication.publicationType,
                 publication.indexing);
    }
    const patent = achievement.patent;
    if (patent) {
      parts.push(patent.patentNumber, patent.patentStatus, patent.country);
    }
    const grant = achievement.researchGrant;
    if (grant) {
      parts.push(grant.fundingAgency, grant.projectTitle, grant.projectType, grant.grantStatus);
    }
    const workshop = achievement.workshopFdp;
    if (workshop) {
      parts.push(workshop.eventName, workshop.eventType, workshop.organizingBody, workshop.location);
    }
    const award = achievement.award;
    if (award) {
      parts.push(award.awardName, award.awardingBody, award.awardLevel);
    }

    /* Cached on the record because this runs for every row on every
       keystroke, and the values never change once loaded. */
    achievement.__searchText = parts.filter(Boolean).join(' ').toLowerCase();
    return achievement.__searchText;
  }

  function applyFilters() {
    filtered = allAchievements.filter(function (achievement) {
      if (activeCategory !== 'ALL' && achievement.categoryCode !== activeCategory) return false;
      return passesNonCategoryFilters(achievement);
    });

    sortFiltered();
    currentPage = 0;
    renderTabs();
    render();
  }

  function sortFiltered() {
    const mode = elSort.value;

    filtered.sort(function (a, b) {
      if (mode === 'title') {
        return String(a.title || '').localeCompare(String(b.title || ''));
      }
      const left = String(a.achievementDate || '');
      const right = String(b.achievementDate || '');
      /* ISO dates compare correctly as plain strings. */
      return mode === 'oldest'
        ? left.localeCompare(right)
        : right.localeCompare(left);
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

    if (total === 0) {
      elCount.textContent = 'No achievements match these filters';
      return;
    }

    const from = currentPage * PAGE_SIZE + 1;
    const to = Math.min(total, (currentPage + 1) * PAGE_SIZE);
    const noun = total === 1 ? 'achievement' : 'achievements';

    let line = 'Showing <strong>' + from + '–' + to + '</strong> of <strong>' + total + '</strong> public ' + noun;

    if (activeCategory !== 'ALL') {
      line += ' in ' + PublicUI.escapeHtml(PublicUI.categoryMeta(activeCategory).label);
    }

    elCount.innerHTML = line;
  }

  function renderGrid() {
    if (filtered.length === 0) {
      PublicUI.showEmpty(
        elGrid,
        'No achievements match these filters',
        'Nothing published so far fits this combination. Try a different category, a shorter search term, or clear the filters to see everything.',
        '<button class="pub-btn pub-btn-outline" type="button" id="emptyClearBtn">Clear all filters</button>'
      );
      const clearBtn = document.getElementById('emptyClearBtn');
      if (clearBtn) clearBtn.addEventListener('click', resetFilters);
      return;
    }

    const start = currentPage * PAGE_SIZE;
    const rows = filtered.slice(start, start + PAGE_SIZE);

    elGrid.removeAttribute('aria-busy');
    elGrid.innerHTML = rows.map(function (achievement) {
      return PublicUI.achievementCard(achievement, authorFor(achievement));
    }).join('');
  }

  function renderPagination() {
    const pageCount = Math.ceil(filtered.length / PAGE_SIZE);

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
    elYear.value = '';
    activeCategory = 'ALL';
    applyFilters();
  }

  function initFilters() {
    elDepartment.addEventListener('change', applyFilters);
    elYear.addEventListener('change', applyFilters);
    elSort.addEventListener('change', function () {
      sortFiltered();
      currentPage = 0;
      render();
    });

    let debounce = null;
    elKeyword.addEventListener('input', function () {
      clearTimeout(debounce);
      debounce = setTimeout(applyFilters, 220);
    });

    elForm.addEventListener('submit', function (event) {
      event.preventDefault();
      clearTimeout(debounce);
      applyFilters();
    });

    elForm.addEventListener('reset', function (event) {
      event.preventDefault();
      resetFilters();
    });
  }

  /** ?q= from the hero search, ?category= from the home page pills,
      ?dept= and ?year= for a shareable filtered link. */
  function applyUrlFilters() {
    const keyword = PublicUI.queryParam('q');
    if (keyword) elKeyword.value = keyword;

    const category = PublicUI.queryParam('category');
    if (category && PublicUI.CATEGORY_ORDER.indexOf(category) !== -1) {
      activeCategory = category;
    }

    const department = PublicUI.queryParam('dept');
    if (department) {
      const hasDept = Array.prototype.some.call(elDepartment.options, function (option) {
        return option.value === department;
      });
      if (hasDept) elDepartment.value = department;
    }

    const year = PublicUI.queryParam('year');
    if (year) {
      const hasYear = Array.prototype.some.call(elYear.options, function (option) {
        return option.value === year;
      });
      if (hasYear) elYear.value = year;
    }
  }

  /* ================================================================
     Boot
     ================================================================ */

  async function load() {
    PublicUI.showSkeletons(elGrid, 6);
    elCount.textContent = 'Loading achievements…';

    try {
      const results = await Promise.all([loadAchievements(), loadFaculty(), loadDepartments()]);
      allAchievements = results[0] || [];

      facultyBySlug = {};
      (results[1] || []).forEach(function (person) {
        if (person.slug) facultyBySlug[person.slug] = person;
      });

      fillDepartmentOptions(results[2] || []);
      fillYearOptions();
      applyUrlFilters();
      applyFilters();

      if (usingSampleData) {
        PublicUI.showSampleNotice(elNotice);
      }

    } catch (error) {
      console.error('[public-achievements] failed to load the gallery', error);
      elCount.textContent = '';
      PublicUI.showError(
        elGrid,
        'Could not load achievements',
        'Something went wrong while fetching the achievement list. Please refresh the page, or try again in a few minutes.',
        '<button class="pub-btn pub-btn-outline" type="button" onclick="window.location.reload()">Reload page</button>'
      );
    }
  }

  document.addEventListener('DOMContentLoaded', function () {
    elForm       = document.getElementById('achFilterForm');
    elKeyword    = document.getElementById('achKeyword');
    elDepartment = document.getElementById('achDepartment');
    elYear       = document.getElementById('achYear');
    elSort       = document.getElementById('achSort');
    elTabs       = document.getElementById('achTabs');
    elGrid       = document.getElementById('achGrid');
    elCount      = document.getElementById('achResultCount');
    elPagination = document.getElementById('achPagination');
    elNotice     = document.getElementById('achSampleNotice');

    if (!elGrid) return;

    initFilters();
    load();
  });

})();
