/**
 * Faculty Achievement Portal — Global Environment Configuration (Production & Development)
 */

const getApiBaseUrl = () => {
  // Allow explicit runtime environment injection via global window Object if configured in production HTML
  if (typeof window !== 'undefined' && window.__APP_CONFIG__ && window.__APP_CONFIG__.API_BASE_URL) {
    return window.__APP_CONFIG__.API_BASE_URL;
  }

  if (typeof window !== 'undefined' && window.location) {
    const port = window.location.port;
    const hostname = window.location.hostname || '127.0.0.1';
    const protocol = window.location.protocol || 'http:';

    // Opened straight off the disk (file:///C:/.../dashboard.html) rather than
    // through a web server. Both branches below assumed an http origin, so with
    // protocol "file:" they built "file://127.0.0.1:8080/api" — a URL fetch()
    // cannot use, which made every request fail and every page in every portal
    // come up blank at once. Return a well-formed address instead. It will still
    // be refused, because a file page sends "Origin: null" and the backend allows
    // only http://localhost:* and http://127.0.0.1:*, but the request now fails
    // as a recognisable network error, which is what lets IS_FILE_PROTOCOL below
    // turn it into an explanation the reader can act on.
    if (protocol === 'file:') {
      return 'http://localhost:8080/api';
    }

    // If served directly from same-origin backend Spring Boot server on port 8080 or standard HTTP/HTTPS ports (80/443 with domain)
    if (port === '8080' || (port === '' && (protocol === 'http:' || protocol === 'https:'))) {
      return window.location.origin + '/api';
    }

    // Target the backend Spring Boot server on port 8080 using the matching host/IP
    return `${protocol}//${hostname}:8080/api`;
  }

  // Development Fallback: Target Spring Boot backend API on port 8080
  return "http://localhost:8080/api";
};

const CONFIG = {
  APP_NAME: "NIET Faculty Achievement Portal",
  INSTITUTION: "Noida Institute of Engineering and Technology",
  API_BASE_URL: getApiBaseUrl(),
  DATA_SOURCE: "API",

  // True when the page was opened as a file instead of over http. A browser sends
  // "Origin: null" from such a page and the backend's CORS rules do not accept it,
  // so no request can ever succeed. api.js reads this to say so plainly rather
  // than blaming the backend for being down when it is running perfectly well.
  IS_FILE_PROTOCOL: typeof window !== 'undefined'
    && !!window.location
    && window.location.protocol === 'file:'
};
