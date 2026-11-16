// Shared fetch wrapper: JSON in/out, CSRF header, and one error shape for every page.
class ApiError extends Error {
  constructor(message, status, errors) {
    super(message);
    this.status = status;
    this.errors = errors || {};
  }
}

const Api = (() => {
  // Spring Security puts the CSRF token in the XSRF-TOKEN cookie; it must be echoed back as a header.
  function csrfToken() {
    const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/);
    return match ? decodeURIComponent(match[1]) : null;
  }

  async function request(method, url, body) {
    const headers = { Accept: 'application/json' };
    const options = { method, headers, credentials: 'same-origin' };
    if (method !== 'GET') {
      const token = csrfToken();
      if (token) headers['X-XSRF-TOKEN'] = token;
    }
    if (body instanceof FormData || body instanceof URLSearchParams) {
      options.body = body;
    } else if (body !== undefined) {
      headers['Content-Type'] = 'application/json';
      options.body = JSON.stringify(body);
    }

    let response;
    try {
      response = await fetch(url, options);
    } catch {
      throw new ApiError('Network error. Check your connection and try again.', 0);
    }

    if (response.status === 401 && url !== '/api/auth/login') {
      location.href = '/login.html?expired';
      throw new ApiError('Your session has expired. Please log in again.', 401);
    }
    if (response.status === 204) return null;

    const data = await response.json().catch(() => null);
    if (!response.ok) {
      const message = data?.detail || data?.title || `Request failed (${response.status})`;
      throw new ApiError(response.status === 403 && !data ? 'You are not allowed to do that.' : message,
        response.status, data?.errors);
    }
    return data;
  }

  return {
    get: (url) => request('GET', url),
    post: (url, body) => request('POST', url, body),
    put: (url, body) => request('PUT', url, body),
    patch: (url, body) => request('PATCH', url, body),
    del: (url) => request('DELETE', url),
  };
})();
