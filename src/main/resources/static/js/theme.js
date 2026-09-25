// Loaded in <head> without defer: applies the saved theme before the page paints, so dark mode
// never flashes light. Without a saved choice, tokens.css follows the OS.
(function () {
  var saved = document.cookie.match(/(?:^|; )gf_theme=(light|dark)(?:;|$)/);
  if (saved) document.documentElement.dataset.theme = saved[1];
})();

/** Called by the appearance picker: "light", "dark", or anything else for "follow the system". */
function gorefestSetTheme(theme) {
  var root = document.documentElement;
  if (theme === "light" || theme === "dark") {
    root.dataset.theme = theme;
    document.cookie = "gf_theme=" + theme + "; path=/; max-age=31536000; samesite=lax";
  } else {
    delete root.dataset.theme;
    document.cookie = "gf_theme=; path=/; max-age=0; samesite=lax";
  }
}
