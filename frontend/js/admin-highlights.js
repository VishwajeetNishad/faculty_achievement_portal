/**
 * Admin — Homepage Highlight Banners
 *
 * Connected to live Spring Boot endpoints:
 *   GET    /api/highlights              (every banner, live and retired)
 *   POST   /api/highlights              (upload — multipart: file + metadata)
 *   PUT    /api/highlights/{id}         (text, crop focus, live/hidden)
 *   POST   /api/highlights/{id}/image   (swap the picture, keep everything else)
 *   PUT    /api/highlights/order        (the whole running order, in one call)
 *   DELETE /api/highlights/{id}         (remove the row and its file)
 *
 * All six are gated by MANAGE_HIGHLIGHTS on the server (an administrator
 * implicitly holds it). Nothing in this file is a security check: the file-type
 * and size tests below exist only to give a fast, clear message before a 2 MB
 * upload leaves the browser. HighlightImageStorageServiceImpl re-inspects the
 * actual bytes and ignores both the filename and the Content-Type the browser
 * claims.
 *
 * No content is hardcoded. With no banners uploaded the table says so; it never
 * shows a sample poster.
 */

/* Mirrors app.highlight-storage.max-file-size in application.properties. If the
   server's limit changes, this becomes a lie that only makes the error message
   arrive later — never one that lets a larger file through. */
const HL_MAX_FILE_BYTES = 2097152;              // 2 MB
const HL_ALLOWED_TYPES = ['image/png', 'image/jpeg', 'image/webp'];

/* The banner frame is 1600px wide on a large screen. Anything narrower gets
   stretched to fill it and looks soft, so the admin is told at upload time
   rather than discovering it on the live homepage. */
const HL_RECOMMENDED_WIDTH = 1600;

const HL_FOCAL_POINTS = [
  'TOP_LEFT', 'TOP_CENTER', 'TOP_RIGHT',
  'CENTER_LEFT', 'CENTER', 'CENTER_RIGHT',
  'BOTTOM_LEFT', 'BOTTOM_CENTER', 'BOTTOM_RIGHT'
];

let allHighlightsData = [];

// Which banner the add/edit modal is about; null means "adding a new one".
let editingHighlightId = null;

// Which banner the replace-picture modal is about.
let replacingHighlight = null;

// Which banner the delete modal is about.
let deletingHighlight = null;

// The crop focus currently selected in the add/edit modal.
let selectedFocalPoint = 'CENTER';

// True while a reorder request is in flight, so a fast double-click on ▲ cannot
// send two orders built from the same starting list.
let reorderInFlight = false;

document.addEventListener('DOMContentLoaded', () => {
  if (!document.getElementById('highlightsTableBody')) return;
  initializeHighlightsPage();
});

async function initializeHighlightsPage() {
  if (typeof ensurePermissionsLoaded === 'function') {
    try { await ensurePermissionsLoaded(); } catch (e) { /* fall back to the cached list */ }
  }

  document.getElementById('addHighlightBtn').addEventListener('click', () => openHighlightModal(null));
  document.getElementById('hlSaveBtn').addEventListener('click', saveHighlight);
  document.getElementById('hlImageSaveBtn').addEventListener('click', saveReplacementImage);
  document.getElementById('hlDeleteConfirmBtn').addEventListener('click', confirmDeleteHighlight);

  document.getElementById('hlFile').addEventListener('change', event =>
    previewChosenFile(event.target, 'hlPreviewImg', 'hlPreviewEmpty', 'hlFile'));

  document.getElementById('hlNewFile').addEventListener('change', event =>
    previewChosenFile(event.target, 'hlNewPreviewImg', 'hlNewPreviewEmpty', 'hlNewFile'));

  // The 3 x 3 crop grid, one delegated listener.
  document.getElementById('hlFocalGrid').addEventListener('click', event => {
    const cell = event.target.closest('.hl-focal-cell');
    if (cell) applyFocalPoint(cell.getAttribute('data-value'));
  });

  // One delegated listener so the row buttons keep working after every re-render.
  document.getElementById('highlightsTableBody').addEventListener('click', event => {
    const button = event.target.closest('button[data-id]');
    if (!button) return;

    const highlight = findHighlight(button.getAttribute('data-id'));
    if (!highlight) return;

    if (button.classList.contains('js-hl-edit'))    { openHighlightModal(highlight); return; }
    if (button.classList.contains('js-hl-image'))   { openImageModal(highlight); return; }
    if (button.classList.contains('js-hl-delete'))  { openDeleteModal(highlight); return; }
    if (button.classList.contains('js-hl-toggle'))  { toggleHighlightActive(highlight); return; }
    if (button.classList.contains('js-hl-up'))      { moveHighlight(highlight, -1); return; }
    if (button.classList.contains('js-hl-down'))    { moveHighlight(highlight, 1); }
  });

  await loadHighlights();
}

