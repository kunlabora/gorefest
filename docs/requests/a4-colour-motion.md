# A4 Colour and motion: contrast, focus and target size checked

Source: product idea, 2026-10-30. Status: 📋 todo. Design: [Accessible by construction](a-accessible-by-construction.md).

**Idea:** a unit test reads `tokens.css` and `gorefest.css`, resolves the tokens per theme and checks contrast for every pair the components use, plus the focus ring, reduced motion and target sizes. A failing pair is fixed in `tokens.css` (decided 2026-10-30).

## Criteria

- [ ] A11Y-11: Text has at least 4.5:1 contrast on every surface it's used on, in the light and the dark theme.
- [ ] A11Y-12: Borders that mark a control and the focus ring have at least 3:1 contrast on every surface, in both themes.
- [ ] A11Y-13: The keyboard focus is always visible, and motion calms down when the device asks for less.
- [ ] A11Y-14: Every button, chip and icon button is at least 24px in both directions.
- [ ] Any token fixed is checked against the mockups in both themes.
- [ ] `docs/contract/README.md` Known gaps: the manual VoiceOver and keyboard pass.
- [ ] No event, route or stored data changes.
