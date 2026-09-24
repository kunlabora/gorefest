# Design

The Gorefest design system and mockups for every screen in [the design brief](../design-brief.md).

- **Open [`mockups/index.html`](mockups/index.html) in a browser** to see all screens and components, in light and dark.
- [`brand-book.md`](brand-book.md): voice, colour, type, layout, htmx patterns and accessibility rules. Read this before building a page.
- [`microcopy.md`](microcopy.md): sample copy for every screen and the product-name candidates.
- [`css/tokens.css`](css/tokens.css): design tokens as custom properties. Follows the OS theme; `<html data-theme="light|dark">` forces one.
- [`css/gorefest.css`](css/gorefest.css): the components as plain CSS classes (`gf-*`). No build step.
- [`tokens.json`](tokens.json): the token source, with a usage note per token.
- [`icons/`](icons/): the line icon set. Inline the SVG with `stroke="currentColor"` and class `gf-icon`.

The live, browsable version is the "Gorefest" design system artifact on claude.ai. Its component previews match `mockups/`.

## Using it in the app

Serve `tokens.css` and `gorefest.css` as static resources, link them (in that order) from the page shell in `Html.kt` in place of Pico, and build pages from the markup in the mockups. The mockups use the real class names and `hx-*` attributes, so a screen's HTML can be ported to kotlinx.html almost line by line.