function findHighlight(id) {
  return allHighlightsData.find(h => String(h.id) === String(id));
}

// ─────────────────────────────────────────────────────────────────────────────
// Load & render
// ─────────────────────────────────────────────────────────────────────────────

async function loadHighlights() {
  const tbody = document.getElementById('highlightsTableBody');
  tbody.innerHTML = `<tr><td colspan="6" class="empty-state"><div class="spinner"></div>
    <p style="margin-top:0.5rem;">Loading banners…</p></td></tr>`;

  const res = await ApiClient.get('/highlights');

  if (!res.success) {
    if (res.status === 403) {
      showHlNotice('danger', 'You cannot manage the homepage banners.',
        'This screen needs the MANAGE_HIGHLIGHTS permission, or an administrator role. Ask an administrator to grant it from the User Permissions page.');
      tbody.innerHTML = `<tr><td colspan="6" class="empty-state">
        <div class="empty-state-title">Access Denied</div>
        <p class="empty-state-text">Your account is not permitted to change what appears on the public homepage.</p></td></tr>`;
      document.getElementById('addHighlightBtn').style.display = 'none';
    } else {
      tbody.innerHTML = `<tr><td colspan="6" class="empty-state">
        <div class="empty-state-title">Could not load the banners</div>
        <p class="empty-state-text">${escapeHtml(res.message || 'The server did not return the banner list.')}</p></td></tr>`;
    }
    return;
  }

  // The API already returns them in display order; no client-side sort, so the
  // table and the carousel cannot disagree about the running order.
  allHighlightsData = res.data || [];

  renderHighlightsTable();
  updateHighlightCounts();
}

function updateHighlightCounts() {
  const live = allHighlightsData.filter(h => h.active === true).length;
  document.getElementById('hlLiveCount').textContent = live;
  document.getElementById('hlRetiredCount').textContent = allHighlightsData.length - live;
}

