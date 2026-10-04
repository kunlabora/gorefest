// Loaded right after htmx. Rejections (409 conflict, 403 forbidden) come back as an error fragment
// for the element that asked: let htmx swap it in, like a 200, instead of dropping it as an error.
htmx.config.responseHandling = [
  { code: "204", swap: false },
  { code: "[23]..", swap: true },
  { code: "40[39]", swap: true, error: false },
  { code: "[45]..", swap: false, error: true },
];
