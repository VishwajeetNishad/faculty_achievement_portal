/* ====================================================================
   public-share.js — the anonymous view of one shared achievement
   --------------------------------------------------------------------
   Reads ?t=<token> and calls GET /api/public/share/{token}.

   The backend decides everything that matters. It answers:

     200  → the research, as SharedAchievementResponse
     404  → no such token
     410  + reason EXPIRED  → the link's time ran out
     410  + reason REVOKED  → the owner withdrew it

   The countdown drawn on this page is decoration and nothing else. The
   server re-checks the expiry on every single request, including the
   proof-document download, so a link that dies while this page is open
   is already dead at the server whether or not the timer has noticed.
   That is deliberate: a clock in a browser can be changed by whoever is
   looking at it, so it can never be what protects the research.

   No token is ever written into the page text, only into the URL the
   visitor already has and into the download link. Nothing is stored.
   ==================================================================== */

(function () {
  'use strict';

  const E = PublicUI.escapeHtml;

  let COUNTDOWN_TIMER = null;
  let EXPIRES_AT_MS = null;   // null = permanent, so no countdown at all

  document.addEventListener('DOMContentLoaded', load);

  /* ================================================================
     Load
     ================================================================ */
  async function load() {
    const root = document.getElementById('shareRoot');
    if (!root) return;

    const token = PublicUI.queryParam('t');

    // Nothing to look up. Say so plainly rather than firing a request for
    // an empty token and reporting whatever the server says about that.
    if (!token) {
      renderState(root, 'missing');
      return;
    }

    const res = await PublicApi.getRaw('/public/share/' + encodeURIComponent(token));

    if (res.ok && res.body) {
      renderAchievement(root, res.body, token);
      return;
    }

    if (res.status === 404) { renderState(root, 'unknown'); return; }

    if (res.status === 410) {
      const reason = String((res.body && res.body.reason) || '').toUpperCase();
      renderState(root, reason === 'REVOKED' ? 'revoked' : 'expired');
      return;
    }

    if (res.status === 0) { renderState(root, 'offline'); return; }

    renderState(root, 'error', res.status);
  }

  /* ================================================================
     The research itself
     ================================================================ */
  function renderAchievement(root, data, token) {
    root.removeAttribute('aria-busy');

    const meta = PublicUI.categoryMeta(data.categoryCode);
    const permanent = !!data.permanent;
    EXPIRES_AT_MS = (!permanent && data.expiresAt) ? new Date(data.expiresAt).getTime() : null;

    const keywords = String(data.keywords || '')
      .split(',')
      .map(function (k) { return k.trim(); })
      .filter(Boolean);

    const authorSlug = data.facultySlug
      ? PublicUI.rootPath('public/faculty-profile.html?u=' + encodeURIComponent(data.facultySlug))
      : null;

    root.innerHTML =
      // ── What this page is, before anything else ──────────────────
      '<div class="pub-share-banner">' +
        '<svg fill="none" stroke="currentColor" viewBox="0 0 24 24">' +
          '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" ' +
          'd="M13.828 10.172a4 4 0 010 5.656l-3 3a4 4 0 01-5.656-5.656l1.5-1.5M10.172 13.828a4 4 0 010-5.656l3-3a4 4 0 015.656 5.656l-1.5 1.5"/></svg>' +
        '<span><strong>Shared privately with you.</strong> ' +
        'This record was shared through a private link by the faculty member below. ' +
        'It is not listed in the public directory or in search results. ' +
        'Please do not redistribute it without their permission.</span>' +
      '</div>' +

      // ── Expiry ───────────────────────────────────────────────────
      (permanent
        ? '<div class="pub-share-expiry is-permanent">' +
            '<span class="pub-share-expiry-label">Access</span>' +
            '<span class="pub-share-expiry-value">This link does not expire</span>' +
          '</div>'
        : '<div class="pub-share-expiry" id="shareExpiryBar">' +
            '<span class="pub-share-expiry-label">Access expires in</span>' +
            '<span class="pub-share-expiry-value" id="shareCountdown">…</span>' +
            '<span class="pub-share-expiry-abs">' +
              (data.expiresAt ? E(formatDateTime(data.expiresAt)) : '') +
            '</span>' +
          '</div>') +

      // ── The record ───────────────────────────────────────────────
      // --cat-color / --cat-bg are how public-theme.css tints .pub-cat-chip;
      // the chip itself carries no per-category class.
      '<article class="pub-card" style="margin-bottom: 1.25rem; ' +
              '--cat-color:' + meta.color + '; --cat-bg:' + meta.bg + ';">' +
        '<div class="pub-card-pad">' +

          '<div class="pub-ach-card-top">' +
            '<span class="pub-cat-chip has-icon">' +
              PublicUI.categoryIcon(data.categoryCode) +
              E(data.categoryName || meta.label) +
            '</span>' +
            '<span class="pub-ach-date">' + E(PublicUI.formatMonthYear(data.achievementDate)) + '</span>' +
          '</div>' +

          '<h1 class="pub-share-title">' + E(data.title || 'Untitled') + '</h1>' +

          (data.academicYear
            ? '<p class="pub-share-subtle">Academic year ' + E(data.academicYear) + '</p>'
            : '') +

          (data.description
            ? '<div class="pub-share-abstract">' +
                '<h2 class="pub-share-h2">Abstract</h2>' +
                '<p>' + E(data.description) + '</p>' +
              '</div>'
            : '') +

          PublicUI.detailLineFor(data) +
          PublicUI.doiLinkFor(data) +

          (keywords.length
            ? '<div class="pub-share-keywords">' +
                keywords.map(function (k) {
                  return '<span class="pub-keyword">' + E(k) + '</span>';
                }).join('') +
              '</div>'
            : '') +

        '</div>' +

        // ── Who shared it ─────────────────────────────────────────
        '<div class="pub-share-author">' +
          '<span class="pub-avatar">' + E(PublicUI.initialsFrom(data.facultyName)) + '</span>' +
          '<span class="pub-ach-author">' +
            (authorSlug
              ? '<a class="pub-ach-author-name" href="' + authorSlug + '">' + E(data.facultyName || '') + '</a>'
              : '<span class="pub-ach-author-name">' + E(data.facultyName || '') + '</span>') +
            '<span class="pub-ach-author-dept">' +
              E([data.designation, data.departmentName || data.departmentCode].filter(Boolean).join(' · ')) +
            '</span>' +
          '</span>' +
          (data.sharedAt
            ? '<span class="pub-share-author-when">Shared ' + E(formatDateTime(data.sharedAt)) + '</span>'
            : '') +
        '</div>' +
      '</article>' +

      // ── Proof document, only when the owner allowed it ───────────
      (data.proofDocumentAvailable
        ? '<div class="pub-card pub-card-pad pub-share-doc">' +
            '<div>' +
              '<h2 class="pub-share-h2" style="margin: 0 0 0.2rem;">Supporting document</h2>' +
              '<p class="pub-share-subtle" style="margin: 0;">' +
                'The faculty member chose to include the proof document with this link.' +
              '</p>' +
            '</div>' +
            '<a class="pub-btn pub-btn-primary" id="shareDocBtn" ' +
               'href="' + E(PublicApi.urlFor('/public/share/' + encodeURIComponent(token) + '/document')) + '" ' +
               'target="_blank" rel="noopener noreferrer">' +
              '<svg style="width:15px;height:15px;" fill="none" stroke="currentColor" viewBox="0 0 24 24">' +
                '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" ' +
                'd="M7 21h10a2 2 0 002-2V9.414a1 1 0 00-.293-.707l-5.414-5.414A1 1 0 0012.586 3H7a2 2 0 00-2 2v14a2 2 0 002 2z"/></svg>' +
              'Open the document' +
            '</a>' +
          '</div>'
        : '') +

      // ── Where to go next ────────────────────────────────────────
      '<div class="pub-share-footnote">' +
        (data.facultySlug
          ? 'See this faculty member\'s publicly listed work on their ' +
            '<a href="' + authorSlug + '">profile page</a>, or '
          : 'You can ') +
        'browse the whole <a href="' + PublicUI.rootPath('public/achievements.html') + '">public achievement gallery</a>. ' +
        'NIET faculty and staff can <a href="' + PublicUI.rootPath('pages/login.html') + '">sign in to the portal</a>.' +
      '</div>';

    if (EXPIRES_AT_MS !== null) startCountdown();
  }

  /* ================================================================
     Countdown — visual only
     ================================================================ */
  function startCountdown() {
    if (COUNTDOWN_TIMER) clearInterval(COUNTDOWN_TIMER);
    tick();
    COUNTDOWN_TIMER = setInterval(tick, 1000);
  }

  function tick() {
    const el = document.getElementById('shareCountdown');
    const bar = document.getElementById('shareExpiryBar');
    if (!el || EXPIRES_AT_MS === null) return;

    const msLeft = EXPIRES_AT_MS - Date.now();

    if (msLeft <= 0) {
      clearInterval(COUNTDOWN_TIMER);
      COUNTDOWN_TIMER = null;
      // Re-ask the server rather than deciding locally. If our clock is
      // wrong the server will still hand the record back, and the visitor
      // keeps reading instead of being shown a false "expired" screen.
      recheckExpiry();
      return;
    }

    el.textContent = remainingText(msLeft);
    if (bar) {
      bar.classList.toggle('is-soon', msLeft < 60 * 60 * 1000);
    }
  }

  async function recheckExpiry() {
    const root = document.getElementById('shareRoot');
    const token = PublicUI.queryParam('t');
    if (!root || !token) return;

    const res = await PublicApi.getRaw('/public/share/' + encodeURIComponent(token));

    if (res.ok && res.body) {
      // Still alive — the owner extended it, or our clock ran fast.
      renderAchievement(root, res.body, token);
      return;
    }

    if (res.status === 410) {
      const reason = String((res.body && res.body.reason) || '').toUpperCase();
      renderState(root, reason === 'REVOKED' ? 'revoked' : 'expired');
      return;
    }
    if (res.status === 404) { renderState(root, 'unknown'); return; }

    // Could not reach the server. Say the link has expired, because that is
    // what the timer observed, and offer a reload.
    renderState(root, 'expired');
  }

  function remainingText(msLeft) {
    const totalSeconds = Math.floor(msLeft / 1000);
    const days = Math.floor(totalSeconds / 86400);
    const hours = Math.floor((totalSeconds % 86400) / 3600);
    const minutes = Math.floor((totalSeconds % 3600) / 60);
    const seconds = totalSeconds % 60;

    if (days > 0) return days + 'd ' + hours + 'h ' + minutes + 'm';
    if (hours > 0) return hours + 'h ' + minutes + 'm ' + seconds + 's';
    if (minutes > 0) return minutes + 'm ' + seconds + 's';
    return seconds + 's';
  }

  /* ================================================================
     The five things that can go wrong, each explained in plain words
     ================================================================ */
  const STATES = {
    missing: {
      variant: 'warn',
      icon: 'link',
      title: 'This address is incomplete',
      text: 'A share link ends in a long code, like ?t=… — this one has no code in it. ' +
            'Please open the full link exactly as it was sent to you.'
    },
    unknown: {
      variant: 'warn',
      icon: 'question',
      title: 'This link does not exist',
      text: 'No shared research matches this code. Links are long, so the most common ' +
            'cause is a link that was cut short when it was copied or pasted. ' +
            'Ask the sender to share it again.'
    },
    expired: {
      variant: 'expired',
      icon: 'clock',
      title: 'This link has expired',
      text: 'The faculty member set this link to work for a limited time, and that time ' +
            'has now passed. The research itself is safe — only the link stopped working. ' +
            'Ask them for a fresh link if you still need access.'
    },
    revoked: {
      variant: 'revoked',
      icon: 'ban',
      title: 'This link was withdrawn',
      text: 'The faculty member has revoked this link, so it no longer opens their research. ' +
            'If you believe you should still have access, please contact them directly.'
    },
    offline: {
      variant: 'error',
      icon: 'warn',
      title: 'Cannot reach the portal',
      text: 'The Faculty Achievement Portal did not respond. This is usually a temporary ' +
            'network problem — please try again in a moment.'
    },
    error: {
      variant: 'error',
      icon: 'warn',
      title: 'Something went wrong',
      text: 'The portal could not open this shared research. Please try again in a moment.'
    }
  };

  const ICONS = {
    link: 'M13.828 10.172a4 4 0 010 5.656l-3 3a4 4 0 01-5.656-5.656l1.5-1.5M10.172 13.828a4 4 0 010-5.656l3-3a4 4 0 015.656 5.656l-1.5 1.5',
    question: 'M8.228 9c.549-1.165 2.03-2 3.772-2 2.21 0 4 1.343 4 3 0 1.4-1.278 2.575-3.006 2.907-.542.104-.994.54-.994 1.093m0 3h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z',
    clock: 'M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z',
    ban: 'M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728A9 9 0 015.636 5.636m12.728 12.728L5.636 5.636',
    warn: 'M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z'
  };

  function renderState(root, key, status) {
    const state = STATES[key] || STATES.error;
    root.removeAttribute('aria-busy');

    const detail = (key === 'error' && status)
      ? '<p class="pub-share-status-code">Server responded with HTTP ' + E(String(status)) + '.</p>'
      : '';

    root.innerHTML =
      '<div class="pub-card pub-card-pad pub-share-state is-' + E(state.variant) + '">' +
        '<span class="pub-share-state-icon">' +
          '<svg fill="none" stroke="currentColor" viewBox="0 0 24 24">' +
            '<path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="' +
            ICONS[state.icon] + '"/></svg>' +
        '</span>' +
        '<h1 class="pub-share-state-title">' + E(state.title) + '</h1>' +
        '<p class="pub-share-state-text">' + E(state.text) + '</p>' +
        detail +
        '<div class="pub-share-state-actions">' +
          '<a class="pub-btn pub-btn-primary" href="' +
            PublicUI.rootPath('public/achievements.html') + '">Browse public achievements</a>' +
          '<a class="pub-btn pub-btn-outline" href="' +
            PublicUI.rootPath('public/faculty.html') + '">Faculty directory</a>' +
          ((key === 'offline' || key === 'error')
            ? '<button class="pub-btn pub-btn-outline" id="shareRetryBtn">Try again</button>'
            : '') +
        '</div>' +
      '</div>';

    const retry = document.getElementById('shareRetryBtn');
    if (retry) retry.addEventListener('click', function () { window.location.reload(); });
  }

  /* ================================================================
     Formatting
     ================================================================ */
  function formatDateTime(value) {
    if (!value) return '';
    const d = new Date(value);
    if (isNaN(d.getTime())) return '';
    return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' }) +
           ', ' + d.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });
  }

})();
