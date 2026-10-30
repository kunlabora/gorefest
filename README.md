# Boardfest

Helps a group of boardgamers pick the games they'll enjoy most at a game day. Everyone answers a handful of quick "which would you rather play?" questions, the app learns a taste profile per player, and then suggests the three best games for the people actually at a table.

Game data comes from [BoardGameGeek](https://boardgamegeek.com).

It's built for one friend group and runs as a private, self-hosted app: it tells search engines to stay away (`robots.txt`, `X-Robots-Tag`), everything except login and invite pages needs an account, and the admin approves every new account. You're welcome to run your own instance for your group. There's no public instance to sign up to.

The code is called `gorefest` (the game days it started with); the app calls itself Boardfest.

## How it works

1. A host creates a **game day** and shares an invite link.
2. Players log in with an email magic link or code, optionally link their BGG account, and tick the games they're bringing (the **shelf**).
3. A short **wizard** of pairwise comparisons builds each player's taste profile.
4. On the day, tick who's at the table and get **suggestions**, each with a one-line reason. At each checkpoint, log what was played and answer one more comparison.

## Stack

Kotlin (JDK 25), Ktor, kotlinx.html, htmx and hyperscript. Server-rendered, no SPA. Postgres with plain JDBC and Flyway. The state is an **event-sourced** log with Dynamic Consistency Boundaries: pure deciders and projections in `gorefest.core`, everything with IO in `gorefest.shell`.

[CLAUDE.md](CLAUDE.md) is the best description of the architecture, conventions and domain vocabulary, and works as the contributor guide for people and AI assistants alike.

## How it's built

Mostly by AI agents, following [Impatient Programming](https://impatientprogramming.org/) (IMP): Extreme Programming applied to agentic development. In this repo that means:

- **The Contract** ([docs/contract/](docs/contract)): what the app promises, as numbered obligations. Every obligation has a test tagged `@Obligation("GD-1")`, and the build fails when one is unproven.
- **Machines enforce the standards**: architecture rules (Konsist, `ArchitectureTest`) and detekt run in `./gradlew check` on every push. Mutation testing (`./gradlew mutationTest`) shows which tests actually catch anything.
- **Guardrails on the agent**: Claude Code hooks (`.claude/hooks/`) ask before a push, before changing a migration or a stored event type, and before dropping an obligation.
- **`fix:` and `refine:`**: a `fix:` commit is only for a defect in live production, and it starts with the obligation that was missing. Everything else is `refine:`.

## Run it locally

You need JDK 25 and a container runtime (Docker or Podman).

```bash
podman compose up -d
```
```bash
./gradlew buildFatJar && java --sun-misc-unsafe-memory-access=allow -jar build/libs/gorefest.jar
```

Then open http://localhost:8080. Dev mode (the default) sends login mail to an in-memory mailbox at `/dev/mailbox` and uses a fake BGG with a small catalogue, so you need no accounts or tokens.

## Test

```bash
./gradlew check
```

This runs the tests, the Contract coverage, the architecture rules and detekt. Tests start their own Postgres through Testcontainers, so only the container runtime has to be running.

## Documentation

- [CLAUDE.md](CLAUDE.md): architecture, conventions, taste model, matching
- [docs/contract/](docs/contract): the Contract, what the app promises, with its known gaps
- [docs/backlog.md](docs/backlog.md): milestones, story status and flow metrics
- [docs/design/](docs/design): design system, brand book, microcopy and screen mockups
- [deploy/](deploy): deployment. [scaleway.md](deploy/scaleway.md) is the step-by-step guide, [README.md](deploy/README.md) covers general setup, deploys and restores

## Deployment

One small VPS runs the fat jar under systemd, with Caddy in front for TLS and a nightly `pg_dump` to object storage. Every push to `main` is built, tested and deployed by GitHub Actions, and a release that doesn't come up healthy is rolled back. In a fork the deploy step skips itself until you set the `DEPLOY_*` secrets.

Running your own instance against the real BoardGameGeek needs your own `BGG_TOKEN`: since 2025 BGG's XML API only answers registered applications, so register one with BGG and follow their terms. Without a token the app uses a fake BGG.

## Licence

[MIT](LICENSE), for the code and docs. The fonts are under the SIL Open Font License ([docs/design/fonts/OFL.txt](docs/design/fonts/OFL.txt)), and the "Powered by BGG" logo and the BGG screenshot belong to BoardGameGeek.

Found a security problem? See [SECURITY.md](SECURITY.md).
