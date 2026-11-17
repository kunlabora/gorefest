# U24 Release notes

Source: Tim, 2026-11-17. Status: ✅ built

**Idea:** players see what changed in Boardfest: what's new, what works differently, and what got fixed, per version.

**Today:** the footer says "Boardfest v2.4.1" (from `version.properties`, filled by Gradle) and links nowhere. What
changed lives only in git: each release is an annotated tag with a one-line summary (`Boardfest 2.4.1: …`, written by
the `release` skill), and the commits under it (`refine:`/`fix:`) mix player-facing changes with design docs, privacy
checks, "breathe out" tidy-ups and developer tooling. No events are involved.

**Vocabulary:** **Release notes**: per released version, its date and what's *new*, *changed* and *fixed*, in players'
words.

## Behaviour

1. The footer's "Boardfest v2.4.1" becomes a link to **What's new** (`/release-notes`), on every page, signed-out ones
   included (like Privacy).
2. The page lists the versions newest first. Each has its number and date as a heading, then up to three short lists:
   **New**, **Changed**, **Fixed**. A list with nothing in it isn't shown.
3. Lines are plain sentences about what a player notices ("You can mark the games you can explain"), not story ids,
   bug numbers or developer work (the Contract, Gradle tasks, the onboarding site).
4. The version the footer shows always has its notes: a release without them fails the build.

## Design

**Events:** none. **Deciders:** none. **Matcher:** no change.

**Source of the notes:** a hand-written file, `src/main/resources/release-notes.md`, in a strict shape so it reads well
on GitHub too:

```markdown
## 2.4.1 · 2026-11-14

### Fixed

- Login codes now work for an hour, so a slow mail still arrives in time.
```

`## <version> · <yyyy-mm-dd>`, then `### New` / `### Changed` / `### Fixed`, each followed by `- ` lines. Inline
markup: none (plain text; a stray `*` shows as is). Anything else is a parse error, caught by a test, not at runtime.

The `release` skill gets a step: before the version commit, write the new version's section from the commits since the
previous tag (player-facing ones only), in the same commit as the version bump.

**Read model:** none in the core (it isn't domain state). `shell.web.releasenotes.ReleaseNotes` parses the file once at
startup into `List<Release(version, date, new, changed, fixed)>`; the parser is pure and tested on its own.

**Web** (new package `web.releasenotes`):

- `GET /release-notes`: `releaseNotesPage(view)`, public, `page(site = …)` for the signed-in (`Nav` none) or the
  wordmark bar for the signed-out, as `/privacy` does.
- Built from existing components: `pageHeading("What's new")`, per release a `sectionHeading` with version and date
  (`<time datetime>`), `heading(Level.H3, "New" | "Changed" | "Fixed")` and a plain list. New CSS only in a
  `release-notes` screen block, if any.
- Footer: `Boardfest v…` wrapped in a link to `/release-notes`.

**Privacy:** nothing about people; no change to `docs/gdpr.md` or `/privacy` expected (privacy-check confirms).

## Open questions

1. **Decided (2026-11-17): as recommended.** **Hand-written notes or generated from git?** Generated would be free but noisy: half the commits are design docs,
   privacy checks and tidy-ups, and the sentences are written for developers. Recommendation: hand-written in
   `release-notes.md`, drafted by the `release` skill from the commits and checked by whoever releases.
2. **Decided (2026-11-17): as recommended.** **Public or signed-in only?** Recommendation: public, like `/privacy`: it holds nothing about people, the footer link
   shows on signed-out pages, and `robots.txt`/`noindex` still keep it out of search.
3. **Decided (2026-11-17): as recommended.** **How far back?** Recommendation: backfill 2.0.0 (the redesign) to 2.4.1 from the tags and commits, plus one line for
   1.0 ("The first Boardfest: game days, invites, the taste wizard and suggestions"). What's on main since 2.4.1 (U21,
   U22, U23) goes in with the next release.
4. **Decided (2026-11-17): as recommended.** **"New since your last visit" dot on the app bar?** Recommendation: not now; it needs a per-player "last seen
   version". A later story if people miss the page.
5. **Decided (2026-11-17): as recommended.** **Name.** Recommendation: page title and heading **What's new**, path `/release-notes`.

## Criteria

- [x] WEB-17: The footer's version, on every page, links to the release notes.
- [x] WEB-18: The release notes list every version newest first, each with its date and its new, changed and fixed
      lines; an empty kind isn't shown.
- [x] WEB-19: The version the footer shows has its release notes.
- [x] WEB-20: The release notes are readable signed out.
- [x] The parser rejects a file that strays from the shape (unknown heading, line outside a kind, bad date), with the
      line number.
- [x] Backfilled notes for 2.0.0–2.4.1 and 1.0, in players' words.
- [x] The `release` skill writes the new version's notes in the version commit.
- [x] Design mockup in `docs/design/mockups/screen-release-notes.html` and the screen uses existing components; new
      sentences in `docs/design/microcopy.md`.
- [x] No events: event evolution and erasure unaffected; `CLAUDE.md` lists `web.releasenotes`.

Built as designed, with these notes:

- Signed-in readers get the site bar (no nav item marked), signed-out readers the wordmark bar; the design said so, the first build missed it and review caught it.
- Dates show as on the rest of the app ("Fri 13 Nov 2026"); the kinds are small uppercase kickers.
- The parser also refuses a version with no lines and a version listed twice.
- The page shares the privacy notice's reading column (`.gf-reading` in the shared CSS).
