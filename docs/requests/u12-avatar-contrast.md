# U12 Player avatar circles have bad contrast with list items

Source: user testing, 2026-10-06. Status: ✅

**Feedback:** The player listing avatar circles have bad contrast with the list items.

**Wanted:** avatars readable on list rows in light and dark theme: a border or ring in the row's surface colour, or stronger fills, per the design tokens. Check WCAG contrast for the initials too.

**Criteria**
- [x] Fix in `docs/design/css` (single source), both themes
- [x] Mockups still match

**Done:** Avatars get a 1.5px `line-strong` ring (>= 3:1 on every row background, both themes); initials stay `ink` on the soft fills (>= 7:1). Checked at 375x812, light and dark. Mockups use the same CSS.
