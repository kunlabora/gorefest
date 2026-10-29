# Milestone 2: user testing and GDPR

What the first user test (2026-10-06) asked for, and the GDPR work that followed (2026-10-09), released as 2.1 and 2.2.

Status, the definition of done and the flow-metric columns (Arrived, Started, Done) are explained in
[backlog.md](backlog.md).

## User testing feedback, 2026-10-06

One request file per item in [`docs/requests/`](requests/); each has its own criteria. Decisions: U16 cancels silently;
U9 and U11 (option C) are done (2026-10-14); U17 and U19 won't be done (2026-10-14).

| #   | Story                                                                                                                | Status | Depends on | Arrived    | Started    | Done       |
|-----|----------------------------------------------------------------------------------------------------------------------|--------|------------|------------|------------|------------|
| U1  | [Joining the game day straight after signing up](requests/u1-join-after-signup.md)                                   | ✅     |            | 2026-10-06 | 2026-10-06 | 2026-10-06 |
| U2  | [Don't suggest games a player said they don't know](requests/u2-no-suggestion-for-unknown-game.md)                   | ✅     |            | 2026-10-06 | 2026-10-06 | 2026-10-06 |
| U3  | [Absurd playtimes (7th Continent: 5-1000 min, Sleeping Gods: 60-1200 min)](requests/u3-absurd-playtimes.md)          | ✅     |            | 2026-10-06 | 2026-10-06 | 2026-10-06 |
| U4  | [Show a game's type/category to help make choices](requests/u4-game-type-in-choices.md)                              | ✅     |            | 2026-10-06 | 2026-10-06 | 2026-10-06 |
| U5  | [Replace "Don't know" in the wizard with clearer "Haven't played it" wording](requests/u5-haven-t-played-wording.md) | ✅     |            | 2026-10-06 | 2026-10-06 | 2026-10-06 |
| U6  | ["We haven't learned your taste" shown right after a taste test](requests/u6-taste-not-learned-bug.md)               | ✅     |            | 2026-10-06 | 2026-10-06 | 2026-10-06 |
| U7  | [Log plays during the game day, not only after it](requests/u7-log-plays-during-game-day.md)                         | ✅     |            | 2026-10-06 | 2026-10-06 | 2026-10-06 |
| U8  | [Does logging a play influence the matching?](requests/u8-does-play-log-influence-matching.md)                       | ✅     |            | 2026-10-06 | 2026-10-06 | 2026-10-06 |
| U9  | [Having seen a table play a game helps form an opinion](requests/u9-seen-it-played.md)                               | ✅     |            | 2026-10-06 | 2026-10-14 | 2026-10-14 |
| U10 | [Indicate which shelf game you really want to play](requests/u10-want-to-play-pick.md)                               | ✅     |            | 2026-10-06 | 2026-10-06 | 2026-10-06 |
| U11 | [Organise a small game evening from people's libraries](requests/u11-small-evening-from-library.md)                  | ✅     | S17        | 2026-10-06 | 2026-10-14 | 2026-10-14 |
| U12 | [Player avatar circles have bad contrast with list items](requests/u12-avatar-contrast.md)                           | ✅     |            | 2026-10-06 | 2026-10-06 | 2026-10-06 |
| U13 | [Placeholder text is too prominent](requests/u13-placeholder-colour.md)                                              | ✅     |            | 2026-10-06 | 2026-10-06 | 2026-10-06 |
| U14 | [Fun default game day titles](requests/u14-game-day-title-generator.md)                                              | ✅     |            | 2026-10-06 | 2026-10-06 | 2026-10-06 |
| U15 | [Loading indicator during active search](requests/u15-search-loading-indicator.md)                                   | ✅     |            | 2026-10-06 | 2026-10-06 | 2026-10-06 |
| U16 | [Cancel a game day](requests/u16-cancel-game-day.md)                                                                 | ✅     |            | 2026-10-06 | 2026-10-06 | 2026-10-06 |
| U17 | [Link BGG by a message from a Boardfest bot account](requests/u17-bgg-link-via-bot-message.md)                       | 🚫     |            | 2026-10-06 |            | 2026-10-14 |
| U18 | ["Your likely favourites today": pills overlap the bottom after scrolling](requests/u18-favourites-pills-overlap.md) | ✅     |            | 2026-10-06 | 2026-10-06 | 2026-10-06 |
| U19 | [Update emails when people add games (and push as an alternative)](requests/u19-update-emails-and-push.md)           | 🚫     |            | 2026-10-06 |            | 2026-10-14 |
| U20 | [Ask someone to bring a game from their collection](requests/u20-ask-someone-to-bring-a-game.md)                     | ✅     |            | 2026-10-08 | 2026-10-08 | 2026-10-08 |

## GDPR, 2026-10-09

Analysis in [`gdpr.md`](gdpr.md); one request file per story. G2 is the hard one (rewriting the immutable log); G1, G4,
G5 and G8 are independent and small.

| #  | Story                                                                                                                                     | Status | Depends on | Arrived    | Started    | Done       |
|----|-------------------------------------------------------------------------------------------------------------------------------------------|--------|------------|------------|------------|------------|
| G1 | [Privacy notice and processing register](requests/g1-privacy-notice.md)                                                                   | ✅     |            | 2026-10-09 | 2026-10-12 | 2026-10-12 |
| G2 | [Erase my account: the event log](requests/g2-erasure-event-log.md)                                                                       | ✅     |            | 2026-10-09 | 2026-10-12 | 2026-10-12 |
| G3 | [Erase my account: the screen](requests/g3-erasure-ui.md)                                                                                 | ✅     | G2         | 2026-10-09 | 2026-10-12 | 2026-10-12 |
| G4 | [Download my data](requests/g4-data-export.md)                                                                                            | ✅     |            | 2026-10-09 | 2026-10-12 | 2026-10-12 |
| G5 | [Don't show the place to non-members](requests/g5-invite-place.md)                                                                        | ✅     |            | 2026-10-09 | 2026-10-12 | 2026-10-12 |
| G6 | [Change email, unlink BGG](requests/g6-rectification.md)                                                                                  | ✅     | G2         | 2026-10-09 | 2026-10-12 | 2026-10-12 |
| G7 | [Retention, backups and restore](requests/g7-retention-backups.md)                                                                        | ✅     | G2         | 2026-10-09 | 2026-10-12 | 2026-10-12 |
| G8 | [Logs and URLs: no secrets, short retention](requests/g8-logs.md)                                                                         | ✅     |            | 2026-10-09 | 2026-10-12 | 2026-10-12 |
| G9 | Manual approval of new accounts: `/pending`, `/admin/approvals`, `ADMIN_EMAIL` (supports the household-exemption argument; see `gdpr.md`) | ✅     |            | 2026-10-09 | 2026-10-09 | 2026-10-09 |
