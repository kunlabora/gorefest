# Fonts

Self-hosted, so pages don't call Google Fonts (a third party seeing every visitor of a private site, plus a render-blocking request).

- **Atkinson Hyperlegible Next** (body, `--font-sans`) and **Bricolage Grotesque** (headings, `--font-display`): both SIL Open Font License 1.1, as listed on Google Fonts. The licence and both copyright notices are in `OFL.txt`, which goes into the jar with the fonts.
- Variable fonts, one file per script subset (`latin`, `latin-ext`, `vietnamese`), exactly as Google serves them. `gorefest.css` declares them with `unicode-range`, so the browser fetches a subset only when the page's text needs it.
- The server sends these with `Cache-Control: public, max-age=31536000, immutable`. **Never replace a file under the same name**: add a new file and point `gorefest.css` at it.
- To update: fetch the family's CSS from `fonts.googleapis.com` with a modern browser User-Agent, download the `woff2` URLs it lists, and rewrite the `@font-face` rules at the top of `gorefest.css`.
