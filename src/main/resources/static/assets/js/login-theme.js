(function () {
  var mode = 'light';
  try {
    var cfg = JSON.parse(localStorage.getItem('__THEME_CONFIG__') || 'null');
    if (cfg && (cfg.theme === 'dark' || cfg.theme === 'light')) mode = cfg.theme;
    else if (window.matchMedia && matchMedia('(prefers-color-scheme: dark)').matches) mode = 'dark';
  } catch (e) {}
  document.documentElement.setAttribute('data-bs-theme', mode);
})();
