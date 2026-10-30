# A2 Names: every button, link, toggle and image says what it is

Source: product idea, 2026-10-30. Status: 📋 todo. Design: [Accessible by construction](a-accessible-by-construction.md).

**Idea:** buttons, links, icon buttons, toggles and images take their accessible name or alt as a required, non-blank parameter, and the ARIA attributes views write by hand become typed parameters on components.

## Criteria

- [ ] A11Y-4: Every button and link has a name a screen reader can say, icon-only ones included.
- [ ] A11Y-5: Decorative icons and pictures are hidden from screen readers; an image that informs says what it shows.
- [ ] A11Y-6: A toggle says whether it is on.
- [ ] `button`/`submitButton`, `iconButton`, `textLink`, `toggle(pressed)`, `image(alt: Alt)`, `AccessibleName` refuses a blank string.
- [ ] `ArchitectureTest`: no view calls `button(` or `img(`, or sets `aria-*` or `role`; every public component rendering a `button`, `a` or `input` takes a `label`.
- [ ] Screens look as before; no event, route or stored data changes.
