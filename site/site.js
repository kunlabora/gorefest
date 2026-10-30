// Theme toggle and the hero's placeholder art. The demo lives in demo.js.
(function () {
  'use strict';
  var html = document.documentElement;
  var toggle = document.querySelector('[data-theme-toggle]');

  function isDark() {
    return html.dataset.theme ? html.dataset.theme === 'dark' : window.matchMedia('(prefers-color-scheme: dark)').matches;
  }

  function sync() { toggle.setAttribute('aria-pressed', String(isDark())); }

  toggle.addEventListener('click', function () {
    var theme = isDark() ? 'light' : 'dark';
    html.dataset.theme = theme;
    try { localStorage.setItem('bf-theme', theme); } catch (e) {}
    sync();
  });
  sync();
})();
