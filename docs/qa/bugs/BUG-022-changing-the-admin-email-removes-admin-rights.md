# BUG-022: The admin changes their email on /account and loses admin rights; the old address becomes the admin address for whoever signs up with it

- **Severity:** minor (admin-only, recoverable by changing back, but a trap)
- **Area:** `/account/email` x `ADMIN_EMAIL` (admin check and auto-approval are by email address)
- **Environment:** `ADMIN_EMAIL=tim@example.com`

## Steps to reproduce
1. As the admin (tim@example.com) change the email to `tim2@example.com` on `/account` and confirm the code.
2. Open `/admin/requests` and `/admin/approvals`.
3. Check the old address: `select email, approved_at from players` shows only `tim2@example.com`.

## Expected
Either the admin is refused/warned ("this address is the admin address"), or admin follows the person.

## Actual
Both admin pages answer 404 straight away, the session stays valid, there is no warning on the page, and nobody can approve new accounts or handle requests until the address is changed back. `tim@example.com` is now free: anyone who signs up with it (needs only that inbox) is auto-approved and is the admin ("the admin's own email is approved at first login"). I changed it back to tim@example.com (code mail) and the admin pages returned 200 again.

## Evidence
```
POST /account/email email=tim2@example.com ; POST /account/email/confirm code=... -> /account?emailChanged=1
GET /admin/requests -> 404 ; GET /admin/approvals -> 404
```

## Suspected cause (guess)
`admin { }` compares the current player's email with `config.adminEmail`; `EmailChange` has no notion of it.

## Status
Status: fixed.
