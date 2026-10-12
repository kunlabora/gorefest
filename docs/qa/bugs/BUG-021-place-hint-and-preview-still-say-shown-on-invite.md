# BUG-021: The game day form still says the place is "Shown on the invite", and its invite preview shows the place

- **Severity:** minor (misleading copy; the server itself behaves correctly)
- **Area:** `/gamedays/new` and `/gamedays/{id}/edit` (`GameDayFormViews.kt` line 90 and the `#invite-preview` card)

## Steps to reproduce
1. As a host open `/gamedays/new` (or the edit page of a game day that has a place).
2. Read the hint under "Where"; look at the live invite preview card.

## Expected
G5 says the place appears only for people who joined. The date poll form already says "Only people who have joined see it. Leave out the house number if you like." The game day form should say the same, and the preview should not show the place on the "invitee" card (or label it as members-only).

## Actual
Hint: "Shown on the invite. Leave out the house number if you like." The preview card ("Tim invites you to a game day / QA Day One / Saturday 24 October / Zebrastraat 43 Secretville / Hosted by Tim") shows the place as if the invitee saw it. In reality `/i/{token}`, its og:* tags and og.png contain no place until the visitor joined (verified), so the host is told something false and may leave out the address for nothing, or think a preview shows more than it does.

## Evidence
```
$ curl -b tim.jar /gamedays/new | grep -i 'shown on the invite'
Shown on the invite. Leave out the house number if you like.
$ curl -b tim.jar /polls/new | grep -i 'house number'
Only people who have joined see it. Leave out the house number if you like.
$ curl /i/{token} | grep -ci zebra     -> 0
```

## Status
Status: fixed.