function renderHighlightsTable() {
  const tbody = document.getElementById('highlightsTableBody');
  tbody.innerHTML = '';

  if (allHighlightsData.length === 0) {
    tbody.innerHTML = `<tr><td colspan="6" class="empty-state">
      <div class="empty-state-title">No banners yet</div>
      <p class="empty-state-text">The homepage currently opens straight at the headline —
      no empty frame is shown to visitors. Add the first banner to start the slideshow.</p></td></tr>`;
    return;
  }

  const lastIndex = allHighlightsData.length - 1;

  allHighlightsData.forEach((highlight, index) => {
    const id = escapeHtml(String(highlight.id));
    const isLive = highlight.active === true;
    const tooSmall = Number(highlight.imageWidth || 0) < HL_RECOMMENDED_WIDTH;

    const tr = document.createElement('tr');
    tr.innerHTML = `
      <td data-label="Preview">
        <div class="hl-thumb-frame">
          ${isLive
            ? `<img src="${escapeHtml(imageSrcFor(highlight))}"
                    class="${focalClass(highlight.focalPoint)}"
                    alt="${escapeHtml(highlight.altText || '')}">`
            /* No <img> for a retired banner. Hiding one makes its picture stop
               being downloadable, which is the point — an <img> here would just
               render a broken-image icon and look like a bug. */
            : `<span class="hl-thumb-retired">Hidden —<br>no preview</span>`}
        </div>
      </td>

      <td data-label="Banner">
        <div class="table-title-cell">${escapeHtml(highlight.altText || '')}</div>
        ${highlight.caption
          ? `<div class="table-subtext">Caption: ${escapeHtml(highlight.caption)}</div>`
          : ''}
        ${highlight.linkUrl
          ? `<div class="table-subtext">Links to: ${escapeHtml(highlight.linkUrl)}</div>`
          : ''}
        <div style="margin-top:0.3rem;">
          <span class="hl-chip is-focus">Keeps ${escapeHtml(focalLabel(highlight.focalPoint))}</span>
        </div>
      </td>

      <td data-label="Image">
        <div class="hl-file-facts">
          ${Number(highlight.imageWidth || 0)} × ${Number(highlight.imageHeight || 0)}<br>
          ${escapeHtml(formatBytes(highlight.fileSizeBytes))} ·
          ${escapeHtml(shortType(highlight.contentType))}
        </div>
        ${tooSmall
          ? `<div style="margin-top:0.3rem;"><span class="hl-chip is-warn">Small — may look soft</span></div>`
          : ''}
      </td>

      <td data-label="Order">
        <div class="hl-order">
          <span class="hl-order-position">${index + 1}</span>
          <span class="hl-order-stack">
            <button type="button" class="hl-order-btn js-hl-up" data-id="${id}"
                    aria-label="Move up" title="Move up" ${index === 0 ? 'disabled' : ''}>▲</button>
            <button type="button" class="hl-order-btn js-hl-down" data-id="${id}"
                    aria-label="Move down" title="Move down" ${index === lastIndex ? 'disabled' : ''}>▼</button>
          </span>
        </div>
      </td>

      <td data-label="Status">
        <button type="button" class="hl-chip js-hl-toggle ${isLive ? 'is-live' : 'is-off'}"
                data-id="${id}" style="cursor:pointer;"
                title="${isLive ? 'Click to hide this banner' : 'Click to put this banner live'}">
          ${isLive ? 'Live' : 'Hidden'}
        </button>
      </td>

      <td data-label="Actions">
        <div class="action-btn-group">
          <button type="button" class="btn btn-outline btn-sm js-hl-edit" data-id="${id}">Edit</button>
          <button type="button" class="btn btn-outline btn-sm js-hl-image" data-id="${id}">Replace</button>
          <button type="button" class="btn btn-danger btn-sm js-hl-delete" data-id="${id}">Delete</button>
        </div>
      </td>
    `;
    tbody.appendChild(tr);
  });
}

// ─────────────────────────────────────────────────────────────────────────────
// Add / edit
// ─────────────────────────────────────────────────────────────────────────────

function openHighlightModal(highlight) {
  editingHighlightId = highlight ? highlight.id : null;

  document.getElementById('highlightModalNotice').innerHTML = '';
  ['hlFile', 'hlAltText', 'hlCaption', 'hlLinkUrl'].forEach(clearHlFieldError);

  document.getElementById('highlightModalTitle').textContent = highlight ? 'Edit Banner' : 'Add Banner';
  document.getElementById('hlSaveBtn').textContent = highlight ? 'Save Changes' : 'Add Banner';

  /* The picker only exists when adding. Changing the picture of an existing
     banner is the Replace button, so editing a caption cannot re-upload a
     poster by accident. */
  const fileGroup = document.getElementById('hlFileGroup');
  const fileInput = document.getElementById('hlFile');
  fileInput.value = '';
  fileGroup.hidden = !!highlight;

  document.getElementById('hlAltText').value = highlight ? (highlight.altText || '') : '';
  document.getElementById('hlCaption').value = highlight ? (highlight.caption || '') : '';
  document.getElementById('hlLinkUrl').value = highlight ? (highlight.linkUrl || '') : '';
  document.getElementById('hlActive').value =
    highlight ? String(highlight.active === true) : 'true';

  applyFocalPoint(highlight ? highlight.focalPoint : 'CENTER');

  /* In edit mode the preview shows the picture already on the server, so
     changing only the crop focus still shows the effect. A hidden banner's
     picture is deliberately unreachable, so it says that instead of breaking. */
  const previewImg = document.getElementById('hlPreviewImg');
  const previewEmpty = document.getElementById('hlPreviewEmpty');
  if (highlight && highlight.active === true) {
    previewImg.src = imageSrcFor(highlight);
    previewImg.hidden = false;
    previewEmpty.hidden = true;
  } else {
    previewImg.removeAttribute('src');
    previewImg.hidden = true;
    previewEmpty.hidden = false;
    previewEmpty.textContent = highlight
      ? 'This banner is hidden, so its picture is not downloadable. Set it live to preview the crop.'
      : 'Choose a picture to see exactly what the homepage banner will show.';
  }

  openModal('highlightModal');
  document.getElementById(highlight ? 'hlAltText' : 'hlFile').focus();
}

