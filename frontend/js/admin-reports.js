/**
 * Admin — Accreditation Report (NAAC / NBA)
 *
 * Connected to live Spring Boot endpoints:
 *   GET /api/reports/naac                  (the report; ROLE_ADMIN or VIEW_REPORTS)
 *   GET /api/reports/naac/export/csv       (the same report as CSV; also needs EXPORT_REPORTS)
 *
 * The permission checks in this file only decide what to OFFER. The backend
 * re-reads both permissions from the database on every request, so hiding a
 * button here is a courtesy, not a control.
 *
 * Everything on the page comes from the API response — the year and department
 * filters included. Nothing is hardcoded: the submission form's list of academic
 * years would go stale, and a typed-in department list would drift from the
 * database the first time somebody adds one.
 *
 * escapeHtml / showToast / can / ensurePermissionsLoaded come from common.js —
 * do NOT redefine them.
 */

// The first, unfiltered response. Kept so the filter dropdowns keep offering
// every year and department even after the user narrows the report — otherwise
// filtering to one year would collapse the dropdown to that one year and there
// would be no way back.
let repFilterSource = null;

document.addEventListener('DOMContentLoaded', () => {
  if (!document.getElementById('repDocument')) return;
  repInit();
});

async function repInit() {
  if (typeof ensurePermissionsLoaded === 'function') {
    try { await ensurePermissionsLoaded(); } catch (e) { /* fall back to the cached list */ }
  }

  // UX gate. The backend enforces VIEW_REPORTS on the endpoint regardless.
  if (typeof can === 'function' && !can('VIEW_REPORTS')) {
    repDenied();
    return;
  }

  document.getElementById('repPrintBtn').addEventListener('click', () => window.print());
  document.getElementById('repApplyBtn').addEventListener('click', () => repLoad());
  document.getElementById('repResetBtn').addEventListener('click', repReset);

  // Export is a separate permission, and the endpoint requires BOTH. An account
  // with only EXPORT_REPORTS never reaches this line, because VIEW_REPORTS gates
  // the page above — which is the same order the backend checks them in.
  if (typeof can === 'function' && can('EXPORT_REPORTS')) {
    const btn = document.getElementById('repCsvBtn');
    btn.style.display = '';
    btn.addEventListener('click', repExportCsv);
  }

  await repLoad();
}

function repQueryString() {
  const params = new URLSearchParams();
  const from = document.getElementById('repFromYear').value;
  const to = document.getElementById('repToYear').value;
  const dept = document.getElementById('repDepartment').value;
  if (from) params.set('fromYear', from);
  if (to) params.set('toYear', to);
  if (dept) params.set('departmentId', dept);
  const qs = params.toString();
  return qs ? `?${qs}` : '';
}

async function repLoad() {
  const doc = document.getElementById('repDocument');
  doc.innerHTML = `<div class="card"><div class="rep-empty">Building the report…</div></div>`;

  const res = await ApiClient.get(`/reports/naac${repQueryString()}`);

  if (!res.success) {
    if (res.status === 403) {
      repDenied();
      return;
    }
    doc.innerHTML = '';
    repNotice('alert-danger', res.message || 'Could not build the report. Please try again.');
    return;
  }

  repNotice(null);

  // Populate the filters from the first response only.
  if (!repFilterSource) {
    repFilterSource = res.data;
    repPopulateFilters(res.data);
  }

  repRender(res.data);
}

function repReset() {
  document.getElementById('repFromYear').value = '';
  document.getElementById('repToYear').value = '';
  document.getElementById('repDepartment').value = '';
  repLoad();
}

/**
 * Fills both year selects and the department select from live data.
 *
 * Years come from `academicYears`, which the backend derives from the stored
 * records rather than from a fixed list — so a value the submission form does not
 * offer still shows up here instead of being invisible. Departments come from
 * `summary`, which carries a row for every department including the ones with no
 * research on record yet.
 */
function repPopulateFilters(report) {
  const years = Array.isArray(report.academicYears) ? report.academicYears : [];
  ['repFromYear', 'repToYear'].forEach(id => {
    const select = document.getElementById(id);
    const first = select.options[0];
    select.innerHTML = '';
    select.appendChild(first);
    years.forEach(y => {
      const option = document.createElement('option');
      option.value = y;
      option.textContent = y;
      select.appendChild(option);
    });
  });

  const deptSelect = document.getElementById('repDepartment');
  const firstDept = deptSelect.options[0];
  deptSelect.innerHTML = '';
  deptSelect.appendChild(firstDept);
  (Array.isArray(report.summary) ? report.summary : []).forEach(row => {
    const option = document.createElement('option');
    option.value = row.departmentId;
    option.textContent = `${row.departmentCode} — ${row.departmentName}`;
    deptSelect.appendChild(option);
  });
}

// ---------------------------------------------------------------------------
// Rendering
// ---------------------------------------------------------------------------

