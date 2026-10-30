# A4 Colour and motion: contrast, focus and target size checked

Source: product idea, 2026-10-30. Status: ✅ built. Design: [Accessible by construction](a-accessible-by-construction.md).

**Idea:** a unit test reads `tokens.css` and `gorefest.css`, resolves the tokens per theme and checks contrast for every pair the components use, plus the focus ring, reduced motion and target sizes. A failing pair is fixed in `tokens.css` (decided 2026-10-30).

## Criteria

- [x] A11Y-11: Text has at least 4.5:1 contrast on every surface it's used on, in the light and the dark theme.
- [x] A11Y-12: Borders that mark a control and the focus ring have at least 3:1 contrast on every surface, in both themes.
- [x] A11Y-13: The keyboard focus is always visible, and motion calms down when the device asks for less.
- [x] A11Y-14: Every button, chip and icon button is at least 24px in both directions.
- [x] Any token fixed is checked against the mockups in both themes.
- [x] `docs/contract/README.md` Known gaps: the manual VoiceOver and keyboard pass.
- [x] No event, route or stored data changes.

Built as designed, with these notes:

- `ColourAndMotionTest` (`web.components`) reads `docs/design/css/tokens.css` and `gorefest.css`. For every rule that sets a `color`, it finds the background behind it: the rule's own, else the nearest one on the element or an ancestor in its selector, else the three page surfaces. It then checks 4.5:1, or 3:1 for icons and spinners, in both themes. It also checks the text tokens on every surface and soft fill, each fill against its own ink, and that the dark-device block matches the dark toggle's.
- Every pair already passed, so no token changed and the mockups look as before. The test found two other bugs, both fixed: the sign-in shortlist illustration's tick used `--good`, which doesn't exist (it is olive now), and with reduced motion on, the second card dealt on the wizard still animated (`.gf-deal .gf-pick:last-of-type` won on specificity).
- The star on "Both great" is the one exception (3.0:1 on the light surface): it sits beside its own words and is hidden from screen readers, so WCAG 1.4.11 doesn't apply.
- Focus: `:focus-visible` draws a 3px `--focus` ring, nothing turns an outline off, and every input hidden inside a tile, chip or segment has a `:has(input:focus-visible)` ring on its holder.
- Targets are 44px or more (WCAG 2.2 AA asks 24px); the test also reads the `@media` overrides.
