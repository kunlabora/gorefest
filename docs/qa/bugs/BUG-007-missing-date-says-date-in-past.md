# BUG-007: A missing or unparseable date is reported as "That date is in the past"

- **Severity:** cosmetic
- **Area:** Game day create/edit (server-side validation)
- **Environment:** any viewport; reached by a client without native date validation (the browser's `required` date input normally stops this), or a stale/odd client posting the form.

## Steps to reproduce
1. Log in as Tim.
2. `curl -b tim.jar -H 'HX-Request: true' -d 'title=T&place=x' http://localhost:8099/gamedays` (no date), and again with `date=garbage`.

## Expected
A message about the date being missing or not understood ("Pick a date").

## Actual
Both answer `That date is in the past` (also when editing, same parser).

## Evidence
```
-d "title=T&date=garbage&place=x"  ->  <p class="gf-error" role="alert">... That date is in the past
-d "title=T&place=x"               ->  <p class="gf-error" role="alert">... That date is in the past
```

## Suspected cause (guess)
`GameDayRoutes.kt` line ~201: `private fun Parameters.date()` falls back to `LocalDate.MIN`, which the decider then rejects as `DateInPast`. Needs its own rejection (or a null date) so the sentence is truthful.
