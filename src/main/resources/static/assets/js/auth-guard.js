(() => {
  try {
    const session = JSON.parse(localStorage.getItem('crmsSession') || 'null');
    if (!session?.access_token || (session.expires_at && Date.parse(session.expires_at) <= Date.now())) {
      throw new Error('Session unavailable');
    }
    const page = (location.pathname.split('/').pop() || 'index.html').toLowerCase();
    const embedded = new URLSearchParams(location.search).get('crmsEmbedded') === '1';
    if (embedded) {
      document.documentElement.classList.add('crms-embedded-view');
      const style = document.createElement('style');
      style.id = 'crms-embedded-early-style';
      style.textContent = '.crms-embedded-view .navbar-header,.crms-embedded-view .header,.crms-embedded-view #sidebar{display:none!important}.crms-embedded-view .page-wrapper{margin:0!important;min-height:100vh!important}.crms-embedded-view .content{padding-top:18px!important}';
      document.head.appendChild(style);
    } else if (page !== 'index.html') {
      document.documentElement.classList.add('crms-shell-pending');
      const style = document.createElement('style');
      style.id = 'crms-shell-pending-style';
      style.textContent = '.crms-shell-pending .navbar-header,.crms-shell-pending .header,.crms-shell-pending #sidebar{visibility:hidden!important}';
      document.head.appendChild(style);
    }
  } catch (_) {
    localStorage.removeItem('crmsSession');
    const page = location.pathname.split('/').pop() || 'index.html';
    location.replace(`login.html?returnTo=${encodeURIComponent(page)}`);
  }
})();
