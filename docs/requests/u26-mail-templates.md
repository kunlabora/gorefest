# U26 Mails that look like Boardfest

Source: Tim, 2026-11-20. Status: 📋 todo

**Idea:** improve the mails Boardfest sends, or at least style them, so they look like the app and read well on a
phone.

**Today:** every mail is plain text: `Mail(to, subject, text, sentAt)` in `shell.auth.Mailer`, and the three providers
(`ScalewayMailer`, `PostmarkMailer`, `ResendMailer` in `HttpMailers.kt`) send only a text body. Each sender builds its
text inline with string templates:

- the login code and magic link (`Login`)
- the email change code and the notice to the old address (`EmailChange`)
- account requests: data export and deletion confirmations (`AccountRequests`)
- the rejection mail to a newcomer (`Newcomers`)
- the inactive-account warning and deletion notice (`InactiveAccounts`)
- the game day recap (`RecapMailer`, wording in `Recap`)
- the admin notice when Discord isn't set (`AdminNotifier`)

Greetings, sign-offs and links differ from mail to mail, and some text sits on one long line.

## Behaviour (draft)

1. Every mail has an HTML part in Boardfest's look (colours, wordmark, type from the brand book; `docs/design/`), next
   to a plain-text part that says the same thing (multipart/alternative), so text-only clients and previews still work.
2. One layout for all mails: wordmark header, a heading, the body, one clear button for the main link, and a footer
   (why you got this, link to Boardfest, Privacy).
3. The login mail keeps the code in the subject and shows it big and easy to copy in the body; the link button sits
   under it.
4. The recap shows the plays as a small table or list with game, players and minutes.
5. Wording follows the brand book's voice and `docs/design/microcopy.md`; each mail's text is reviewed once.
6. Works in the common clients (Gmail web and app, Apple Mail, Outlook) in light and dark mode; no remote images or
   fonts (inline or attached logo only, system fonts), so nothing calls a third party and nothing is blocked.
7. Readable by screen readers (headings, link text that says where it goes, `lang`, enough contrast), in line with
   milestone 3.

## Design notes

- `Mail` gains an optional `html` (default null), the providers send it when present (`HtmlBody` / `html` / `html`).
  The dev mailbox (`/dev/mailbox`) shows the HTML version too.
- Templates in `shell` (built with kotlinx.html, which escapes everything, like the views), one function per mail
  returning both parts, so the senders stop building text inline. Display text stays out of the IO, like `Recap`.
- Tests pin each mail's subject and wording, and parse the HTML with Jsoup like the web tests.
- Could be split: (a) the shared layout plus the login mail, (b) the rest. U19's mail would use the layout.

## Open questions

- Full HTML, or just tidy the plain text (consistent greeting, sign-off, line breaks, link placement) as a smaller
  first step?
- The logo: inline SVG doesn't work in Gmail; an embedded PNG (CID attachment) or a text wordmark?
