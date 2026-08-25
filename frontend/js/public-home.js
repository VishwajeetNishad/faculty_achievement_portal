/* ====================================================================
   public-home.js — the home page
   --------------------------------------------------------------------
   Fills the four dynamic blocks on index.html:
     · the "at a glance" stat strip
     · Featured achievements, with category tabs
     · the faculty preview row
     · the category pills

   Every number and every card on this page is read from the live public
   API. Nothing is hardcoded and there is no placeholder content: if the
   API cannot be reached, the page says so instead of showing figures
   that were never real.
   ==================================================================== */

(function () {

  const FEATURED_LIMIT = 6;
  const FACULTY_PREVIEW_LIMIT = 4;

  /* Filled by load(); the cards need author names, and the achievement
     payload only carries a slug. */
  let facultyBySlug = {};
  let allAchievements = [];
  let activeCategory = 'ALL';

  /* ================================================================
     Data loading

     getOrFail, not tryGet — the whole page is built from these two
     lists, so if either one fails there is nothing honest to draw and
     load()'s catch block takes over.
     ================================================================ */

  async function loadFaculty() {
    const live = await PublicApi.getOrFail('/public/faculty', { page: 0, size: 200 });
    return Array.isArray(live) ? live : (live.content || []);
  }

  async function loadAchievements() {
    const live = await PublicApi.getOrFail('/public/achievements', { page: 0, size: 100 });
    const rows = Array.isArray(live) ? live : (live.content || []);
    /* The backend is the authority on visibility — it only ever sends
       APPROVED + PUBLIC records. Re-filtering here is belt-and-braces,
       not the actual control. */
    return PublicUI.filterPublic(rows);
  }

  /* ================================================================
     Stat strip
     ================================================================ */

  const STAT_ICONS = {
    faculty:
      '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z"/>',
    achievements:
      '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 3v4M3 5h4M6 17v4m-2-2h4m5-16l2.286 6.857L21 12l-5.714 2.143L13 21l-2.286-6.857L5 12l5.714-2.143L13 3z"/>',
    publications:
      '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"/>',
    departments:
      '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4"/>'
  };

  function statMarkup(iconKey, colour, background, value, label) {
    return '' +
      '<div class="pub-stat">' +
        '<span class="pub-stat-icon" style="background:' + background + ';color:' + colour + ';">' +
          '<svg fill="none" stroke="currentColor" viewBox="0 0 24 24">' + STAT_ICONS[iconKey] + '</svg>' +
        '</span>' +
        '<span class="pub-stat-value">' + value + '</span>' +
        '<span class="pub-stat-label">' + PublicUI.escapeHtml(label) + '</span>' +
      '</div>';
  }

  function renderStats(stats) {
    const container = document.getElementById('homeStats');
    if (!container) return;

    container.innerHTML =
      statMarkup('faculty',      '#E11D48', 'rgba(225,29,72,0.08)', stats.facultyCount,     'Faculty listed') +
      statMarkup('achievements', '#D97706', '#FEF3C7',              stats.achievementCount, 'Public achievements') +
      statMarkup('publications', '#0284C7', '#E0F2FE',              stats.publicationCount, 'Publications') +
      statMarkup('departments',  '#059669', '#D1FAE5',              stats.departmentCount,  'Departments');
  }

  /** Counts are derived from the loaded lists, never typed in, so the
      numbers on screen always match the cards below them. */
  function deriveStats(faculty, achievements) {
    const contributing = {};
    achievements.forEach(function (a) {
      if (a.facultySlug || a.facultyName) contributing[a.facultySlug || a.facultyName] = true;
    });

    const departments = {};
    faculty.forEach(function (p) {
      if (p.departmentCode) departments[p.departmentCode] = true;
    });

    return {
      facultyCount: Object.keys(contributing).length || faculty.length,
      achievementCount: achievements.length,
      publicationCount: achievements.filter(function (a) { return a.categoryCode === 'PUBLICATION'; }).length,
      departmentCount: Object.keys(departments).length
    };
  }

  /* ================================================================
     Featured achievements
     ================================================================ */

  function renderTabs() {
    const container = document.getElementById('featuredTabs');
    if (!container) return;

    const counts = {};
    allAchievements.forEach(function (a) {
      counts[a.categoryCode] = (counts[a.categoryCode] || 0) + 1;
    });

    let html = '<button class="pub-tab' + (activeCategory === 'ALL' ? ' is-active' : '') + '"' +
               ' type="button" role="tab" data-category="ALL"' +
               ' aria-selected="' + (activeCategory === 'ALL') + '">' +
               'All (' + allAchievements.length + ')</button>';

    PublicUI.CATEGORY_ORDER.forEach(function (code) {
      const count = counts[code] || 0;
      /* A category with nothing in it would be a dead tab, so hide it. */
      if (count === 0) return;
      const meta = PublicUI.categoryMeta(code);
      const isActive = activeCategory === code;
      html += '<button class="pub-tab' + (isActive ? ' is-active' : '') + '"' +
              ' type="button" role="tab" data-category="' + PublicUI.escapeHtml(code) + '"' +
              ' aria-selected="' + isActive + '">' +
              PublicUI.escapeHtml(meta.short) + ' (' + count + ')</button>';
    });

    container.innerHTML = html;

    container.querySelectorAll('.pub-tab').forEach(function (button) {
      button.addEventListener('click', function () {
        activeCategory = button.dataset.category;
        renderTabs();
        renderFeatured();
      });
    });
  }

  function renderFeatured() {
    const grid = document.getElementById('featuredGrid');
    if (!grid) return;

    let rows = activeCategory === 'ALL'
      ? allAchievements.slice()
      : allAchievements.filter(function (a) { return a.categoryCode === activeCategory; });

    /* Anything the owner flagged as featured floats to the top; the rest
       fall back to most recent first. */
    rows.sort(function (a, b) {
      const aFeatured = a.featured ? 1 : 0;
      const bFeatured = b.featured ? 1 : 0;
      if (aFeatured !== bFeatured) return bFeatured - aFeatured;
      return String(b.achievementDate || '').localeCompare(String(a.achievementDate || ''));
    });

    rows = rows.slice(0, FEATURED_LIMIT);

    if (rows.length === 0) {
      PublicUI.showEmpty(
        grid,
        'Nothing published in this category yet',
        'Once a faculty member marks an achievement in this category as public and it has been verified, it will appear here.'
      );
      return;
    }

    grid.removeAttribute('aria-busy');
    grid.innerHTML = rows.map(function (achievement) {
      return PublicUI.achievementCard(achievement, authorFor(achievement));
    }).join('');
  }

  /** Resolve the achievement's author from whatever the payload carries. */
  function authorFor(achievement) {
    const fromDirectory = facultyBySlug[achievement.facultySlug];
    if (fromDirectory) return fromDirectory;

    /* A live PublicAchievementResponse may embed the author directly. */
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

  /* ================================================================
     Faculty preview
     ================================================================ */

  function renderFacultyPreview(faculty) {
    const grid = document.getElementById('facultyPreviewGrid');
    if (!grid) return;

    /* Lead with the people who actually have something published — an
       empty profile is a poor first impression of the directory. */
    const rows = faculty
      .slice()
      .sort(function (a, b) {
        return (b.publicAchievementCount || 0) - (a.publicAchievementCount || 0);
      })
      .filter(function (p) { return (p.publicAchievementCount || 0) > 0; })
      .slice(0, FACULTY_PREVIEW_LIMIT);

    if (rows.length === 0) {
      PublicUI.showEmpty(
        grid,
        'No public profiles yet',
        'Faculty profiles appear here as soon as they have at least one verified, publicly listed achievement.',
        '<a class="pub-btn pub-btn-outline" href="public/faculty.html">Open the directory</a>'
      );
      return;
    }

    grid.removeAttribute('aria-busy');
    grid.innerHTML = rows.map(PublicUI.facultyCard).join('');
  }

  /* ================================================================
     Category pills
     ================================================================ */

  function renderCategoryPills() {
    const container = document.getElementById('categoryPills');
    if (!container) return;

    const counts = {};
    allAchievements.forEach(function (a) {
      counts[a.categoryCode] = (counts[a.categoryCode] || 0) + 1;
    });

    container.innerHTML = PublicUI.CATEGORY_ORDER.map(function (code) {
      const meta = PublicUI.categoryMeta(code);
      const count = counts[code] || 0;
      const href = 'public/achievements.html?category=' + encodeURIComponent(code);

      return '' +
        '<a class="pub-pill" href="' + href + '">' +
          '<span class="pub-pill-icon" style="background:' + meta.bg + ';color:' + meta.color + ';">' +
            PublicUI.categoryIcon(code) +
          '</span>' +
          '<span>' +
            '<span class="pub-pill-name">' + PublicUI.escapeHtml(meta.label) + '</span>' +
            '<span class="pub-pill-count">' + count + (count === 1 ? ' record' : ' records') + '</span>' +
          '</span>' +
        '</a>';
    }).join('');
  }

  /* ================================================================
     Hero search — hands off to whichever page can answer the question
     ================================================================ */

  function initHeroSearch() {
    const form = document.getElementById('heroSearchForm');
    if (!form) return;

    form.addEventListener('submit', function (event) {
      event.preventDefault();

      const keyword = (document.getElementById('heroSearchInput').value || '').trim();
      const scope = document.getElementById('heroSearchScope').value;
      const target = scope === 'faculty' ? 'public/faculty.html' : 'public/achievements.html';

      window.location.href = keyword
        ? target + '?q=' + encodeURIComponent(keyword)
        : target;
    });
  }

  /* ================================================================
     Boot
     ================================================================ */

  async function load() {
    const featuredGrid = document.getElementById('featuredGrid');
    const facultyGrid = document.getElementById('facultyPreviewGrid');

    PublicUI.showSkeletons(featuredGrid, 3);
    PublicUI.showSkeletons(facultyGrid, 4);

    try {
      const results = await Promise.all([loadFaculty(), loadAchievements()]);
      const faculty = results[0];
      allAchievements = results[1];

      facultyBySlug = {};
      faculty.forEach(function (person) {
        if (person.slug) facultyBySlug[person.slug] = person;
      });

      renderStats(deriveStats(faculty, allAchievements));
      renderTabs();
      renderFeatured();
      renderFacultyPreview(faculty);
      renderCategoryPills();

    } catch (error) {
      console.error('[public-home] failed to build the page', error);

      /* Take the stat strip and the tabs down rather than leaving them
         blank. A strip of empty boxes reads as "zero achievements", which
         is a different — and wrong — statement from "we could not load
         the numbers just now". */
      const stats = document.getElementById('homeStats');
      if (stats) stats.closest('.pub-stats').hidden = true;
      const tabs = document.getElementById('featuredTabs');
      if (tabs) tabs.innerHTML = '';

      const message = 'The portal could not load its content just now. Please refresh the page, or try again in a few minutes.';
      PublicUI.showError(featuredGrid, 'Could not load achievements', message,
        '<button class="pub-btn pub-btn-outline" type="button" onclick="window.location.reload()">Reload page</button>');
      PublicUI.showError(facultyGrid, 'Could not load faculty', message);
    }
  }

  document.addEventListener('DOMContentLoaded', function () {
    initHeroSearch();
    load();
  });

})();
