/* ====================================================================
   public-api.js — HTTP helper for the public (no-login) pages
   --------------------------------------------------------------------
   Why this exists instead of reusing js/api.js:

   ApiClient attaches the JWT from sessionStorage and, on ANY 401,
   wipes the session and redirects to login.html. That is exactly right
   for the signed-in portal and exactly wrong here — a student browsing
   the faculty directory has no token, so ApiClient would bounce them
   to a login screen they have no business seeing.

   So this helper:
     · sends NO Authorization header
     · never redirects
     · reports failure to the caller instead of handling it itself

   There are three functions because the pages need three different
   things from a failed request:

     tryGet   — resolves to null on any failure. For data the page can
                do without, like the department dropdown.
     getOrFail— throws on any failure. For the data the page exists to
                show; the caller's catch block draws the error state.
     getRaw   — hands back the status code and body untouched, for when
                the failure IS the message (a share link that expired is
                a different story from one that never existed).
   ==================================================================== */

const PublicApi = (function () {

  /* CONFIG comes from js/config.js, which holds no auth logic. */
  const BASE = (typeof CONFIG !== 'undefined' && CONFIG.API_BASE_URL)
    ? CONFIG.API_BASE_URL
    : 'http://localhost:8080/api';

  /**
   * Turn { keyword: 'ai', page: 0, departmentCode: '' } into
   * "?keyword=ai&page=0" — blank and null values are dropped so the
   * backend never receives an empty filter it has to special-case.
   */
  function toQueryString(params) {
    if (!params) return '';
    const usp = new URLSearchParams();
    Object.keys(params).forEach(function (key) {
      const value = params[key];
      if (value !== null && value !== undefined && String(value).trim() !== '') {
        usp.append(key, value);
      }
    });
    const qs = usp.toString();
    return qs ? '?' + qs : '';
  }

  /**
   * GET a public endpoint, tolerating failure.
   *
   * Resolves to the parsed JSON body on success, or null on ANY failure —
   * network down, a non-2xx status, or a body that is not JSON.
   *
   * Use this only for data the page can manage without. An empty array is
   * a success and comes back as `[]`, so null always means "the request
   * did not work", never "there were no records".
   *
   * @param {string} path   e.g. '/public/departments'
   * @param {Object} params optional query parameters
   * @returns {Promise<Object|Array|null>}
   */
  async function tryGet(path, params) {
    const url = BASE + path + toQueryString(params);

    try {
      const response = await fetch(url, {
        method: 'GET',
        headers: { 'Accept': 'application/json' }
      });

      if (!response.ok) {
        console.warn('[public-api] ' + url + ' returned ' + response.status);
        return null;
      }

      return await response.json();

    } catch (error) {
      console.warn('[public-api] ' + url + ' is unreachable (' + error.message + ')');
      return null;
    }
  }

  /**
   * GET a public endpoint, treating failure as fatal.
   *
   * Resolves to the parsed JSON body, or throws. Use this for the data a
   * page exists to show: if the faculty directory cannot be fetched there
   * is no directory to draw, and the honest thing is to say so rather than
   * to show an empty grid that looks like "nobody works here".
   *
   * The thrown message is for the developer console. What the visitor
   * reads is the error state the calling page draws in its catch block.
   *
   * @param {string} path   e.g. '/public/faculty'
   * @param {Object} params optional query parameters
   * @returns {Promise<Object|Array>}
   */
  async function getOrFail(path, params) {
    const url = BASE + path + toQueryString(params);

    let response;
    try {
      response = await fetch(url, {
        method: 'GET',
        headers: { 'Accept': 'application/json' }
      });
    } catch (error) {
      throw new Error('GET ' + url + ' could not be reached: ' + error.message);
    }

    if (!response.ok) {
      throw new Error('GET ' + url + ' returned ' + response.status);
    }

    try {
      return await response.json();
    } catch (error) {
      throw new Error('GET ' + url + ' did not return JSON');
    }
  }

  /**
   * GET a public endpoint and report the outcome in full.
   *
   * `tryGet` collapses every failure to null, which suits a listing page: it
   * either has records to draw or it does not. The share page cannot use that,
   * because for a share link the failure IS the message. A visitor needs to be
   * told three different things:
   *
   *   404  — no such link. Probably a typo, or the address was truncated.
   *   410 EXPIRED  — the link was real and its time ran out.
   *   410 REVOKED  — the owner deliberately withdrew it.
   *
   * So this returns the status and the parsed body, and lets the caller decide.
   * `status: 0` means the request never reached the server at all.
   *
   * @param {string} path   e.g. '/public/share/abc123'
   * @param {Object} params optional query parameters
   * @returns {Promise<{ok: boolean, status: number, body: *}>}
   */
  async function getRaw(path, params) {
    const url = BASE + path + toQueryString(params);

    try {
      const response = await fetch(url, {
        method: 'GET',
        headers: { 'Accept': 'application/json' }
      });

      let body = null;
      try {
        body = await response.json();
      } catch (e) {
        body = null; // an error page with no JSON body is still a valid outcome
      }

      return { ok: response.ok, status: response.status, body: body };

    } catch (error) {
      return { ok: false, status: 0, body: null };
    }
  }

  /** Absolute URL for a public endpoint — for an <a href> or an <iframe src>. */
  function urlFor(path) {
    return BASE + path;
  }

  return {
    tryGet: tryGet,
    getOrFail: getOrFail,
    getRaw: getRaw,
    urlFor: urlFor,
    toQueryString: toQueryString
  };

})();
