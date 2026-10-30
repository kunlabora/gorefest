# Milestone 3: accessibility

WCAG 2.2 AA basics built into the components and held in place by `ArchitectureTest` rules and static checks on the
CSS and the rendered HTML, rather than by a browser scanner. The shared design is in
[`a-accessible-by-construction.md`](requests/a-accessible-by-construction.md); each story has its own file with its
criteria.

Status, the definition of done and the flow-metric columns (Arrived, Started, Done) are explained in
[backlog.md](backlog.md).

## Accessible by construction, 2026-10-30

Decisions (2026-10-30): split into four stories; a contrast pair that fails is fixed in `tokens.css` within A4.

| #  | Story                                                                                      | Status | Depends on | Arrived    | Started    | Done |
|----|--------------------------------------------------------------------------------------------|--------|------------|------------|------------|------|
| A1 | [Forms: every control has its label, hint and error](requests/a1-forms.md)                 | ✅     |            | 2026-10-30 | 2026-10-30 | 2026-10-30 |
| A2 | [Names: every button, link, toggle and image says what it is](requests/a2-names.md)        | ✅     |            | 2026-10-30 | 2026-10-30 | 2026-10-30 |
| A3 | [Structure: one heading, headings in order, announced swaps](requests/a3-structure.md)     | 📋     |            | 2026-10-30 |            |      |
| A4 | [Colour and motion: contrast, focus and target size checked](requests/a4-colour-motion.md) | 📋     |            | 2026-10-30 |            |      |
