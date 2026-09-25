// Loaded in <head> without defer: applies the saved theme before the page paints, so dark mode
// never flashes light. Until someone picks one, tokens.css follows the device.
(function () {
  var saved = document.cookie.match(/(?:^|; )gf_theme=(light|dark)(?:;|$)/);
  if (saved) document.documentElement.dataset.theme = saved[1];
  document.addEventListener("DOMContentLoaded", syncToggles);
})();

function currentTheme() {
  return document.documentElement.dataset.theme ||
    (window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light");
}

/** Tells assistive tech whether dark mode is on. */
function syncToggles() {
  var dark = currentTheme() === "dark";
  document.querySelectorAll(".gf-theme-toggle").forEach(function (button) {
    button.setAttribute("aria-pressed", String(dark));
  });
}

/** The app bar's sun/moon button: switch to the other theme and remember it for a year. */
function gorefestToggleTheme() {
  var next = currentTheme() === "dark" ? "light" : "dark";
  document.documentElement.dataset.theme = next;
  document.cookie = "gf_theme=" + next + "; path=/; max-age=31536000; samesite=lax";
  syncToggles();
}
