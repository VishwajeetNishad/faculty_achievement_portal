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
  DATA_SOURCE: "API"
};
