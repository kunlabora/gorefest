# BUG-032: A name ending in a backslash breaks the admin's "Delete account" confirm, so one click erases the account

- **Severity:** minor (admin-only and needs an odd name, but the action can't be undone)
- **Area:** `/admin/requests` (`AccountViews.kt`, the hyperscript confirm)

## Steps to reproduce
1. Sign up as `Zed\` (a name ending in a backslash), get approved, and on /account ask "Delete my account".
2. As the admin, open /admin/requests and click "Delete account" on Zed's row.

## Expected
"Delete Zed\'s account for good?" confirm, as for any other name.

## Actual
The button's script is `on submit if not confirm('Delete Zed\\'s account for good?') halt the event`: the doubled backslash ends the string early. The console shows `hyperscript errors were found … Unterminated string at [Line: 1, Column: 58]`, no handler is installed, and the click submits straight away: Zed was erased with no confirm. (Check: a working confirm, "Take off" on a poll game, was dismissed by the same browser and posted nothing.)

## Evidence
`screenshots/BUG-032-admin-requests-backslash-name.png` (the row); the console error above; server log `Erased account …` right after the click; `select count(*) from players where name like 'Zed%'` = 0.

## Suspected cause (guess)
The name is put into a single-quoted hyperscript string with only `'` removed (`replace("'", "")`); a backslash isn't escaped. The poll's "Take CATAN off the poll?" confirm uses the same pattern with game names (only a problem for a name with a backslash).

## Status
Status: open
