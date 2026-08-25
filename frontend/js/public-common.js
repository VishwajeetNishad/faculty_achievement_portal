/* ====================================================================
   public-common.js — shared helpers for the public (no-login) pages
   --------------------------------------------------------------------
   Loaded by index.html and everything under public/. Holds the visibility
   rule, the category lookup, the card markup and the loading/empty/error
   states, so the four page scripts stay short and cannot drift apart.

   Path note: index.html sits at the web root and the other public pages
   sit one folder down, so every page declares its own prefix on <body>:
       <body class="public-body" data-root="">     ← index.html
       <body class="public-body" data-root="../">  ← public/*.html
   All links built here go through rootPath() so they resolve from either.
   ==================================================================== */

const PublicUI = (function () {

  /* ================================================================
     Paths
     ================================================================ */
  function rootPath(relative) {
    const prefix = (document.body && document.body.dataset.root) || '';
    return prefix + relative;
  }

  /* ================================================================
     Text safety
     Every value below originates from user-entered data (a faculty
     member typed the title of their own paper), so it is escaped before
     it reaches innerHTML — same rule the signed-in pages follow.
     ================================================================ */
  function escapeHtml(value) {
    if (value === null || value === undefined) return '';
    return String(value)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  /** "Dr. A. Sample" → "AS". Used for the avatar circles, because the
      users table has no photo column. */
  function initialsFrom(fullName) {
    if (!fullName) return '?';
    const words = String(fullName)
      .replace(/\b(Dr|Prof|Mr|Ms|Mrs|Shri|Smt)\.?\s*/gi, '')
      .trim()
      .split(/\s+/)
      .filter(Boolean);
    if (words.length === 0) return '?';
    if (words.length === 1) return words[0].charAt(0).toUpperCase();
    return (words[0].charAt(0) + words[words.length - 1].charAt(0)).toUpperCase();
  }

  /* ================================================================
     THE VISIBILITY RULE
     ----------------------------------------------------------------
     A record is public only when it is BOTH verified and marked public:

         status === 'APPROVED'  AND  visibility === 'PUBLIC'

     Which means:
       PRIVATE   → never public
       UNLISTED  → reachable only through a share link, and deliberately
                   absent from public search and the gallery
       PENDING   → not published; nobody has checked it yet
       REJECTED  → not published, and the reviewer's comment is never
                   exposed either

     ⚠️  This filter is for DISPLAY ONLY. It runs in the visitor's
     browser, so it is not a security control — anyone can edit it with
     dev tools. The backend must apply the identical rule inside the
     service layer of every /api/public/** endpoint, as a hard-coded
     condition and never as a client-supplied filter. When Track B is
     built, the API must not send non-public records to this page in the
     first place; this function is then only a second pair of eyes.
     ================================================================ */
  function isPubliclyVisible(achievement) {
    if (!achievement) return false;

    /* Guard against a field being absent rather than assuming it passes.
       An unknown status or visibility is treated as not public. */
    const status = achievement.status;
    const visibility = achievement.visibility;

    if (status !== 'APPROVED') return false;
    if (visibility !== 'PUBLIC') return false;
    return true;
  }

  function filterPublic(list) {
    if (!Array.isArray(list)) return [];
    return list.filter(isPubliclyVisible);
  }

  /* ================================================================
     Categories — the five rows seeded in achievement_categories.
     Colours are chosen from the existing palette so the public site
     and the signed-in portal read as one product.
     ================================================================ */
  const CATEGORIES = {
    PUBLICATION: {
      label: 'Research Publication', short: 'Publication',
      color: '#0284C7', bg: '#E0F2FE',
      icon: '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"/>'
    },
    PATENT: {
      label: 'Patent & Intellectual Property', short: 'Patent',
      color: '#0D9488', bg: '#CCFBF1',
      icon: '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4M7.835 4.697a3.42 3.42 0 001.946-.806 3.42 3.42 0 014.438 0 3.42 3.42 0 001.946.806 3.42 3.42 0 013.138 3.138 3.42 3.42 0 00.806 1.946 3.42 3.42 0 010 4.438 3.42 3.42 0 00-.806 1.946 3.42 3.42 0 01-3.138 3.138 3.42 3.42 0 00-1.946.806 3.42 3.42 0 01-4.438 0 3.42 3.42 0 00-1.946-.806 3.42 3.42 0 01-3.138-3.138 3.42 3.42 0 00-.806-1.946 3.42 3.42 0 010-4.438 3.42 3.42 0 00.806-1.946 3.42 3.42 0 013.138-3.138z"/>'
    },
    RESEARCH_GRANT: {
      label: 'Research & Consultancy Grant', short: 'Grant',
      color: '#059669', bg: '#D1FAE5',
      icon: '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z"/>'
    },
    WORKSHOP_FDP: {
      label: 'Workshop / FDP / Certification', short: 'Workshop / FDP',
      color: '#7C3AED', bg: '#EDE9FE',
      icon: '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 14l9-5-9-5-9 5 9 5zm0 0l6.16-3.422a12.083 12.083 0 01.665 6.479A11.952 11.952 0 0012 20.055a11.952 11.952 0 00-6.824-2.998 12.078 12.078 0 01.665-6.479L12 14zm-4 6v-7.5l4-2.222"/>'
    },
    AWARD: {
      label: 'Award & Recognition', short: 'Award',
      color: '#D97706', bg: '#FEF3C7',
      icon: '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4M7.835 4.697a3.42 3.42 0 001.946-.806 3.42 3.42 0 014.438 0 3.42 3.42 0 001.946.806 3.42 3.42 0 013.138 3.138 3.42 3.42 0 00.806 1.946 3.42 3.42 0 010 4.438 3.42 3.42 0 00-.806 1.946 3.42 3.42 0 01-3.138 3.138 3.42 3.42 0 00-1.946.806 3.42 3.42 0 01-4.438 0 3.42 3.42 0 00-1.946-.806 3.42 3.42 0 01-3.138-3.138 3.42 3.42 0 00-.806-1.946 3.42 3.42 0 010-4.438 3.42 3.42 0 00.806-1.946 3.42 3.42 0 013.138-3.138z"/>'
    }
  };

  const CATEGORY_ORDER = ['PUBLICATION', 'PATENT', 'RESEARCH_GRANT', 'WORKSHOP_FDP', 'AWARD'];

  /* An unknown code still renders, in the brand red, rather than
     breaking the card. */
  function categoryMeta(code) {
    return CATEGORIES[code] || {
      label: code || 'Achievement', short: code || 'Achievement',
      color: '#E11D48', bg: 'rgba(225,29,72,0.08)',
      icon: '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 3v4M3 5h4M6 17v4m-2-2h4m5-16l2.286 6.857L21 12l-5.714 2.143L13 21l-2.286-6.857L5 12l5.714-2.143L13 3z"/>'
    };
  }

  function categoryIcon(code, cssClass) {
    const meta = categoryMeta(code);
    return '<svg class="' + (cssClass || '') + '" fill="none" stroke="currentColor" viewBox="0 0 24 24">' +
           meta.icon + '</svg>';
  }

  /* ================================================================
     Formatting
     ================================================================ */
  const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
                  'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

  /** '2026-03-14' → 'Mar 2026'. Day-level precision is noise here. */
  function formatMonthYear(isoDate) {
    if (!isoDate) return '';
    const parts = String(isoDate).slice(0, 10).split('-');
    if (parts.length < 2) return String(isoDate);
    const monthIndex = parseInt(parts[1], 10) - 1;
    if (isNaN(monthIndex) || monthIndex < 0 || monthIndex > 11) return String(isoDate);
    return MONTHS[monthIndex] + ' ' + parts[0];
  }

  function formatFullDate(isoDate) {
    if (!isoDate) return '';
    const parts = String(isoDate).slice(0, 10).split('-');
    if (parts.length < 3) return formatMonthYear(isoDate);
    const monthIndex = parseInt(parts[1], 10) - 1;
    if (isNaN(monthIndex) || monthIndex < 0 || monthIndex > 11) return String(isoDate);
    return parseInt(parts[2], 10) + ' ' + MONTHS[monthIndex] + ' ' + parts[0];
  }

  /** 2850000 → '₹28.50 Lakh'; 14600000 → '₹1.46 Crore'. Indian
      grant figures are quoted this way, not as raw rupees. */
  function formatGrantAmount(amount) {
    const value = Number(amount);
    if (!amount || isNaN(value)) return '';
    if (value >= 10000000) return '₹' + (value / 10000000).toFixed(2) + ' Crore';
    if (value >= 100000)   return '₹' + (value / 100000).toFixed(2) + ' Lakh';
    return '₹' + value.toLocaleString('en-IN');
  }

  /** 'WEB_OF_SCIENCE' → 'Web of Science'. */
  function humanizeEnum(value) {
    if (!value) return '';
    const special = {
      WEB_OF_SCIENCE: 'Web of Science',
      UGC_CARE: 'UGC-CARE',
      SCOPUS: 'Scopus',
      BOOK_CHAPTER: 'Book Chapter',
      FDP: 'FDP',
      RESOURCE_PERSON: 'Resource Person'
    };
    if (special[value]) return special[value];
    return String(value)
      .toLowerCase()
      .split('_')
      .map(function (word) { return word.charAt(0).toUpperCase() + word.slice(1); })
      .join(' ');
  }

  /* ================================================================
     The category-specific detail line on a card
     Only fields that genuinely exist on the entities are read, and each
     one is optional — a record with no DOI simply shows no DOI.
     ================================================================ */
  function detailLineFor(achievement) {
    const rows = [];

    const publication = achievement.publication;
    if (publication) {
      if (publication.journalConferenceName) {
        rows.push('<strong>' + escapeHtml(publication.journalConferenceName) + '</strong>');
      }
      const bits = [];
      if (publication.publicationType) bits.push(humanizeEnum(publication.publicationType));
      if (publication.volume) bits.push('Vol. ' + escapeHtml(publication.volume));
      if (publication.issue)  bits.push('Issue ' + escapeHtml(publication.issue));
      if (publication.pages)  bits.push('pp. ' + escapeHtml(publication.pages));
      if (publication.indexing && publication.indexing !== 'OTHER') {
        bits.push(humanizeEnum(publication.indexing) + ' indexed');
      }
      if (bits.length) rows.push(bits.join(' · '));
    }

    const patent = achievement.patent;
    if (patent) {
      if (patent.patentNumber) rows.push('Patent No. <strong>' + escapeHtml(patent.patentNumber) + '</strong>');
      const bits = [];
      if (patent.patentStatus) bits.push(humanizeEnum(patent.patentStatus));
      if (patent.country) bits.push(escapeHtml(patent.country));
      if (bits.length) rows.push(bits.join(' · '));
    }

    const grant = achievement.researchGrant;
    if (grant) {
      if (grant.fundingAgency) rows.push('Funded by <strong>' + escapeHtml(grant.fundingAgency) + '</strong>');
      const bits = [];
      if (grant.grantAmount) bits.push(formatGrantAmount(grant.grantAmount));
      if (grant.projectType) bits.push(humanizeEnum(grant.projectType));
      if (grant.durationMonths) bits.push(grant.durationMonths + ' months');
      if (bits.length) rows.push(bits.join(' · '));
    }

    const workshop = achievement.workshopFdp;
    if (workshop) {
      if (workshop.eventName) rows.push('<strong>' + escapeHtml(workshop.eventName) + '</strong>');
      const bits = [];
      if (workshop.eventType) bits.push(humanizeEnum(workshop.eventType));
      if (workshop.role) bits.push(humanizeEnum(workshop.role));
      if (workshop.organizingBody) bits.push(escapeHtml(workshop.organizingBody));
      if (workshop.durationDays) bits.push(workshop.durationDays + ' days');
      if (bits.length) rows.push(bits.join(' · '));
    }

    const award = achievement.award;
    if (award) {
      if (award.awardName) rows.push('<strong>' + escapeHtml(award.awardName) + '</strong>');
      const bits = [];
      if (award.awardingBody) bits.push(escapeHtml(award.awardingBody));
      if (award.awardLevel) bits.push(humanizeEnum(award.awardLevel) + ' level');
      if (bits.length) rows.push(bits.join(' · '));
    }

    if (!rows.length) return '';
    return '<p class="pub-ach-meta">' + rows.join('<br>') + '</p>';
  }

  /** A DOI is a public identifier, so linking it out is fine. The paper
      itself is never hosted or proxied here. */
  function doiLinkFor(achievement) {
    const doi = achievement.publication && achievement.publication.doi;
    if (!doi) return '';
    const safe = escapeHtml(doi);

    /* encodeURI, not encodeURIComponent: a DOI always contains a slash
       ("10.1000/xyz123") and encodeURIComponent would turn it into %2F,
       which doi.org rejects. encodeURI keeps the slash but still escapes
       spaces, quotes and angle brackets, so the href stays safe. */
    const href = encodeURI('https://doi.org/' + String(doi).trim());

    return '<a class="pub-doi-link" href="' + href + '"' +
           ' target="_blank" rel="noopener noreferrer">' +
           '<svg fill="none" stroke="currentColor" viewBox="0 0 24 24">' +
           '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" ' +
           'd="M10 6H6a2 2 0 00-2 2v10a2 2 0 002 2h10a2 2 0 002-2v-4M14 4h6m0 0v6m0-6L10 14"/></svg>' +
           'doi.org/' + safe + '</a>';
  }

  /* ================================================================
     Cards
     ================================================================ */

  /**
   * One achievement card.
   * @param {Object} achievement
   * @param {Object} author  optional { fullName, designation, departmentName, slug }
   */
  function achievementCard(achievement, author) {
    const meta = categoryMeta(achievement.categoryCode);

    let footer = '';
    if (author && author.fullName) {
      const profileHref = author.slug
        ? rootPath('public/faculty-profile.html?u=' + encodeURIComponent(author.slug))
        : null;
      const nameMarkup = profileHref
        ? '<a class="pub-ach-author-name" href="' + profileHref + '">' + escapeHtml(author.fullName) + '</a>'
        : '<span class="pub-ach-author-name">' + escapeHtml(author.fullName) + '</span>';

      footer =
        '<div class="pub-ach-foot">' +
          '<span class="pub-avatar">' + escapeHtml(initialsFrom(author.fullName)) + '</span>' +
          '<span class="pub-ach-author">' +
            nameMarkup +
            '<span class="pub-ach-author-dept">' +
              escapeHtml(author.departmentName || author.departmentCode || '') +
            '</span>' +
          '</span>' +
        '</div>';
    }

    const doi = doiLinkFor(achievement);

    return '' +
      '<article class="pub-ach-card" style="--cat-color:' + meta.color + ';--cat-bg:' + meta.bg + ';">' +
        '<div class="pub-ach-card-top">' +
          '<span class="pub-cat-chip">' + escapeHtml(meta.short) + '</span>' +
          '<span class="pub-ach-date">' + escapeHtml(formatMonthYear(achievement.achievementDate)) + '</span>' +
        '</div>' +
        '<h3 class="pub-ach-title">' + escapeHtml(achievement.title) + '</h3>' +
        (achievement.description
          ? '<p class="pub-ach-desc">' + escapeHtml(achievement.description) + '</p>'
          : '') +
        detailLineFor(achievement) +
        (doi ? '<div style="margin-bottom:0.85rem;">' + doi + '</div>' : '') +
        footer +
      '</article>';
  }

  /** One faculty directory card. */
  function facultyCard(person) {
    const href = rootPath('public/faculty-profile.html?u=' + encodeURIComponent(person.slug || ''));
    return '' +
      '<article class="pub-fac-card">' +
        '<span class="pub-avatar is-lg">' + escapeHtml(initialsFrom(person.fullName)) + '</span>' +
        '<h3 class="pub-fac-name">' + escapeHtml(person.fullName) + '</h3>' +
        '<span class="pub-fac-desig">' + escapeHtml(person.designation || '') + '</span>' +
        '<span class="pub-fac-dept">' +
          escapeHtml(person.departmentName || person.departmentCode || '') +
        '</span>' +
        '<div class="pub-fac-counts">' +
          '<span class="pub-fac-count">' +
            '<span class="pub-fac-count-value">' + Number(person.publicAchievementCount || 0) + '</span>' +
            '<span class="pub-fac-count-label">Achievements</span>' +
          '</span>' +
          '<span class="pub-fac-count">' +
            '<span class="pub-fac-count-value">' + Number(person.publicationCount || 0) + '</span>' +
            '<span class="pub-fac-count-label">Publications</span>' +
          '</span>' +
        '</div>' +
        '<a class="pub-btn pub-btn-outline" href="' + href + '">View Profile</a>' +
      '</article>';
  }

  /* ================================================================
     Loading / empty / error states
     ================================================================ */
  function skeletonCards(count) {
    let html = '';
    for (let i = 0; i < (count || 3); i++) {
      html +=
        '<div class="pub-skeleton-card" aria-hidden="true">' +
          '<div class="pub-sk-line is-chip"></div>' +
          '<div class="pub-sk-line is-title"></div>' +
          '<div class="pub-sk-line"></div>' +
          '<div class="pub-sk-line"></div>' +
          '<div class="pub-sk-line is-short"></div>' +
        '</div>';
    }
    return html;
  }

  function showSkeletons(container, count) {
    if (!container) return;
    container.setAttribute('aria-busy', 'true');
    container.innerHTML = skeletonCards(count);
  }

  const ICON_EMPTY =
    '<svg fill="none" stroke="currentColor" viewBox="0 0 24 24">' +
    '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" ' +
    'd="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/></svg>';

  const ICON_ERROR =
    '<svg fill="none" stroke="currentColor" viewBox="0 0 24 24">' +
    '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" ' +
    'd="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"/></svg>';

  function showEmpty(container, title, text, actionHtml) {
    if (!container) return;
    container.removeAttribute('aria-busy');
    container.innerHTML =
      '<div class="pub-state">' +
        '<span class="pub-state-icon">' + ICON_EMPTY + '</span>' +
        '<h3 class="pub-state-title">' + escapeHtml(title) + '</h3>' +
        '<p class="pub-state-text">' + escapeHtml(text) + '</p>' +
        (actionHtml || '') +
      '</div>';
  }

  function showError(container, title, text, actionHtml) {
    if (!container) return;
    container.removeAttribute('aria-busy');
    container.innerHTML =
      '<div class="pub-state is-error">' +
        '<span class="pub-state-icon">' + ICON_ERROR + '</span>' +
        '<h3 class="pub-state-title">' + escapeHtml(title) + '</h3>' +
        '<p class="pub-state-text">' + escapeHtml(text) + '</p>' +
        (actionHtml || '') +
      '</div>';
  }

  /* ================================================================
     Navigation — the mobile drawer
     ================================================================ */
  function initNav() {
    const toggle = document.getElementById('pubNavToggle');
    const links = document.getElementById('pubNavLinks');
    if (!toggle || !links) return;

    toggle.addEventListener('click', function () {
      const open = links.classList.toggle('is-open');
      toggle.setAttribute('aria-expanded', open ? 'true' : 'false');
    });

    /* Tapping a link should close the drawer, otherwise it covers the
       section the visitor just jumped to. */
    links.addEventListener('click', function (event) {
      if (event.target.closest('a')) {
        links.classList.remove('is-open');
        toggle.setAttribute('aria-expanded', 'false');
      }
    });
  }

  /** Read one query-string value, e.g. ?u=a-sample-faculty-cse */
  function queryParam(name) {
    return new URLSearchParams(window.location.search).get(name);
  }

  /* ================================================================
     Public surface
     ================================================================ */
  return {
    rootPath: rootPath,
    escapeHtml: escapeHtml,
    initialsFrom: initialsFrom,

    isPubliclyVisible: isPubliclyVisible,
    filterPublic: filterPublic,

    CATEGORIES: CATEGORIES,
    CATEGORY_ORDER: CATEGORY_ORDER,
    categoryMeta: categoryMeta,
    categoryIcon: categoryIcon,

    formatMonthYear: formatMonthYear,
    formatFullDate: formatFullDate,
    formatGrantAmount: formatGrantAmount,
    humanizeEnum: humanizeEnum,

    detailLineFor: detailLineFor,
    doiLinkFor: doiLinkFor,
    achievementCard: achievementCard,
    facultyCard: facultyCard,

    skeletonCards: skeletonCards,
    showSkeletons: showSkeletons,
    showEmpty: showEmpty,
    showError: showError,

    initNav: initNav,
    queryParam: queryParam
  };

})();

document.addEventListener('DOMContentLoaded', function () {
  PublicUI.initNav();
});
