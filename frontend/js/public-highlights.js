/* ====================================================================
   public-highlights.js — the home page banner carousel
   --------------------------------------------------------------------
   Fills the .pub-slider section at the top of index.html from
   GET /api/public/highlights.

   Nothing here is hardcoded. There is no placeholder slide and no
   sample image: with no banners uploaded — or with the request failing
   — the section stays hidden and the page simply starts at the hero.

   Kept out of public-home.js deliberately. These banners are
   decoration; the statistics and the featured research are the
   substance. A picture failing to load must not be able to take them
   down with it.

   The images are cropped to fill the frame (object-fit: cover in
   public-theme.css), and each banner carries a focal point saying which
   part must survive the crop.
   ==================================================================== */

(function () {

  /* 6 seconds. Long enough to read a poster, and the reason the pause
     button in the markup is mandatory rather than a nicety: WCAG 2.2.2
     requires a pause mechanism for anything that moves on its own for
     more than five seconds. */
  const ADVANCE_MS = 6000;

  /* The nine focal points the API can send. A whitelist, not a
     transformation: the value becomes part of a class name, so it is
     checked against a list this file owns rather than trusted. Anything
     unrecognised falls back to centre. */
  const FOCAL_POINTS = [
    'TOP_LEFT', 'TOP_CENTER', 'TOP_RIGHT',
    'CENTER_LEFT', 'CENTER', 'CENTER_RIGHT',
    'BOTTOM_LEFT', 'BOTTOM_CENTER', 'BOTTOM_RIGHT'
  ];

  let slides = [];
  let current = 0;
  let timer = null;
  let userPaused = false;

  const reduceMotion = window.matchMedia
    ? window.matchMedia('(prefers-reduced-motion: reduce)')
    : null;

  /* ================================================================
     Helpers
     ================================================================ */

  function prefersReducedMotion() {
    return !!(reduceMotion && reduceMotion.matches);
  }

  /**
   * The CSS class for a focal point, e.g. TOP_CENTER -> is-focus-top-center.
   * Only names on the whitelist get through, so no server-supplied text can
   * invent a class.
   */
  function focalClass(focalPoint) {
    const name = FOCAL_POINTS.indexOf(focalPoint) === -1 ? 'CENTER' : focalPoint;
    return 'is-focus-' + name.toLowerCase().replace(/_/g, '-');
  }

  /**
   * A link is used only if it is plainly safe: an absolute http(s) URL, or a
   * path starting with a single slash. Everything else — javascript:, data:,
   * protocol-relative //host — is dropped and the slide renders without a link.
   *
   * The server enforces the same rule when the banner is saved. This is the
   * second of the two checks, and the cheaper one; neither is sufficient alone
   * because a link on the front page is worth checking twice.
   */
  function safeLink(rawUrl) {
    if (typeof rawUrl !== 'string') return null;
    const url = rawUrl.trim();
    if (!url) return null;

    const lower = url.toLowerCase();
    if (lower.indexOf('http://') === 0 || lower.indexOf('https://') === 0) return url;
    if (url.charAt(0) === '/' && url.charAt(1) !== '/') return url;

    return null;
  }

  /* ================================================================
     Rendering
     ================================================================ */

  function buildSlide(highlight, index) {
    const slide = document.createElement('div');
    slide.className = 'pub-slider-slide';
    slide.setAttribute('role', 'group');
    slide.setAttribute('aria-roledescription', 'slide');
    slide.setAttribute('aria-label', (index + 1) + ' of ' + slides.length);

    const img = document.createElement('img');
    img.className = focalClass(highlight.focalPoint);
    img.alt = highlight.altText || '';

    /* width/height from the API, so the browser knows each slide's shape
       before the bytes arrive. Combined with the viewport's aspect-ratio
       this is what keeps the hero below from jumping as posters load. */
    if (highlight.imageWidth)  img.width = highlight.imageWidth;
    if (highlight.imageHeight) img.height = highlight.imageHeight;

    /* imagePath is relative to the API base, not to the site. In production
       the two are the same origin; in local development the pages come from
       :5500 and the API from :8080, so the base has to be prefixed or every
       image 404s against the static server. */
    const src = PublicApi.urlFor(highlight.imagePath);

    if (index === 0) {
      /* The first slide is the largest thing above the fold, so it is
         fetched immediately and at high priority. */
      img.src = src;
      img.loading = 'eager';
      img.setAttribute('fetchpriority', 'high');
      img.addEventListener('load', hydrateRemainingSlides, { once: true });
      img.addEventListener('error', hydrateRemainingSlides, { once: true });
    } else {
      /* Held back until slide 1 has loaded, then hydrated.

         loading="lazy" would be the obvious choice and would not work: these
         slides sit inside a translated, overflow-hidden track, so the browser
         never sees them intersect the viewport and never fetches them. The
         first advance would show an empty frame. */
      img.dataset.src = src;
    }

    const link = safeLink(highlight.linkUrl);
    if (link) {
      const anchor = document.createElement('a');
      anchor.href = link;
      // External links open in a new tab; internal ones stay put.
      if (link.charAt(0) !== '/') {
        anchor.target = '_blank';
        anchor.rel = 'noopener noreferrer';
      }
      anchor.appendChild(img);
      slide.appendChild(anchor);
    } else {
      const media = document.createElement('div');
      media.className = 'pub-slider-media';
      media.appendChild(img);
      slide.appendChild(media);
    }

    if (highlight.caption) {
      const caption = document.createElement('p');
      caption.className = 'pub-slider-caption';
      /* textContent, not innerHTML + escapeHtml. The caption is plain text,
         and assigning text as text means there is no markup context for
         anything to escape from in the first place. */
      caption.textContent = highlight.caption;
      slide.appendChild(caption);
    }

    return slide;
  }

  function hydrateRemainingSlides() {
    const pending = document.querySelectorAll('#highlightTrack img[data-src]');
    for (let i = 0; i < pending.length; i++) {
      pending[i].src = pending[i].dataset.src;
      delete pending[i].dataset.src;
    }
  }

  function buildDots(dotsEl) {
    dotsEl.innerHTML = '';
    for (let i = 0; i < slides.length; i++) {
      const dot = document.createElement('button');
      dot.type = 'button';
      dot.className = 'pub-slider-dot';
      dot.setAttribute('role', 'tab');
      dot.setAttribute('aria-selected', i === 0 ? 'true' : 'false');
      dot.setAttribute('aria-label', 'Highlight ' + (i + 1));
      dot.addEventListener('click', (function (index) {
        return function () {
          goTo(index);
          /* Clicking a dot is a deliberate choice, so the timer restarts
             from full rather than yanking the slide away a moment later. */
          restartTimer();
        };
      })(i));
      dotsEl.appendChild(dot);
    }
  }

  /* ================================================================
     Movement
     ================================================================ */

  function goTo(index) {
    const track = document.getElementById('highlightTrack');
    if (!track || !slides.length) return;

    current = (index + slides.length) % slides.length;
    track.style.transform = 'translateX(-' + (current * 100) + '%)';

    /* Only the visible slide is exposed. Without this a screen reader reads
       all three posters in a row as though they were on the page together. */
    const slideEls = track.children;
    for (let i = 0; i < slideEls.length; i++) {
      slideEls[i].setAttribute('aria-hidden', i === current ? 'false' : 'true');
    }

    const dots = document.querySelectorAll('#highlightDots .pub-slider-dot');
    for (let i = 0; i < dots.length; i++) {
      dots[i].setAttribute('aria-selected', i === current ? 'true' : 'false');
    }
  }

  function next() { goTo(current + 1); }
  function prev() { goTo(current - 1); }

  /* ================================================================
     The timer

     Stopped whenever motion would be unwelcome or pointless: the user
     asked it to stop, the OS asks for reduced motion, the pointer or
     keyboard focus is inside the carousel, the tab is in the
     background, or there is only one slide to show.
     ================================================================ */

  function canAutoAdvance() {
    return slides.length > 1
        && !userPaused
        && !prefersReducedMotion()
        && !document.hidden;
  }

  function startTimer() {
    stopTimer();
    if (!canAutoAdvance()) return;
    timer = window.setInterval(next, ADVANCE_MS);
  }

  function stopTimer() {
    if (timer !== null) {
      window.clearInterval(timer);
      timer = null;
    }
  }

  function restartTimer() {
    if (canAutoAdvance()) startTimer();
  }

  /* ================================================================
     Wiring
     ================================================================ */

  function wireControls(section) {
    const prevBtn = document.getElementById('highlightPrev');
    const nextBtn = document.getElementById('highlightNext');
    const pauseBtn = document.getElementById('highlightPause');
    const controls = document.getElementById('highlightControls');

    /* One slide is not a carousel. No arrows, no dots, no pause button and
       no timer — a single banner animating to itself is just a flicker. */
    if (slides.length < 2) {
      return;
    }

    if (prevBtn) {
      prevBtn.hidden = false;
      prevBtn.addEventListener('click', function () { prev(); restartTimer(); });
    }
    if (nextBtn) {
      nextBtn.hidden = false;
      nextBtn.addEventListener('click', function () { next(); restartTimer(); });
    }
    if (controls) controls.hidden = false;

    if (pauseBtn) {
      pauseBtn.addEventListener('click', function () {
        userPaused = !userPaused;
        pauseBtn.setAttribute('aria-pressed', userPaused ? 'true' : 'false');
        pauseBtn.setAttribute('aria-label', userPaused
          ? 'Play the highlights slideshow'
          : 'Pause the highlights slideshow');
        const label = pauseBtn.querySelector('.pub-slider-pause-label');
        if (label) label.textContent = userPaused ? 'Play' : 'Pause';

        if (userPaused) stopTimer(); else startTimer();
      });
    }

    /* Hover and focus pause without touching userPaused, so leaving the
       carousel resumes only if the visitor had not pressed pause. */
    section.addEventListener('mouseenter', stopTimer);
    section.addEventListener('mouseleave', restartTimer);
    section.addEventListener('focusin', stopTimer);
    section.addEventListener('focusout', restartTimer);

    // A background tab should not be running a timer.
    document.addEventListener('visibilitychange', function () {
      if (document.hidden) stopTimer(); else restartTimer();
    });

    // Someone who turns reduced motion on mid-visit gets it honoured at once.
    if (reduceMotion && reduceMotion.addEventListener) {
      reduceMotion.addEventListener('change', function () {
        if (prefersReducedMotion()) stopTimer(); else restartTimer();
      });
    }

    section.addEventListener('keydown', function (event) {
      if (event.key === 'ArrowLeft')  { prev(); restartTimer(); }
      if (event.key === 'ArrowRight') { next(); restartTimer(); }
    });
  }

  /* ================================================================
     Load
     ================================================================ */

  async function load() {
    const section = document.getElementById('highlightSlider');
    const track = document.getElementById('highlightTrack');
    if (!section || !track) return;

    /* tryGet, not getOrFail. This block is decorative: a failure here must
       be silent and must leave the rest of the page alone. */
    const data = await PublicApi.tryGet('/public/highlights');

    if (!Array.isArray(data) || data.length === 0) {
      /* No banners uploaded yet, or the request failed. Either way the
         section stays hidden — no empty frame, no placeholder poster.
         Nothing is logged as an error: a portal with no banners is a
         perfectly normal portal. */
      return;
    }

    slides = data.filter(function (h) { return h && h.imagePath; });
    if (!slides.length) return;

    const fragment = document.createDocumentFragment();
    for (let i = 0; i < slides.length; i++) {
      fragment.appendChild(buildSlide(slides[i], i));
    }
    track.appendChild(fragment);

    buildDots(document.getElementById('highlightDots'));

    // Revealed only now, with real content already in it.
    section.hidden = false;

    goTo(0);
    wireControls(section);
    startTimer();
  }

  document.addEventListener('DOMContentLoaded', load);

})();
