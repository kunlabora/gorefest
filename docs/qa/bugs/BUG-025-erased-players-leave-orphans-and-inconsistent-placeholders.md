# BUG-025: After erasure, the erased person's shelf games and requests stay, and the placeholder is "?" in some places and "Someone" in others

- **Severity:** minor (G2 documents that other projections keep the id, so this is partly by design)
- **Area:** hub shelf, play log lines, bring requests after `PlayerErased`

## Steps to reproduce
1. Cara hosts "Cara Day" (upcoming); Anna and Dan join; Cara and Anna each bring a game; Anna asks Bram to bring Concordia; Dan asks Anna to bring Dixit.
2. Admin erases Cara, then Anna (`/admin/requests`, "Delete account").
3. Look at the hub of Cara Day as Anna/Dan, the hub and log of "QA Today" (a play with Anna and Bram), the bring page as Bram, and the readiness page.

## Actual
- Cara's CATAN stays on the shelf of the upcoming game day with a "?" avatar, though nobody will bring it (shelf counter, invite page "N games on the shelf" and the matcher still count it; it is only left out of suggestions when the player count doesn't fit).
- "Played today: Carcassonne, **?**, Bram · 45 min" (play log) but "**Someone** asks if you can bring Concordia" with an "S" avatar (bring page): two different placeholders.
- Anna's request to Bram stays open and keeps showing as "1 bring request waiting" on the host's readiness page, and still counts against Bram's per-owner cap of 5; Dan's open request to the erased Anna can never be answered.
- A host-less game day can no longer be edited, cancelled or have its invite rotated (the warning on /account says so, which is accurate).

## Expected
At least one placeholder ("Someone"); preferably the erased person's open requests are withdrawn/answered and their shelf entries dropped from an upcoming game day (as `GameDays` already drops them from the table).

## Status
Status: open (decide: accept per G2, or extend the erasure projections).
