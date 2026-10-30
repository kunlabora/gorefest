# A3 Structure: one heading, headings in order, announced swaps

Source: product idea, 2026-10-30. Status: 📋 todo. Design: [Accessible by construction](a-accessible-by-construction.md).

**Idea:** `page` renders the one `<h1>`, `section` picks heading levels from nesting, `swapTarget` makes every htmx result region announce itself, and `page()` in the web tests checks the rendered structure on every page.

## Criteria

- [ ] A11Y-7: Every page names its language and has one main heading, one main region and named navigation.
- [ ] A11Y-8: Headings follow each other without skipping a level.
- [ ] A11Y-9: Ids on a page are unique, and every reference to one (labels, descriptions, controls, htmx targets) resolves.
- [ ] A11Y-10: A result htmx swaps in is announced to a screen reader; a refusal is announced at once.
- [ ] `ArchitectureTest`: no view calls `h1(` … `h6(`.
- [ ] `HtmlAssertions.page()` and `fragment()` run the structure and id checks.
- [ ] Screens look as before; no event, route or stored data changes.