async function saveHighlight() {
  const altText = document.getElementById('hlAltText').value.trim();
  const caption = document.getElementById('hlCaption').value.trim();
  const linkUrl = document.getElementById('hlLinkUrl').value.trim();
  const active = document.getElementById('hlActive').value === 'true';
  const file = document.getElementById('hlFile').files[0] || null;

  ['hlFile', 'hlAltText', 'hlCaption', 'hlLinkUrl'].forEach(clearHlFieldError);
  document.getElementById('highlightModalNotice').innerHTML = '';

  /* Mirrors the HighlightMetadataRequest bean validation and the storage
     service's file rules, so an obvious mistake is caught before a round trip.
     The server validates all of it again regardless. */
  let valid = true;

  if (!altText) {
    setHlFieldError('hlAltText', 'Please describe the picture. A banner with no description is unreadable to a screen reader.');
    valid = false;
  }

  const linkProblem = linkProblemFor(linkUrl);
  if (linkProblem) { setHlFieldError('hlLinkUrl', linkProblem); valid = false; }

  if (!editingHighlightId) {
    const fileProblem = fileProblemFor(file);
    if (fileProblem) { setHlFieldError('hlFile', fileProblem); valid = false; }
  }

  if (!valid) return;

  const btn = document.getElementById('hlSaveBtn');
  const originalLabel = btn.textContent;
  btn.disabled = true;
  btn.textContent = 'Saving…';

  let res;
  if (editingHighlightId) {
    res = await ApiClient.put(`/highlights/${encodeURIComponent(editingHighlightId)}`, {
      altText: altText,
      caption: caption || null,
      linkUrl: linkUrl || null,
      focalPoint: selectedFocalPoint,
      active: active
    });
  } else {
    /* FormData keys must match the HighlightMetadataRequest property names —
       the controller binds them with @ModelAttribute, which is name-based. */
    const form = new FormData();
    form.append('file', file);
    form.append('altText', altText);
    if (caption) form.append('caption', caption);
    if (linkUrl) form.append('linkUrl', linkUrl);
    form.append('focalPoint', selectedFocalPoint);
    form.append('active', String(active));

    res = await ApiClient.upload('/highlights', form);
  }

  btn.disabled = false;
  btn.textContent = originalLabel;

  if (res.success) {
    closeModal('highlightModal');
    const wasCreating = !editingHighlightId;
    editingHighlightId = null;

    showToast(wasCreating ? 'Banner added.' : 'Banner saved.', 'success');

    /* The server reports the real dimensions it read out of the file, so this
       warning is based on the picture that was actually stored — not on
       anything the browser claimed about it. */
    const width = res.data && Number(res.data.imageWidth || 0);
    if (wasCreating && width && width < HL_RECOMMENDED_WIDTH) {
      showHlNotice('warning',
        `That picture is only ${width}px wide.`,
        `The banner is up to ${HL_RECOMMENDED_WIDTH}px wide on a large screen, so this one will be stretched and look soft. It is live either way — replace it with a larger version when you have one.`);
    }

    await loadHighlights();
    return;
  }

  if (res.status === 403) {
    showHlModalNotice('danger', 'You are not allowed to save this.',
      res.message || 'This needs the MANAGE_HIGHLIGHTS permission.');
    return;
  }

  if (res.status === 400 && /link/i.test(res.message || '')) {
    setHlFieldError('hlLinkUrl', res.message);
    return;
  }

  if (res.status === 400 && /file|image|picture|large|format/i.test(res.message || '')) {
    setHlFieldError('hlFile', res.message);
    return;
  }

  showHlModalNotice('danger', 'The banner could not be saved.',
    res.message || 'Please check the details and try again.');
}

