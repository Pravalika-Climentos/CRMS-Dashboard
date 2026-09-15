(() => {
  'use strict';
  const SESSION_KEY = 'crmsSession';
  const originalFetch = window.fetch.bind(window);

  function readSession() {
    try { return JSON.parse(localStorage.getItem(SESSION_KEY) || 'null'); }
    catch (_) { return null; }
  }
  function getAccessToken() { return readSession()?.access_token || null; }
  function getCurrentUser() { return readSession()?.user || null; }
  function saveSession(response) {
    localStorage.setItem(SESSION_KEY, JSON.stringify({
      access_token: response.accessToken,
      expires_at: response.expiresAt,
      user: response.user
    }));
  }
  function clearSession() { localStorage.removeItem(SESSION_KEY); }
  function goToLogin() {
    const target = location.pathname.split('/').pop() || 'index.html';
    location.replace(`login.html?returnTo=${encodeURIComponent(target)}`);
  }
  function isSameOriginApi(input) {
    const raw = typeof input === 'string' ? input : input.url;
    const url = new URL(raw, location.href);
    return url.origin === location.origin && url.pathname.startsWith('/api/');
  }
  async function authenticatedFetch(input, init = {}) {
    const headers = new Headers(init.headers || (input instanceof Request ? input.headers : undefined));
    const token = getAccessToken();
    if (token && isSameOriginApi(input)) headers.set('Authorization', `Bearer ${token}`);
    const response = await originalFetch(input, { ...init, headers });
    if (response.status === 401 && !new URL(typeof input === 'string' ? input : input.url, location.href).pathname.endsWith('/api/auth/login')) {
      clearSession();
      goToLogin();
    }
    return response;
  }
  async function logout() {
    try { await authenticatedFetch('/api/auth/logout', { method: 'POST' }); } catch (_) {}
    clearSession();
    location.replace('login.html');
  }
  async function loadCurrentUser() {
    const response = await authenticatedFetch('/api/auth/me');
    if (!response.ok) throw new Error('Unable to load current user.');
    const user = await response.json();
    const session = readSession();
    if (session) localStorage.setItem(SESSION_KEY, JSON.stringify({ ...session, user }));
    return user;
  }

  window.CrmsAuth = { readSession, getAccessToken, getCurrentUser, saveSession,
    clearSession, authenticatedFetch, loadCurrentUser, logout, goToLogin };

  // Existing module code that calls fetch('/api/...') receives the JWT automatically.
  window.fetch = authenticatedFetch;
})();

document.addEventListener('DOMContentLoaded', () => {
    document
        .querySelectorAll('[data-crms-logout]')
        .forEach(button => {
            button.addEventListener('click', event => {
                event.preventDefault();
                window.CrmsAuth.logout();
            });
        });
});