function repRender(report) {
  const years = Array.isArray(report.academicYears) ? report.academicYears : [];
  const coverage = report.coverage || {};
  const sections = Array.isArray(report.sections) ? report.sections : [];

  const parts = [];
  parts.push(repDocHeader(report));

  if (coverage.rowCapReached) {
    // Said out loud, and printed. A silently truncated accreditation report is
    // worse than no report, because nobody knows to go looking for the rest.
    parts.push(`<div class="alert alert-warning rep-truncated"><div>
      <strong>This report is incomplete.</strong> More than ${repNum(coverage.rowCap)} verified
      records match the current scope, so the detail tables below stop at that limit. The counts and
      totals cover only the rows shown. Narrow the year range or pick a single department, and export
      in parts.
    </div></div>`);
  }

  parts.push(repCoverageCard(coverage));

  if (Number(coverage.approvedIncluded || 0) === 0) {
    parts.push(`<div class="card rep-section"><div class="rep-empty">
      No verified achievements match this scope, so there is nothing to report yet. Achievements appear
      here once a Head of Department or an administrator has approved them.
    </div></div>`);
  } else {
    parts.push(repMatrixCard('Summary — verified records by department and academic year', years,
      Array.isArray(report.summary) ? report.summary : []));
    sections.forEach(section => parts.push(repSectionCard(section, years)));
  }

  parts.push(`<p class="rep-footnote">${escapeHtml(report.metricRefNote || '')}</p>`);

  document.getElementById('repDocument').innerHTML = parts.join('');
}

function repDocHeader(report) {
  const scope = report.departmentFilterName
    ? escapeHtml(report.departmentFilterName)
    : 'All departments';

  let yearScope = 'All years on record';
  if (report.fromYear && report.toYear) {
    yearScope = `${escapeHtml(report.fromYear)} to ${escapeHtml(report.toYear)}`;
  } else if (report.fromYear) {
    yearScope = `From ${escapeHtml(report.fromYear)}`;
  } else if (report.toYear) {
    yearScope = `Up to ${escapeHtml(report.toYear)}`;
  }

  return `<div class="rep-doc-header">
    <h2 class="rep-doc-title">${escapeHtml(report.reportTitle || '')}</h2>
    <div class="rep-doc-meta">
      <span>Scope: <strong>${scope}</strong></span>
      <span>Academic years: <strong>${yearScope}</strong></span>
      <span>Generated: <strong>${escapeHtml(report.generatedAt || '')}</strong></span>
      <span>Prepared by: <strong>${escapeHtml(report.generatedByName || '')}</strong></span>
    </div>
  </div>`;
}

/**
 * The honesty block. Printed, not hidden behind a tooltip: a submitted document
 * has to be able to state what it counted and what it left out.
 */
function repCoverageCard(c) {
  const rows = [
    ['Verified records included', c.approvedIncluded],
    ['Awaiting departmental verification (not counted)', c.pendingExcluded],
    ['Rejected on review (not counted)', c.rejectedExcluded]
  ];
  if (Number(c.unclassifiedExcluded || 0) > 0) {
    rows.push(['Category has no section in this report (not counted)', c.unclassifiedExcluded]);
  }

  return `<div class="card rep-section">
    <div class="card-header"><h2 class="card-title">What this report counts</h2></div>
    <div class="rep-stat-strip">
      ${rows.map(([label, value]) =>
        `<span>${escapeHtml(label)}: <strong>${repNum(value)}</strong></span>`).join('')}
    </div>
    <div class="rep-empty">
      Only achievements a Head of Department or an administrator has approved are counted. Anything
      still pending is listed above so the totals can be read for what they are — the institution's
      verified output, not everything submitted.
    </div>
  </div>`;
}

function repMatrixCard(title, years, rows) {
  if (!rows.length) return '';

  const head = ['Department', ...years, 'Total']
    .map((h, i) => `<th${i === 0 ? '' : ' class="rep-num"'}>${escapeHtml(String(h))}</th>`).join('');

  const body = rows.map(row => {
    const cells = years.map(y =>
      `<td class="rep-num" data-label="${escapeHtml(y)}">${repNum((row.countsByYear || {})[y])}</td>`).join('');
    return `<tr>
      <td data-label="Department"><strong>${escapeHtml(row.departmentCode || '')}</strong> — ${escapeHtml(row.departmentName || '')}</td>
      ${cells}
      <td class="rep-num" data-label="Total"><strong>${repNum(row.total)}</strong></td>
    </tr>`;
  }).join('');

  return `<div class="card rep-section">
    <div class="card-header"><h2 class="card-title">${escapeHtml(title)}</h2></div>
    <div class="card-body" style="padding: 0;">
      <div class="table-responsive">
        <table class="data-table">
          <thead><tr>${head}</tr></thead>
          <tbody>${body}</tbody>
        </table>
      </div>
    </div>
  </div>`;
}