// ─────────────────────────────────────────────────────────────────────────────
// Replace the picture
// ─────────────────────────────────────────────────────────────────────────────

function openImageModal(highlight) {
  replacingHighlight = highlight;

  document.getElementById('hlImageNotice').innerHTML = '';
  clearHlFieldError('hlNewFile');

  document.getElementById('hlImageTarget').innerHTML =
    `Replacing the picture for <strong>${escapeHtml(highlight.altText || '')}</strong>.`;

  const input = document.getElementById('hlNewFile');
  input.value = '';

  const previewImg = document.getElementById('hlNewPreviewImg');
  previewImg.removeAttribute('src');
  previewImg.hidden = true;
  previewImg.className = focalClass(highlight.focalPoint);
  document.getElementById('hlNewPreviewEmpty').hidden = false;

  openModal('highlightImageModal');
  input.focus();
}

async function saveReplacementImage() {
  if (!replacingHighlight) return;

  const file = document.getElementById('hlNewFile').files[0] || null;
  clearHlFieldError('hlNewFile');
  document.getElementById('hlImageNotice').innerHTML = '';

  const fileProblem = fileProblemFor(file);
  if (fileProblem) { setHlFieldError('hlNewFile', fileProblem); return; }

  const btn = document.getElementById('hlImageSaveBtn');
  const originalLabel = btn.textContent;
  btn.disabled = true;
  btn.textContent = 'Uploading…';

  const form = new FormData();
  form.append('file', file);

  const res = await ApiClient.upload(
    `/highlights/${encodeURIComponent(replacingHighlight.id)}/image`, form);

  btn.disabled = false;
  btn.textContent = originalLabel;

  if (res.success) {
    closeModal('highlightImageModal');
    replacingHighlight = null;
    showToast('Picture replaced. The old file has been deleted from the server.', 'success');

    const width = res.data && Number(res.data.imageWidth || 0);
    if (width && width < HL_RECOMMENDED_WIDTH) {
      showHlNotice('warning',
        `That picture is only ${width}px wide.`,
        `It will be stretched to fill the banner and look soft on a large screen.`);
    }

    await loadHighlights();
    return;
  }

  if (res.status === 403) {
    showHlImageNotice('danger', 'You are not allowed to change this.',
      res.message || 'This needs the MANAGE_HIGHLIGHTS permission.');
    return;
  }

  setHlFieldError('hlNewFile', res.message || 'The picture could not be uploaded.');
}

// ─────────────────────────────────────────────────────────────────────────────
// Live / hidden
// ─────────────────────────────────────────────────────────────────────────────

async function toggleHighlightActive(highlight) {
  const goingLive = highlight.active !== true;

  /* The whole metadata object is sent, not just `active`. PUT /highlights/{id}
     replaces the text fields with whatever it receives, so omitting the caption
     here would quietly erase it. */
  const res = await ApiClient.put(`/highlights/${encodeURIComponent(highlight.id)}`, {
    altText: highlight.altText,
    caption: highlight.caption || null,
    linkUrl: highlight.linkUrl || null,
    focalPoint: highlight.focalPoint,
    active: goingLive
  });

  if (res.success) {
    showToast(goingLive
      ? 'Banner is now live on the homepage.'
      : 'Banner hidden. It stays here and its picture is no longer downloadable.', 'success');
    await loadHighlights();
    return;
  }

  showToast(res.message || 'The banner could not be updated.', 'error');
}

// ─────────────────────────────────────────────────────────────────────────────
// Reorder
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Moves a banner one place up (-1) or down (+1).
 *
 * <p>Sends the complete running order rather than a "move this one up"
 * instruction. Two quick clicks, or two administrators reordering at once, can
 * interleave and settle on an order nobody chose; one atomic call cannot. The
 * server refuses the call unless the ids submitted are exactly the ids it
 * stores, so a stale page reloads instead of stranding a row.
 */
