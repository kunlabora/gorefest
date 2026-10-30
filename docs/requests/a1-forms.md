# A1 Forms: every control has its label, hint and error

Source: product idea, 2026-10-30. Status: 🚧 doing. Design: [Accessible by construction](a-accessible-by-construction.md).

**Idea:** a form control can only be rendered inside `field`, which gives it its id, its label, and ties its hint and error to it. A rule keeps views from rendering a control by hand.

## Criteria

- [ ] A11Y-1: Every form control has a label tied to it, so a screen reader names it.
- [ ] A11Y-2: A field's hint and error are read with its control, and a refused field is marked invalid.
- [ ] A11Y-3: A set of choices (segmented control, chips, switches) is announced with its question.
- [ ] `field` owns its control (`FieldScope` builders); `switch` for the `gf-switch` checkboxes; the poll's date rows each have a label.
- [ ] `ArchitectureTest`: no view outside `web.components` calls `input(`, `select(`, `textarea(`, `label(` or `fieldSet(`.
- [ ] `docs/contract/a11y.md` exists and `docs/contract/README.md` lists it.
- [ ] Every form looks as before (browser, desktop and 375px, light and dark); existing web tests pass on the same selectors.
- [ ] No event, route or stored data changes.