/**
 * One card per section, driven entirely by `columns` and `rows`.
 *
 * Six sections have six different column sets, and the server sends both the
 * headings and the already-formatted cells. So this one function renders all six
 * — and the printed page shows exactly the strings the CSV contains, which is
 * what stops the two outputs from ever disagreeing about a date or an amount.
 */
function repSectionCard(section, years) {
  const columns = Array.isArray(section.columns) ? section.columns : [];
  const rows = Array.isArray(section.rows) ? section.rows : [];

  const metricRef = section.metricRef
    ? ` <span class="rep-metric-ref">· Metric ${escapeHtml(section.metricRef)}</span>`
    : '';

  let head = `<div class="card-header">
    <div>
      <h2 class="card-title">${escapeHtml(section.title || '')}${metricRef}</h2>
      <p class="rep-section-subtitle">${escapeHtml(section.subtitle || '')}</p>
    </div>
  </div>`;

  if (!rows.length) {
    return `<div class="card rep-section">${head}
      <div class="rep-empty">No verified records in this section for the selected scope.</div>
    </div>`;
  }

  const strip = [`<span>Records: <strong>${repNum(section.total)}</strong></span>`];
  if (section.totalAmount !== null && section.totalAmount !== undefined) {
    strip.push(`<span>Total sanctioned (INR): <strong>${repNum(section.totalAmount)}</strong></span>`);
  }

  // Every chip is a real stored enum value — indexing, patent status, award
  // level and so on. Nothing here is inferred from the text of a record.
  const chips = Object.keys(section.breakdown || {}).map(key =>
    `<span class="rep-chip">${escapeHtml(key)} <b>${repNum(section.breakdown[key])}</b></span>`).join('');

  const tableHead = columns
    .map(c => `<th>${escapeHtml(String(c))}</th>`).join('');

  const tableBody = rows.map(row => `<tr>${
    row.map((cell, i) =>
      `<td data-label="${escapeHtml(String(columns[i] || ''))}">${escapeHtml(String(cell ?? ''))}</td>`).join('')
  }</tr>`).join('');

  // A section with no meaningful split sends an empty breakdown. Rendering the
  // strip anyway would leave a dangling "By " chip with nothing after it.
  const breakdownStrip = chips
    ? `<div class="rep-breakdown">
      <span class="rep-chip" style="border-style: dashed;">By ${escapeHtml(String(section.breakdownLabel || '').toLowerCase())}</span>
      ${chips}
    </div>`
    : '';

  return `<div class="card rep-section">${head}
    <div class="rep-stat-strip">${strip.join('')}</div>
    ${breakdownStrip}
    <div class="card-body" style="padding: 0;">
      <div class="table-responsive">
        <table class="data-table">
          <thead><tr>${tableHead}</tr></thead>
          <tbody>${tableBody}</tbody>
        </table>
      </div>
    </div>
    ${repMatrixCard(`${section.title} — by department and academic year`, years,
        Array.isArray(section.countsByDepartmentYear) ? section.countsByDepartmentYear : [])
      .replace('<div class="card rep-section">', '<div class="rep-section" style="padding: 0 1.5rem 1.25rem;">')}
  </div>`;
}

async function repExportCsv() {
  const btn = document.getElementById('repCsvBtn');
  const original = btn.innerHTML;
  btn.disabled = true;
  btn.textContent = 'Preparing…';

  const res = await ApiClient.downloadBlob(`/reports/naac/export/csv${repQueryString()}`);

  btn.disabled = false;
  btn.innerHTML = original;

  if (res.success && res.objectUrl) {
    const a = document.createElement('a');
    a.href = res.objectUrl;
    a.download = 'naac-research-report.csv';
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(res.objectUrl);
    showToast('Report downloaded. The download is recorded in the audit trail.', 'success');
  } else {
    showToast(res.message || 'Export failed. Please try again.', 'error');
  }
}

// ---------------------------------------------------------------------------
// States and helpers
// ---------------------------------------------------------------------------

function repDenied() {
  document.getElementById('repDocument').innerHTML = '';
  const filterCard = document.getElementById('repFilterCard');
  if (filterCard) filterCard.style.display = 'none';
  document.getElementById('repPrintBtn').style.display = 'none';
  document.getElementById('repCsvBtn').style.display = 'none';

  repNotice('alert-warning', 'Permission required. Building the institution-wide accreditation '
    + 'report needs the VIEW_REPORTS permission, and downloading it also needs EXPORT_REPORTS. '
    + 'Ask an administrator to grant them.');
}

function repNotice(variant, message) {
  const el = document.getElementById('repNotice');
  if (!el) return;
  if (!variant) {
    el.innerHTML = '';
    return;
  }
  el.innerHTML = `<div class="alert ${variant}" style="margin-bottom: 1.25rem;">
    <div>${escapeHtml(message)}</div>
  </div>`;
}

/** Blank stays blank; a real 0 prints as 0. An empty cell would read as missing. */
function repNum(value) {
  if (value === null || value === undefined || value === '') return '0';
  return escapeHtml(String(value));
}
