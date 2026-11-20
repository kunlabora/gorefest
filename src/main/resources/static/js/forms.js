// Small helpers the views' hyperscript calls. They live here, not in a hyperscript `js` block, because the
// Content-Security-Policy forbids eval, and a `js` block is eval.

/** Keeps only the digits of a pasted or typed code, at most [max] of them. */
function gorefestDigitsOnly(input, max) {
  input.value = input.value.replace(/\D/g, "").slice(0, max);
}
