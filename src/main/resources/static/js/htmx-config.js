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
document.addEventListener("htmx:beforeRequest", (evt) => {
  const elt = evt.detail.elt;
  if (elt.dataset.gfBusy) return evt.preventDefault();
  elt.dataset.gfBusy = "1";
});
document.addEventListener("htmx:afterRequest", (evt) => {
  const elt = evt.detail.elt;
  const xhr = evt.detail.xhr;
  if (!(evt.detail.successful && xhr && xhr.getResponseHeader("HX-Redirect"))) delete elt.dataset.gfBusy;
});
