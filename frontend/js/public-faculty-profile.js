/* ====================================================================
   public-faculty-profile.js — one faculty member's public profile
   --------------------------------------------------------------------
   Drives public/faculty-profile.html, which is opened as
       faculty-profile.html?u=<public-slug>
   from the directory cards, the achievement cards and the home page.

   What this page deliberately does NOT show:
   The users table holds employeeId, email, phone and passwordHash. None
   of those belong on a page anyone on the internet can open, so none of
   them are requested or rendered here — the public profile is name,
   designation, department, slug and the person's own public achievements,
   and nothing else. There are also no photos, bios, education histories
   or external profile links, because no such columns exist; inventing
   them would mean inventing data. The initials circle stands in for a
   photograph.

   Every achievement passes through PublicUI.filterPublic() before it can
   reach the screen: APPROVED + PUBLIC only. PENDING, REJECTED, PRIVATE
   and UNLISTED records must never appear here, and the reviewer comment
   attached to a rejection is never fetched at all.
   ==================================================================== */

(function () {

  const TIMELINE_LIMIT = 12;   /* Newest first; a "view all" link follows. */
  const CITATION_LIMIT = 10;

  let elGrid, elSidebar, elMain, elState, elCrumb;

  /* ================================================================
     Data loading
     ================================================================ */

  async function loadProfile(slug) {
    /* getRaw, because a 404 here is not an error — it means "no such
       faculty member", which has its own, gentler message. Any other
       non-2xx really is a fault and is thrown for the catch block. */
    const res = await PublicApi.getRaw('/public/faculty/' + encodeURIComponent(slug));
    if (res.ok && res.body) return res.body;
    if (res.status === 404) return null;
    throw new Error('GET public faculty profile returned ' + res.status);
  }

  async function loadAchievements(slug) {
    /* Only reached once the profile is known to exist, so the endpoint
       returns a (possibly empty) page. getOrFail: if it faults, the page
       should say so rather than imply the person has published nothing. */
    const live = await PublicApi.getOrFail(
      '/public/faculty/' + encodeURIComponent(slug) + '/achievements',
      { page: 0, size: 200 }
    );
    const rows = Array.isArray(live) ? live : (live.content || []);
    /* Belt and braces — the backend is the authority. */
    return PublicUI.filterPublic(rows);
  }

  /* ================================================================
     Sidebar — identity + what this person works on
     ================================================================ */

  const ICON_BUILDING =
    '<svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4"/></svg>';

  const ICON_TAG =
    '<svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 7h.01M7 3h5a1.99 1.99 0 011.414.586l7 7a2 2 0 010 2.828l-7 7a2 2 0 01-2.828 0l-7-7A1.99 1.99 0 013 12V7a4 4 0 014-4z"/></svg>';

  const ICON_CLOCK =
    '<svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>';

  function metaRow(icon, key, value) {
    return '' +
      '<div class="pub-meta-row">' + icon +
        '<span>' +
          '<span class="pub-meta-key">' + PublicUI.escapeHtml(key) + '</span>' +
          '<span class="pub-meta-val">' + value + '</span>' +
        '</span>' +
      '</div>';
  }

  function renderSidebar(person, achievements, counts) {
    const categoryCodes = PublicUI.CATEGORY_ORDER.filter(function (code) {
      return counts[code] > 0;
    });

    /* "Areas of work" is built from the categories this person has
       actually published in. The reference design showed hand-written
       research-interest keywords, but there is no such column on users —
       deriving the chips from real records keeps the section honest. */
    const chips = categoryCodes.length
      ? categoryCodes.map(function (code) {
          const meta = PublicUI.categoryMeta(code);
          return '<span class="pub-cat-chip" style="--cat-color:' + meta.color +
                 ';--cat-bg:' + meta.bg + ';">' +
                 PublicUI.escapeHtml(meta.short) + ' · ' + counts[code] +
                 '</span>';
        }).join('')
      : '<span class="pub-meta-val" style="color:var(--text-tertiary);">Nothing published publicly yet</span>';

    const latest = achievements.length
      ? PublicUI.formatMonthYear(achievements[0].achievementDate)
      : '—';

    const departmentLabel = person.departmentName || person.departmentCode || 'Not recorded';
    const departmentHref = person.departmentCode
      ? 'faculty.html?dept=' + encodeURIComponent(person.departmentCode)
      : null;

    elSidebar.innerHTML = '' +
      '<div class="pub-card pub-identity">' +
        '<span class="pub-avatar is-lg">' +
          PublicUI.escapeHtml(PublicUI.initialsFrom(person.fullName)) +
        '</span>' +
        '<h1 class="pub-identity-name">' + PublicUI.escapeHtml(person.fullName) + '</h1>' +
        (person.designation
          ? '<span class="pub-identity-desig">' + PublicUI.escapeHtml(person.designation) + '</span>'
          : '') +
        '<span class="pub-identity-dept">' + PublicUI.escapeHtml(departmentLabel) + '</span>' +

        '<span class="pub-identity-divider"></span>' +

        '<div class="pub-meta-list">' +
          metaRow(ICON_BUILDING, 'Department',
            departmentHref
              ? '<a href="' + departmentHref + '" style="color:var(--primary-color);">' +
                PublicUI.escapeHtml(departmentLabel) + '</a>'
              : PublicUI.escapeHtml(departmentLabel)) +

          metaRow(ICON_TAG, 'Areas of work',
            '<span style="display:flex;flex-wrap:wrap;gap:0.35rem;margin-top:0.2rem;">' +
            chips + '</span>') +

          metaRow(ICON_CLOCK, 'Most recent public record', PublicUI.escapeHtml(latest)) +
        '</div>' +

        '<a class="pub-btn pub-btn-outline pub-btn-block" href="faculty.html" style="margin-top:1.5rem;">' +
          'Back to directory' +
        '</a>' +
      '</div>' +

      /* Explains the gaps before anyone wonders about them: a thin
         profile usually means most of that person's work is marked
         private, not that they have not published. */
      '<p style="font-size:var(--font-size-sm);color:var(--text-tertiary);line-height:1.6;margin-top:1rem;">' +
        'This profile lists only the achievements this faculty member has ' +
        'chosen to make public and which their department has verified. ' +
        'Records kept private, or still awaiting review, are not shown.' +
      '</p>';
  }

  /* ================================================================
     Metrics
     ================================================================ */

  const METRIC_ICONS = {
    total:
      '<svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 3v4M3 5h4M6 17v4m-2-2h4m5-16l2.286 6.857L21 12l-5.714 2.143L13 21l-2.286-6.857L5 12l5.714-2.143L13 3z"/></svg>',
    publications:
      '<svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"/></svg>',
    patents:
      '<svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"/></svg>'
  };

  function metricCard(iconKey, colour, value, label) {
    return '' +
      '<div class="pub-metric" style="--metric-color:' + colour + ';">' +
        METRIC_ICONS[iconKey] +
        '<div class="pub-metric-value">' + Number(value || 0) + '</div>' +
        '<div class="pub-metric-label">' + PublicUI.escapeHtml(label) + '</div>' +
      '</div>';
  }

  function metricsMarkup(achievements, counts) {
    return '' +
      '<div class="pub-metric-row">' +
        metricCard('total',        '#E11D48', achievements.length,      'Public achievements') +
        metricCard('publications', '#0284C7', counts.PUBLICATION || 0,  'Publications') +
        metricCard('patents',      '#0D9488', counts.PATENT || 0,       'Patents') +
      '</div>';
  }

  /* ================================================================
     Achievement timeline
     ================================================================ */

  function timelineItem(achievement) {
    const meta = PublicUI.categoryMeta(achievement.categoryCode);
    const doi = PublicUI.doiLinkFor(achievement);

    return '' +
      '<article class="pub-timeline-item" style="--cat-color:' + meta.color + ';--cat-bg:' + meta.bg + ';">' +
        '<div class="pub-timeline-top">' +
          '<span class="pub-cat-chip">' + PublicUI.escapeHtml(meta.short) + '</span>' +
          '<span class="pub-ach-date">' +
            PublicUI.escapeHtml(PublicUI.formatFullDate(achievement.achievementDate)) +
            (achievement.academicYear
              ? ' · AY ' + PublicUI.escapeHtml(achievement.academicYear)
              : '') +
          '</span>' +
        '</div>' +
        '<h3 class="pub-timeline-title">' + PublicUI.escapeHtml(achievement.title) + '</h3>' +
        (achievement.description
          ? '<p class="pub-timeline-desc">' + PublicUI.escapeHtml(achievement.description) + '</p>'
          : '') +
        PublicUI.detailLineFor(achievement) +
        (doi ? '<div style="margin-top:0.5rem;">' + doi + '</div>' : '') +
      '</article>';
  }

  function achievementsCardMarkup(person, achievements) {
    const shown = achievements.slice(0, TIMELINE_LIMIT);
    const hidden = achievements.length - shown.length;

    let body;
    if (shown.length === 0) {
      body =
        '<div class="pub-timeline">' +
          '<div class="pub-state">' +
            '<h3 class="pub-state-title">Nothing published publicly yet</h3>' +
            '<p class="pub-state-text">' +
              PublicUI.escapeHtml(person.fullName) + ' has no verified, publicly ' +
              'marked achievements on the portal at the moment. Anything kept ' +
              'private or still under review is not listed here.' +
            '</p>' +
          '</div>' +
        '</div>';
    } else {
      body =
        '<div class="pub-timeline">' +
          shown.map(timelineItem).join('') +
          (hidden > 0
            ? '<p style="font-size:var(--font-size-sm);color:var(--text-secondary);text-align:center;padding-top:0.5rem;">' +
              'and ' + hidden + ' more public ' + (hidden === 1 ? 'record' : 'records') +
              '</p>'
            : '') +
        '</div>';
    }

    return '' +
      '<section class="pub-card" style="margin-bottom:1.5rem;">' +
        '<div class="pub-card-head">' +
          '<h2 class="pub-card-title">' +
            '<svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M5 3v4M3 5h4M6 17v4m-2-2h4m5-16l2.286 6.857L21 12l-5.714 2.143L13 21l-2.286-6.857L5 12l5.714-2.143L13 3z"/></svg>' +
            'Public achievements' +
          '</h2>' +
          '<span style="font-size:var(--font-size-sm);color:var(--text-secondary);">' +
            achievements.length + ' total' +
          '</span>' +
        '</div>' +
        body +
      '</section>';
  }

  /* ================================================================
     Publications, written out as citations

     Built only from columns that exist on the publications table:
     journalConferenceName, publisher, publicationType, volume, issue,
     pages, indexing, impactFactor, isbnIssn and doi. There is no
     citation-count column, so no citation count is shown.
     ================================================================ */

  function citationBlock(achievement) {
    const publication = achievement.publication || {};
    const bits = [];

    if (publication.journalConferenceName) {
      bits.push('<em>' + PublicUI.escapeHtml(publication.journalConferenceName) + '</em>');
    }
    if (publication.volume) bits.push('Vol. ' + PublicUI.escapeHtml(publication.volume));
    if (publication.issue)  bits.push('Issue ' + PublicUI.escapeHtml(publication.issue));
    if (publication.pages)  bits.push('pp. ' + PublicUI.escapeHtml(publication.pages));
    if (achievement.achievementDate) {
      bits.push(PublicUI.escapeHtml(PublicUI.formatMonthYear(achievement.achievementDate)));
    }
    if (publication.publisher) bits.push(PublicUI.escapeHtml(publication.publisher));

    const tags = [];
    if (publication.publicationType) {
      tags.push(PublicUI.humanizeEnum(publication.publicationType));
    }
    if (publication.indexing && publication.indexing !== 'OTHER') {
      tags.push(PublicUI.humanizeEnum(publication.indexing) + ' indexed');
    }
    if (publication.impactFactor) {
      tags.push('Impact factor ' + PublicUI.escapeHtml(publication.impactFactor));
    }
    if (publication.isbnIssn) {
      tags.push('ISBN/ISSN ' + PublicUI.escapeHtml(publication.isbnIssn));
    }

    const doi = PublicUI.doiLinkFor(achievement);

    return '' +
      '<div class="pub-cite">' +
        '<h3 class="pub-cite-title">' + PublicUI.escapeHtml(achievement.title) + '</h3>' +
        (bits.length ? '<p class="pub-cite-ref">' + bits.join(', ') + '</p>' : '') +
        (tags.length
          ? '<p style="display:flex;flex-wrap:wrap;gap:0.35rem;margin-bottom:0.4rem;">' +
            tags.map(function (tag) {
              return '<span class="pub-cat-chip" style="--cat-color:#0284C7;--cat-bg:#E0F2FE;">' +
                     PublicUI.escapeHtml(tag) + '</span>';
            }).join('') +
            '</p>'
          : '') +
        doi +
      '</div>';
  }

  function publicationsCardMarkup(publications) {
    /* No publications means no section at all — an empty card would just
       be noise on the profile of someone who works on patents or grants. */
    if (publications.length === 0) return '';

    const shown = publications.slice(0, CITATION_LIMIT);
    const hidden = publications.length - shown.length;

    return '' +
      '<section class="pub-card">' +
        '<div class="pub-card-head">' +
          '<h2 class="pub-card-title">' +
            '<svg fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253"/></svg>' +
            'Research &amp; publications' +
          '</h2>' +
          '<span style="font-size:var(--font-size-sm);color:var(--text-secondary);">' +
            publications.length + ' listed' +
          '</span>' +
        '</div>' +
        '<div class="pub-cite-list">' +
          shown.map(citationBlock).join('') +
          (hidden > 0
            ? '<p style="font-size:var(--font-size-sm);color:var(--text-secondary);padding-top:1rem;">' +
              'and ' + hidden + ' more ' + (hidden === 1 ? 'publication' : 'publications') +
              '</p>'
            : '') +
        '</div>' +
        /* A DOI is a public identifier, so linking out to the publisher is
           fine. The paper itself is never hosted or proxied by the portal. */
        '<p style="font-size:var(--font-size-sm);color:var(--text-tertiary);padding:0 1.5rem 1.5rem;line-height:1.6;">' +
          'DOI links open the publisher’s page. Full texts are not hosted here.' +
        '</p>' +
      '</section>';
  }

  /* ================================================================
     States
     ================================================================ */

  function showState(title, text, actionHtml, isError) {
    elGrid.hidden = true;
    /* Emptied as well as hidden — otherwise the loading skeletons keep
       their animation running behind the message for no reason. */
    elSidebar.innerHTML = '';
    elMain.innerHTML = '';
    elState.hidden = false;
    if (isError) {
      PublicUI.showError(elState, title, text, actionHtml);
    } else {
      PublicUI.showEmpty(elState, title, text, actionHtml);
    }
  }

  function showSkeleton() {
    elSidebar.innerHTML =
      '<div class="pub-skeleton-card" aria-hidden="true" style="min-height:340px;">' +
        '<div class="pub-sk-line is-title"></div>' +
        '<div class="pub-sk-line"></div>' +
        '<div class="pub-sk-line is-short"></div>' +
      '</div>';
    elMain.innerHTML = PublicUI.skeletonCards(3);
  }

  /* ================================================================
     Boot
     ================================================================ */

  async function load() {
    const slug = PublicUI.queryParam('u');

    /* Landing here with no slug at all — usually a hand-edited URL. */
    if (!slug) {
      elCrumb.textContent = 'Profile';
      showState(
        'No faculty member selected',
        'This page needs to know whose profile to show. Open a profile from the directory instead.',
        '<a class="pub-btn pub-btn-primary" href="faculty.html">Open the faculty directory</a>'
      );
      return;
    }

    showSkeleton();

    try {
      /* Load the profile first: if the slug is unknown there is no point
         fetching achievements, and the "not found" message is different
         from the "something broke" one. */
      const person = await loadProfile(slug);

      /* Unknown slug — a stale bookmark, a typo, or a profile that has
         since been deactivated. */
      if (!person) {
        elCrumb.textContent = 'Not found';
        showState(
          'That faculty profile could not be found',
          'The link may be out of date, or the profile may no longer be published. Try searching the directory instead.',
          '<a class="pub-btn pub-btn-primary" href="faculty.html">Search the directory</a>'
        );
        return;
      }

      let achievements = await loadAchievements(slug) || [];

      /* Newest first — a profile reads as a record of recent work. */
      achievements.sort(function (a, b) {
        return String(b.achievementDate || '').localeCompare(String(a.achievementDate || ''));
      });

      const counts = {};
      achievements.forEach(function (a) {
        counts[a.categoryCode] = (counts[a.categoryCode] || 0) + 1;
      });

      const publications = achievements.filter(function (a) {
        return a.categoryCode === 'PUBLICATION';
      });

      document.title = person.fullName + ' | NIET Faculty Achievement Portal';
      elCrumb.textContent = person.fullName;

      renderSidebar(person, achievements, counts);
      elMain.innerHTML =
        metricsMarkup(achievements, counts) +
        achievementsCardMarkup(person, achievements) +
        publicationsCardMarkup(publications);

    } catch (error) {
      console.error('[public-faculty-profile] failed to load the profile', error);
      showState(
        'Could not load this profile',
        'Something went wrong while fetching the profile. Please refresh the page, or try again in a few minutes.',
        '<button class="pub-btn pub-btn-outline" type="button" onclick="window.location.reload()">Reload page</button>',
        true
      );
    }
  }

  document.addEventListener('DOMContentLoaded', function () {
    elGrid    = document.getElementById('profileGrid');
    elSidebar = document.getElementById('profileSidebar');
    elMain    = document.getElementById('profileMain');
    elState   = document.getElementById('profileState');
    elCrumb   = document.getElementById('crumbName');

    if (!elGrid) return;
    load();
  });

})();
