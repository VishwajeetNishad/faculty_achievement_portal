/**
 * Faculty Achievement Portal — Centralized REST API Client
 * Wraps Vanilla JavaScript fetch() calls for Spring Boot backend communication.
 * Automatically injects Bearer JWT authentication header from sessionStorage.
 */

const ApiClient = (() => {

  const getHeaders = (isMultipart = false) => {
    const headers = {};
    if (!isMultipart) {
      headers['Content-Type'] = 'application/json';
      headers['Accept'] = 'application/json';
    }

    const token = sessionStorage.getItem('accessToken');
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    return headers;
  };

  const handleResponse = async (response) => {
    // Handle 401 Unauthorized (Expired or invalid token)
    if (response.status === 401) {
      sessionStorage.removeItem('accessToken');
      sessionStorage.removeItem('currentUser');
      if (!window.location.pathname.endsWith('login.html') && !window.location.pathname.endsWith('index.html')) {
        window.location.href = 'login.html?session=expired';
      }
      return { success: false, status: 401, message: 'Session expired or unauthorized. Please sign in again.' };
    }

    // 204 No Content
    if (response.status === 204) {
      return { success: true, data: null };
    }

    let data;
    try {
      data = await response.json();
    } catch (e) {
      data = null;
    }

    if (response.ok) {
      return { success: true, data: data };
    }

    const errorMessage = (data && data.message) 
      ? data.message 
      : (response.status === 404 ? 'Requested resource not found.' : `HTTP Error ${response.status}`);

    return { 
      success: false, 
      status: response.status, 
      message: errorMessage,
      data: data 
    };
  };

  const handleError = (error) => {
    console.error('API Client Network Failure:', error);
    return {
      success: false,
      status: 0,
      message: 'Unable to connect to the Faculty Achievement Portal server. Please check that the Spring Boot backend is running.'
    };
  };

  return {
    get: async (endpoint) => {
      try {
        const response = await fetch(`${CONFIG.API_BASE_URL}${endpoint}`, {
          method: 'GET',
          headers: getHeaders()
        });
        return await handleResponse(response);
      } catch (error) {
        return handleError(error);
      }
    },

    post: async (endpoint, body) => {
      try {
        const response = await fetch(`${CONFIG.API_BASE_URL}${endpoint}`, {
          method: 'POST',
          headers: getHeaders(),
          body: JSON.stringify(body)
        });
        return await handleResponse(response);
      } catch (error) {
        return handleError(error);
      }
    },

    put: async (endpoint, body) => {
      try {
        const response = await fetch(`${CONFIG.API_BASE_URL}${endpoint}`, {
          method: 'PUT',
          headers: getHeaders(),
          body: JSON.stringify(body)
        });
        return await handleResponse(response);
      } catch (error) {
        return handleError(error);
      }
    },

    patch: async (endpoint, body) => {
      try {
        const response = await fetch(`${CONFIG.API_BASE_URL}${endpoint}`, {
          method: 'PATCH',
          headers: getHeaders(),
          body: JSON.stringify(body)
        });
        return await handleResponse(response);
      } catch (error) {
        return handleError(error);
      }
    },

    delete: async (endpoint) => {
      try {
        const response = await fetch(`${CONFIG.API_BASE_URL}${endpoint}`, {
          method: 'DELETE',
          headers: getHeaders()
        });
        return await handleResponse(response);
      } catch (error) {
        return handleError(error);
      }
    },

    /**
     * Multipart/form-data upload method for PDF files.
     * Note: Do NOT set Content-Type header manually; browser generates boundary automatically.
     */
    upload: async (endpoint, formData) => {
      try {
        const response = await fetch(`${CONFIG.API_BASE_URL}${endpoint}`, {
          method: 'POST',
          headers: getHeaders(true),
          body: formData
        });
        return await handleResponse(response);
      } catch (error) {
        return handleError(error);
      }
    },

    /**
     * Protected file download method returning Object URL for authenticated PDF viewing.
     */
    downloadBlob: async (endpoint) => {
      try {
        const response = await fetch(`${CONFIG.API_BASE_URL}${endpoint}`, {
          method: 'GET',
          headers: getHeaders(true)
        });

        if (response.status === 401) {
          sessionStorage.removeItem('accessToken');
          sessionStorage.removeItem('currentUser');
          window.location.href = 'login.html?session=expired';
          return { success: false, message: 'Session expired' };
        }

        if (!response.ok) {
          return { success: false, message: `Failed to download file (HTTP ${response.status})` };
        }

        const blob = await response.blob();
        const objectUrl = URL.createObjectURL(blob);
        return { success: true, objectUrl: objectUrl };
      } catch (error) {
        return handleError(error);
      }
    }
  };

})();
