# Design

The Gorefest design system and mockups for every screen in [the design brief](../design-brief.md).

- **Redesign proposal:** open [`Gorefest App Redesign.html`](<Gorefest App Redesign.html>) (serve the repo root, e.g. `python3 -m http.server`, so the covers load). It redraws every screen in the login's layout; desktop and phone are one markup.
- **Open [`mockups/index.html`](mockups/index.html) in a browser** to see all screens and components, in light and dark.
- [`brand-book.md`](brand-book.md): voice, colour, type, layout, htmx patterns and accessibility rules. Read this before building a page.
- [`microcopy.md`](microcopy.md): sample copy for every screen and the product-name candidates.
- [`css/tokens.css`](css/tokens.css): design tokens as custom properties. Follows the OS theme; `<html data-theme="light|dark">` forces one.
- [`css/gorefest.css`](css/gorefest.css): the components as plain CSS classes (`gf-*`). No build step.
- [`tokens.json`](tokens.json): the token source, with a usage note per token.
- [`icons/`](icons/): the line icon set. Inline the SVG with `stroke="currentColor"` and class `gf-icon`.

The live, browsable version is the "Gorefest" design system artifact on claude.ai. Its component previews match `mockups/`.

## Using it in the app

The app serves these files directly: Gradle copies `css/` and `icons/` into the jar's `static/` resources, and Ktor serves them under `/static`. Edit them here, not in `build/`. `Html.kt` has the page shell (`page`, app bar, dock) and helpers for icons, avatars and empty states. Port a screen by copying the mockup's markup into kotlinx.html; the mockups use the real class names and `hx-*` attributes.
