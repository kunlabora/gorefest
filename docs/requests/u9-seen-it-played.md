# U9 Having seen a table play a game helps form an opinion

Source: user testing, 2026-10-06. Status: 📋

**Feedback:** Having watched a table play a game sometimes also helps a player decide whether they'd like it.

**Idea:** a third comparison basis `WATCHED` (between VIBE and PLAYED in weight): at the checkpoint, "did you watch anyone play X?" then "would you rather play it than Y?".

**Decision (2026-10-06):** record only, build later.

**Open questions:** weighting of WATCHED vs VIBE and PLAYED; do we infer it from the play log's player list (people at the table next to it)? New event or a new basis on `ComparisonRecorded` (a default-valued enum case is a safe addition).
