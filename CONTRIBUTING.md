# Contributing to RagGuard

Thanks for your interest! RagGuard is a young project — issues, discussions and PRs are all welcome.

## Ground rules

- **Code, comments, and APIs in English; tutorials/promotion in Chinese** (see project conventions below).
- **`ragguard-core` stays framework-free** — no Spring, no langchain4j, no LLM SDK dependencies. Framework integration belongs in the starter/adapter modules. This is a hard architectural boundary (see the risk plan in the project roadmap).
- **Metric implementations must be verifiable offline**: unit tests use fixed mock judge/embedding outputs, never live API calls. Live-model stability runs are separate, manual, and recorded in docs.
- Every metric score must be **explainable down to individual claim verdicts** — if your change breaks drill-down, it's a bug.

## Development

```bash
./mvnw verify        # full build + tests (JDK 17+; on Windows set JAVA_HOME to a JDK 17+ first)
./mvnw -pl ragguard-core test   # single module
```

Before opening a PR:

1. `./mvnw verify` passes locally.
2. New features come with **offline unit tests** — fixed mock judge/embedding outputs, never live API calls.
3. Reference the [Ragas paper](https://arxiv.org/abs/2309.15217) and the design notes in `docs/design/` when touching metric logic — note in the PR description if you deviate and why.

## Documentation map

| Doc | When to read/update |
|---|---|
| [docs/architecture.md](docs/architecture.md) | before changing module boundaries or core abstractions |
| [docs/metrics.md](docs/metrics.md) | when changing metric semantics — keep the user-facing summary in sync |
| [docs/test-set-format.md](docs/test-set-format.md) | when touching `TestSetLoader` / `TestSetWriter` |
| [docs/configuration.md](docs/configuration.md) | when adding properties or extension options |
| [docs/design/](design/) | before deviating from the documented algorithm decisions |

Language convention: code, comments and API docs in English; tutorials and promotion in Chinese (see `docs/zh/`).

## Commit style

Conventional Commits (`feat:`, `fix:`, `docs:`, `chore:` …). Keep commits atomic.

## License

By contributing you agree that your contributions are licensed under the Apache License 2.0.
