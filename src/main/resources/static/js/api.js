// Thin wrapper around fetch() that always sends the session cookie,
// parses JSON, and throws a readable error message on failure.
const Api = {
  async request(method, path, body) {
    const opts = {
      method,
      credentials: 'include',
      headers: {},
    };
    if (body !== undefined) {
      opts.headers['Content-Type'] = 'application/json';
      opts.body = JSON.stringify(body);
    }
    const res = await fetch(window.API_BASE + path, opts);

    if (res.status === 401 && path !== '/api/auth/login') {
      if (!location.pathname.endsWith('/index.html') && location.pathname !== '/') {
        window.location.href = resolveLoginPath();
      }
      throw new Error('Session expired. Please log in again.');
    }

    const contentType = res.headers.get('content-type') || '';
    let data = null;
    if (contentType.includes('application/json')) {
      data = await res.json().catch(() => null);
    } else if (contentType.includes('application/pdf')) {
      data = await res.blob();
    }

    if (!res.ok) {
      const message = (data && data.message) ? data.message : `Request failed (${res.status})`;
      throw new Error(message);
    }
    return data;
  },

  get(path) { return this.request('GET', path); },
  post(path, body) { return this.request('POST', path, body); },
  put(path, body) { return this.request('PUT', path, body); },
  del(path) { return this.request('DELETE', path); },

  async getBlob(path) {
    const res = await fetch(window.API_BASE + path, { credentials: 'include' });
    if (!res.ok) {
      const data = await res.json().catch(() => null);
      throw new Error((data && data.message) ? data.message : `Request failed (${res.status})`);
    }
    return res.blob();
  }
};

function resolveLoginPath() {
  // Works whether the app is served from /frontend root or nested under /pages/
  return location.pathname.includes('/pages/') ? '../index.html' : 'index.html';
}

function openPdfBlob(blob, filename) {
  const url = window.URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.target = '_blank';
  a.rel = 'noopener';
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => window.URL.revokeObjectURL(url), 60000);
}
