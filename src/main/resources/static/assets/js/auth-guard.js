(() => {
  try {
    const session = JSON.parse(localStorage.getItem('crmsSession') || 'null');
    if (!session?.access_token || (session.expires_at && Date.parse(session.expires_at) <= Date.now())) {
      throw new Error('Session unavailable');
    }
  } catch (_) {
    localStorage.removeItem('crmsSession');
    const page = location.pathname.split('/').pop() || 'index.html';
    location.replace(`login.html?returnTo=${encodeURIComponent(page)}`);
  }
})();
