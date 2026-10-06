# U14 Fun default game day titles

Source: user testing, 2026-10-06. Status: 📋

**Feedback:** Idea: default titles from a fun game day title generator (things that end with "party", "fest", ...).

**Wanted:** the create form is prefilled with a generated title ("Gorefest" is the family name; e.g. "Meeple Mayhem Party", "Dice Fest"); a small "another one" button re-rolls; the host can still type their own. Generation lives in the shell (randomness via the injected source, not in `core`).

**Criteria**
- [ ] Generator with a wordlist; deterministic under a test random source
- [ ] Create form prefill and re-roll (htmx fragment)
- [ ] Web test
