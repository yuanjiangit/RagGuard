# RagGuard

**Stop guessing. Regression-test your RAG.**

[中文文档](README.zh-CN.md) | [设计笔记（指标算法）](docs/design/01-ragas-metrics-notes.md)

> RagGuard is a quality-assurance framework for RAG applications on the JVM: it turns RAG evaluation into unit tests, so a quality regression blocks the merge — instead of being discovered weeks later by user complaints.

**Status: 🚧 M1 — core metric engine implemented.** All four Ragas-paper metrics, the judge/embedding abstractions, YAML test sets and the JUnit 5 extension are in `ragguard-core` / `ragguard-junit5`, fully offline-verifiable. Spring Boot starter and HTML reports land in M2 (0.1.0). Watch/star to follow along.

## Why

The Python world has Ragas / DeepEval / TruLens. The Java world has frameworks to *build* RAG applications (Spring AI, langchain4j) — but nothing to *verify* them. When a team swaps the embedding model, changes the chunking strategy, or tweaks a prompt, the quality delta today is measured by vibes.

RagGuard closes that gap:

- **4 core metrics**, following the [Ragas paper](https://arxiv.org/abs/2309.15217) definitions: faithfulness, answer relevance, context recall, context precision
- **LLM-as-Judge** with temperature 0, structured (JSON schema) output, optional majority voting, and per-claim explainability — every score drills down to individual claim verdicts
- **JUnit 5 extension** — write evaluation as `@RagTest`; the build fails when a score drops below threshold
- **Spring Boot starter** — auto-configuration for Spring AI; plug in your own RagChain
- **HTML reports** — per-question scores, overall score, diff against the previous run, CI-friendly
- **Cost control** — judge verdicts cached locally by (input hash, model, prompt version)

## Modules

| Module | Purpose |
|---|---|
| `ragguard-core` | Metric engine, test-set model, judge abstraction — framework-free |
| `ragguard-junit5` | `@RagTest` / `RagAssertions` JUnit 5 extension |
| `ragguard-spring-boot-starter` | Spring AI auto-configuration |
| `ragguard-report` | Self-contained HTML reports |

## Roadmap

| Milestone | Scope |
|---|---|
| **M0** (weeks 1–2) | Repository bootstrap, design notes, CI — *in progress* |
| **M1** (weeks 3–6) | Core metric engine + judge abstraction + offline unit tests |
| **M2** (weeks 7–10) | Spring Boot starter, HTML reports, end-to-end example, 0.1.0 release |
| **M3** (weeks 11–14) | Test-set auto-generation, GitHub Action, langchain4j adapter |
| **M4** (weeks 15–16) | Report enhancements, docs, polish — 0.3.0 |

## Building from source

```bash
mvn verify
```

Requires JDK 17+. CI runs the same command on every push and PR.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Design discussions live in [docs/design/](docs/design/).

## License

[Apache License 2.0](LICENSE)
