# U6 "We haven't learned your taste" shown right after a taste test

Source: user testing, 2026-10-06. Status: 📋

**Feedback:** A player was told we haven't learned their taste even though they just did the taste test. They had not linked BGG and had pressed the browser back button midway through the test.

**Wanted:** reproduce (back button mid-wizard, no BGG link) and fix. Candidates: the wizard's state lives in the page/URL and back re-submits or skips answers; answers recorded for another game day; the "thin profile" check counts only some bases.

**Criteria**
- [ ] A failing test that reproduces it
- [ ] Fix, plus a regression test for back-button navigation mid-wizard
- [ ] The "haven't learned" message appears only when it is true
