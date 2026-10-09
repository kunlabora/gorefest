# BUG-001: Double-tapping "Create game day" creates two identical game days

- **Severity:** minor
- **Area:** Game day creation (`/gamedays/new`), probably every htmx form that ends in `HX-Redirect` (also "Start the poll")
- **Environment:** desktop 800x600 (Browser pane), dark and light theme, player Tim (host). Server on localhost, so the response is very fast.

## Steps to reproduce
1. Log in as Tim (`/dev/mailbox` for the code). No other state needed.
2. Open `http://localhost:8099/gamedays/new`.
3. Set Title to `Double Click Day` and Date to any future date (e.g. 26 Dec 2026).
4. Double-click the orange "Create game day" button (real `double_click`; a script calling `button.click()` twice does the same).
5. Open `http://localhost:8099/` and look at "Coming up".

## Expected
One game day is created; the second click is ignored or the button is disabled while the request is in flight.

## Actual
Two game days with the same title/date/host exist, each with its own id and its own invite link. Same happened with a scripted double click for "Tile Night". There is no way to delete one, only cancel it.

## Evidence
Titles of every game day linked from the home page afterwards:

```
2 <title>Double Click Day
2 <title>Tile Night
```

No screenshot (nothing visual to show but two identical rows).

## Suspected cause (guess)
No form uses `hx-disabled-elt` or `hx-sync` (`grep -rn "disabled-elt\|hx-sync" src/main/kotlin` finds nothing). htmx's default is to queue the last trigger while a request is in flight, so the second submit fires right after the first answered, before the `HX-Redirect` navigation lands. `POST /gamedays` has no idempotency check either (`GameDayRoutes.kt`, `post("/gamedays")` mints a fresh id per request). Slower networks may hide it, but the queued request should still fire.
