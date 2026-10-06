# U13 Placeholder text is too prominent

Source: user testing, 2026-10-06. Status: ✅

**Feedback:** Placeholder text colour is a bit too accentuated; it is not easily recognised as a placeholder.

**Wanted:** a muted placeholder colour (token) that is clearly lighter than entered text, but still 4.5:1 against the input background in both themes if it carries information (otherwise 3:1 and don't put essential info in placeholders).

**Criteria**
- [x] One `::placeholder` token used by all inputs, both themes

**Done:** New `--placeholder` token (light #7d7352 = 4.7:1, dark #a8a58e = 5.0:1 on `surface-raised`), used by `.gf-input` and `.gf-code`; added to tokens.css and tokens.json.
