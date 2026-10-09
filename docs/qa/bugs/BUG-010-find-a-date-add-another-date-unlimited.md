# BUG-010: "Add another date" has no limit, the server only complains after submit (max 14)

- **Severity:** cosmetic
- **Area:** Date poll form (`/polls/new`)
- **Environment:** desktop, light theme, Tim.

## Steps to reproduce
1. Open `http://localhost:8099/polls/new`.
2. Click "Add another date" 15 times (the form starts with 3 fields).
3. Fill any two and submit (or just inspect the DOM).

## Expected
The button disappears or disables at the maximum (14, `MAX_POLL_DATES`), like the rule the server enforces.

## Actual
18 date fields are rendered and the button stays. Submitting 40 dates is answered with `That's a lot of dates: keep it to 14` only after the round trip.

## Evidence
Scripted click loop: date input count grew `4,5,...,18`, button still in the DOM.

## Suspected cause (guess)
`GET /polls/new/date` (`DatePollRoutes.kt`) always returns another `dateField()`; it does not know how many exist. The add button could carry the count or be swapped out at the limit.