async function moveHighlight(highlight, direction) {
  if (reorderInFlight) return;

  const from = allHighlightsData.findIndex(h => String(h.id) === String(highlight.id));
  const to = from + direction;
  if (from === -1 || to < 0 || to >= allHighlightsData.length) return;

  const orderedIds = allHighlightsData.map(h => h.id);
  const moved = orderedIds.splice(from, 1)[0];
  orderedIds.splice(to, 0, moved);

  reorderInFlight = true;
  setReorderButtonsDisabled(true);

  const res = await ApiClient.put('/highlights/order', { orderedIds: orderedIds });

  reorderInFlight = false;

  if (res.success) {
    allHighlightsData = res.data || [];
    renderHighlightsTable();
    updateHighlightCounts();
    return;
  }

  setReorderButtonsDisabled(false);

  if (res.status === 400) {
    // The page was out of date — somebody added or removed a banner elsewhere.
    showToast(res.message || 'The order was out of date. Reloading the list.', 'error');
    await loadHighlights();
    return;
  }

  showToast(res.message || 'The order could not be saved.', 'error');
}

function setReorderButtonsDisabled(disabled) {
  document.querySelectorAll('#highlightsTableBody .hl-order-btn')
    .forEach(btn => { btn.disabled = disabled; });
}

// ─────────────────────────────────────────────────────────────────────────────
// Delete
// ─────────────────────────────────────────────────────────────────────────────

function openDeleteModal(highlight) {
  deletingHighlight = highlight;

  document.getElementById('hlDeleteMessage').innerHTML =
    `Delete <strong>${escapeHtml(highlight.altText || '')}</strong>? The picture file is removed
     from the server as well, so this cannot be undone.
     <br><br>To take it off the homepage but keep it, use <strong>Hidden</strong> instead.`;

  openModal('highlightDeleteModal');
}

async function confirmDeleteHighlight() {
  if (!deletingHighlight) return;

  const btn = document.getElementById('hlDeleteConfirmBtn');
  const originalLabel = btn.textContent;
  btn.disabled = true;
  btn.textContent = 'Deleting…';

  const res = await ApiClient.delete(`/highlights/${encodeURIComponent(deletingHighlight.id)}`);

  btn.disabled = false;
  btn.textContent = originalLabel;

  if (res.success) {
    closeModal('highlightDeleteModal');
    showToast('Banner deleted.', 'success');
    deletingHighlight = null;
    await loadHighlights();
    return;
  }

  showToast(res.message || 'The banner could not be deleted.', 'error');
}

// ─────────────────────────────────────────────────────────────────────────────
// The crop editor
// ─────────────────────────────────────────────────────────────────────────────

function applyFocalPoint(value) {
  selectedFocalPoint = HL_FOCAL_POINTS.indexOf(value) === -1 ? 'CENTER' : value;

  document.querySelectorAll('#hlFocalGrid .hl-focal-cell').forEach(cell => {
    cell.setAttribute('aria-checked',
      cell.getAttribute('data-value') === selectedFocalPoint ? 'true' : 'false');
  });

  // Applied as a class, never as an inline object-position value.
  document.getElementById('hlPreviewImg').className = focalClass(selectedFocalPoint);
}

/**
 * Shows the chosen file in a crop preview.
 *
 * <p>Read as a data: URL rather than URL.createObjectURL(). The site's Content
 * Security Policy is `img-src 'self' data:` — a blob: URL is neither, so an
 * object URL would render nothing in production while working perfectly in
 * local development. See Caddyfile.
 */
function previewChosenFile(input, imgId, emptyId, fieldId) {
  const img = document.getElementById(imgId);
  const empty = document.getElementById(emptyId);
  const file = input.files[0];

  clearHlFieldError(fieldId);

  if (!file) {
    img.removeAttribute('src');
    img.hidden = true;
    empty.hidden = false;
    return;
  }

  const problem = fileProblemFor(file);
  if (problem) {
    setHlFieldError(fieldId, problem);
    img.removeAttribute('src');
    img.hidden = true;
    empty.hidden = false;
    return;
  }

  const reader = new FileReader();
  reader.onload = () => {
    img.src = reader.result;
    img.hidden = false;
    empty.hidden = true;
  };
  reader.onerror = () => {
    setHlFieldError(fieldId, 'That file could not be read. Please choose it again.');
  };
  reader.readAsDataURL(file);
}

