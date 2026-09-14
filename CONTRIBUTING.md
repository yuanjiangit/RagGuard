# Contributing to RagGuard

Thanks for your interest! RagGuard is a young project — issues, discussions and PRs are all welcome.

## Ground rules

- **Code, comments, and APIs in English; tutorials/promotion in Chinese** (see project conventions below).
- **`ragguard-core` stays framework-free** — no Spring, no langchain4j, no LLM SDK dependencies. Framework integration belongs in the starter/adapter modules. This is a hard architectural boundary (see the risk plan in the project roadmap).
- **Metric implementations must be verifiable offline**: unit tests use fixed mock judge/embedding outputs, never live API calls. Live-model stability runs are separate, manual, and recorded in docs.
- Every metric score must be **explainable down to individual claim verdicts** — if your change breaks drill-down, it's a bug.

## Development

```bash
mvn verify          # full build + tests (JDK 17+)
mvn -pl ragguard-core test   # single module
```

Before opening a PR:

1. `mvn verify` passes locally.
2. New features come with tests.
3. Reference the [Ragas paper](https://arxiv.org/abs/2309.15217) and the design notes in `docs/design/` when touching metric logic — note in the PR description if you deviate and why.

## Commit style

Conventional Commits (`feat:`, `fix:`, `docs:`, `chore:` …). Keep commits atomic.

## License

By contributing you agree that your contributions are licensed under the Apache License 2.0.
