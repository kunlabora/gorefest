# Gorefest

Helps a group of boardgamers pick the games they'll enjoy most at a game day. Everyone answers a handful of quick "which would you rather play?" questions, the app learns a taste profile per player, and then suggests the three best games for the people actually at a table.

Game data comes from [BoardGameGeek](https://boardgamegeek.com).

It's a private app for one friend group. It tells search engines to stay away (`robots.txt`, `X-Robots-Tag`), and everything except login and invite pages needs an account.

## How it works

1. A host creates a **game day** and shares an invite link.
2. Players log in with an email magic link or code, optionally link their BGG account, and tick the games they're bringing (the **shelf**).
3. A short **wizard** of pairwise comparisons builds each player's taste profile.
4. On the day, tick who's at the table and get **suggestions**, each with a one-line reason. At each checkpoint, log what was played and answer one more comparison.

## Stack

Kotlin (JDK 25), Ktor, kotlinx.html, htmx and hyperscript. Server-rendered, no SPA. Postgres with plain JDBC and Flyway. The state is an **event-sourced** log with Dynamic Consistency Boundaries: pure deciders and projections in `gorefest.core`, everything with IO in `gorefest.shell`.

[CLAUDE.md](CLAUDE.md) is the best description of the architecture, conventions and domain vocabulary, and works as the contributor guide for people and AI assistants alike.

## Run it locally

You need JDK 25 and a container runtime (Docker or Podman).

```bash
podman compose up -d
```
```bash
./gradlew buildFatJar && java -jar build/libs/gorefest.jar
```

Then open http://localhost:8080. Dev mode (the default) sends login mail to an in-memory mailbox at `/dev/mailbox` and uses a fake BGG with a small catalogue, so you need no accounts or tokens.

## Test

```bash
./gradlew test
```

Tests start their own Postgres through Testcontainers, so only the container runtime has to be running.

## Documentation

- [CLAUDE.md](CLAUDE.md): architecture, conventions, taste model, matching
- [docs/backlog.md](docs/backlog.md): stories and their status
- [docs/design/](docs/design): design system, brand book, microcopy and screen mockups
- [deploy/](deploy): deployment. [scaleway.md](deploy/scaleway.md) is the step-by-step guide, [README.md](deploy/README.md) covers general setup, deploys and restores

## Deployment

One small VPS runs the fat jar under systemd, with Caddy in front for TLS and a nightly `pg_dump` to object storage. Every push to `main` is built, tested and deployed by GitHub Actions, and a release that doesn't come up healthy is rolled back.