// ─────────────────────────────────────────────────────────────────────────────
// Small helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Turns the enum name into the stylesheet class, e.g. TOP_CENTER -> hl-focus-top-center. */
function focalClass(focalPoint) {
  const name = HL_FOCAL_POINTS.indexOf(focalPoint) === -1 ? 'CENTER' : focalPoint;
  return 'hl-focus-' + name.toLowerCase().replace(/_/g, '-');
}

/** TOP_CENTER -> "the top". Plain words for the chip in the table. */
function focalLabel(focalPoint) {
  const name = HL_FOCAL_POINTS.indexOf(focalPoint) === -1 ? 'CENTER' : focalPoint;
  return 'the ' + name.toLowerCase().replace(/_/g, ' ').replace('center', 'centre');
}

/**
 * imagePath from the API is relative to the API base, not to this page. In
 * production both are the same origin; in local development the pages come from
 * :5500 and the API from :8080, so the base has to be prefixed.
 */
function imageSrcFor(highlight) {
  return CONFIG.API_BASE_URL + (highlight.imagePath || '');
}

/** Returns a message when the file is obviously wrong, or null when it looks fine. */
function fileProblemFor(file) {
  if (!file) return 'Please choose a picture.';

  if (HL_ALLOWED_TYPES.indexOf(file.type) === -1) {
    return 'Please choose a PNG, JPEG or WebP picture.';
  }

  if (file.size > HL_MAX_FILE_BYTES) {
    return `That file is ${formatBytes(file.size)}. The limit is 2 MB — please save it smaller or use a JPEG.`;
  }

  if (file.size === 0) return 'That file is empty.';

  return null;
}

/**
 * Returns a message when the link is unusable, or null when it is fine.
 * The same rule the server applies in HighlightServiceImpl.normaliseLinkUrl —
 * checked here too, because a link on the front page is worth checking twice.
 */
function linkProblemFor(url) {
  if (!url) return null;

  const lower = url.toLowerCase();
  if (lower.indexOf('http://') === 0 || lower.indexOf('https://') === 0) return null;
  if (url.charAt(0) === '/' && url.charAt(1) !== '/') return null;

  return 'The link must start with https://, http:// or a single / for a page on this site.';
}

function formatBytes(bytes) {
  const size = Number(bytes || 0);
  if (size <= 0) return '0 KB';
  if (size < 1024) return size + ' B';
  if (size < 1024 * 1024) return Math.round(size / 1024) + ' KB';
  return (size / (1024 * 1024)).toFixed(1) + ' MB';
}

/** "image/jpeg" -> "JPEG". The format the server detected, not what was claimed. */
function shortType(contentType) {
  const type = String(contentType || '').split('/')[1] || '';
  return type ? type.toUpperCase() : 'Unknown';
}

function setHlFieldError(fieldId, message) {
  const holder = document.getElementById('err-' + fieldId);
  const input = document.getElementById(fieldId);
  if (holder) { holder.textContent = message; holder.style.display = 'block'; }
  if (input) input.classList.add('is-invalid');
}

function clearHlFieldError(fieldId) {
  const holder = document.getElementById('err-' + fieldId);
  const input = document.getElementById(fieldId);
  if (holder) { holder.textContent = ''; holder.style.display = 'none'; }
  if (input) input.classList.remove('is-invalid');
}

function showHlNotice(type, title, body) {
  const holder = document.getElementById('highlightNotice');
  if (!holder) return;
  holder.innerHTML = `<div class="alert alert-${type}" style="margin-bottom: 1.25rem;">
    <div><strong>${escapeHtml(title)}</strong> ${escapeHtml(body || '')}</div></div>`;
}

function showHlModalNotice(type, title, body) {
  const holder = document.getElementById('highlightModalNotice');
  if (!holder) return;
  holder.innerHTML = `<div class="alert alert-${type}" style="margin-bottom: 1rem;">
    <div><strong>${escapeHtml(title)}</strong> ${escapeHtml(body || '')}</div></div>`;
}

function showHlImageNotice(type, title, body) {
  const holder = document.getElementById('hlImageNotice');
  if (!holder) return;
  holder.innerHTML = `<div class="alert alert-${type}" style="margin-bottom: 1rem;">
    <div><strong>${escapeHtml(title)}</strong> ${escapeHtml(body || '')}</div></div>`;
}
