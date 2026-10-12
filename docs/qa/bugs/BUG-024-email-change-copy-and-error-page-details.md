# BUG-024: Email change screens: "1 tries left", and an error on /account drops the host warning

- **Severity:** cosmetic
- **Area:** `/account/email/confirm`, `POST /account/email` error path (`AccountRoutes.kt`)

## Steps to reproduce
A. Request a code for a new address, then enter a wrong code 4 times: the message reads "That code isn't right. 1 tries left." (login uses "1 more try", the wording helper `inWords`/singular is not used here).
B. As a host with an upcoming game day: `/account` shows "You host QA Today, ... It stays for the others ... Cancel it first". Now submit an invalid new email (`email=bad`): the page that comes back shows "That doesn't look like an email address." but the host warning in the "Delete my account" card is gone (`curl ... | grep -c 'You host'` gives 1 before, 0 after). The error path passes `emptyList()` for the hosted days.

## Expected
A: "1 try left". B: the same page as before plus the error.

## Status
Status: fixed.
