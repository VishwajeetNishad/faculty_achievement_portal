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
     · returns null instead of throwing when the endpoint is missing

   That last point matters. The public read APIs (GET /api/public/...)
   are Track B work and do not exist in the backend yet, so every call
   here currently comes back unauthorised. Each page therefore asks for
   real data first and only falls back to the sample content in
   public-sample-data.js when the request fails. The day the backend
   endpoints land, these pages start showing live data with no change
   to a single line of page code.
   ==================================================================== */

const PublicApi = (function () {

  /* CONFIG comes from js/config.js, which holds no auth logic. */
  const BASE = (typeof CONFIG !== 'undefined' && CONFIG.API_BASE_URL)
    ? CONFIG.API_BASE_URL
    : 'http://localhost:8080/api';

  /* Set to true the first time a public endpoint answers properly.
     The pages read it to decide whether to show the "sample content"
     banner. */
  let liveBackendConfirmed = false;

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
   * GET a public endpoint.
   *
   * Resolves to the parsed JSON body on success, or null on ANY
   * failure — network down, 401/403 because /api/public/** is not
   * whitelisted yet, 404 because the controller does not exist, or a
   * body that is not JSON. Callers treat null as "no live data
   * available" and fall back to sample content.
   *
   * @param {string} path   e.g. '/public/faculty'
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
        console.info(
          '[public-api] ' + url + ' returned ' + response.status +
          ' — falling back to sample content. This is expected until the ' +
          'Track B public endpoints are built.'
        );
        return null;
      }

      const body = await response.json();
      liveBackendConfirmed = true;
      return body;

    } catch (error) {
      console.info(
        '[public-api] ' + url + ' is unreachable (' + error.message +
        ') — falling back to sample content.'
      );
      return null;
    }
  }

  function isLive() {
    return liveBackendConfirmed;
  }

  return {
    tryGet: tryGet,
    isLive: isLive,
    toQueryString: toQueryString
  };

})();
