// Loaded right after htmx. Rejections (409 conflict, 403 forbidden) come back as an error fragment
// for the element that asked: let htmx swap it in, like a 200, instead of dropping it as an error.
htmx.config.responseHandling = [
  { code: "204", swap: false },
  { code: "[23]..", swap: true },
  { code: "40[39]", swap: true, error: false },
  { code: "[45]..", swap: false, error: true },
];

// One request at a time per element, and none after one that answered with an HX-Redirect: the browser is
// already on its way to the next page, so a second tap on "Create game day" must not create a second game day.
// htmx queues that second tap and fires it once the first answers, so the guard has to outlive the first request.
// Don't read evt.detail.successful here: htmx 2 follows an HX-Redirect before it sets it, so it is never true then.
document.addEventListener("htmx:beforeRequest", (evt) => {
  const elt = evt.detail.elt;
  if (elt.dataset.gfBusy) return evt.preventDefault();
  elt.dataset.gfBusy = "1";
});
document.addEventListener("htmx:afterRequest", (evt) => {
  const elt = evt.detail.elt;
  const xhr = evt.detail.xhr;
  const redirecting = xhr && xhr.status >= 200 && xhr.status < 300 && xhr.getResponseHeader("HX-Redirect");
  if (!redirecting) delete elt.dataset.gfBusy;
});
// Back to a page the browser kept in its back/forward cache (Safari does, despite no-store): the form that redirected
// is still marked busy and spinning, so free it, or every tap on it would be dropped.
window.addEventListener("pageshow", (evt) => {
  if (!evt.persisted) return;
  document.querySelectorAll("[data-gf-busy]").forEach((elt) => delete elt.dataset.gfBusy);
  document.querySelectorAll(".htmx-request").forEach((elt) => elt.classList.remove("htmx-request"));
});

// A swapped-in element says what changed in data-announce (the `announce` component); the page's status line reads it
// out, since the swapped region itself is replaced and can't be a live region. The sentence is read from the response's
// root element, so it holds for any swap style, and even when a background refresh replaces the new element at once.
// A poll ("every 2s", the collection page) doesn't repeat what it already said; anything else does, even word for word
// (a second search that also finds 3 games), so the line is cleared first and filled a moment later.
document.addEventListener("htmx:afterSwap", (evt) => {
  const status = document.getElementById("gf-announce");
  const xhr = evt.detail.xhr;
  if (!status || !xhr) return;
  const template = document.createElement("template");
  template.innerHTML = xhr.responseText;
  const root = template.content.firstElementChild;
  const message = root && root.dataset ? root.dataset.announce : null;
  if (!message) return;
  const polled = /\bevery\b/.test(evt.detail.requestConfig?.elt?.getAttribute("hx-trigger") || "");
  if (polled && status.textContent === message) return;
  status.textContent = "";
  setTimeout(() => { status.textContent = message; }, 100);
});
