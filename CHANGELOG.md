# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
during the 0.x phase with a "move fast" caveat: APIs may change between 0.x releases.

## [Unreleased]

### Added

- **Test-set generation (M3)**: `TestSetGenerator` / `TestSetWriter` in core —
  paragraph-boundary chunking, LLM QA generation with grounding constraints,
  two-level dedup (normalized text + optional embedding near-duplicate),
  human-confirmation YAML checklist that round-trips through `TestSetLoader`.
  `SpringAiQuestionGenerator` auto-configured in the starter.
- **langchain4j adapter (M3)**: `ragguard-langchain4j` module with
  `LangChain4jJudge` + `LangChain4jEmbeddingAdapter`; judge prompts and JSON
  parsing moved into core `judge.support` so both adapters behave identically.
  New `examples/langchain4j-demo` (no-Spring evaluation via the JUnit 5 extension).
- **GitHub Action (M3)**: `action/` composite action — runs the evaluation
  build, posts a Markdown metric summary as a PR comment and job summary.
- **Spring Boot starter (M2)**: `RagGuardAutoConfiguration` wires a Spring AI `ChatModel`-backed
  judge (temperature 0, structured JSON output, tolerant parsing with retry) and an
  embedding adapter; users only define their own `RagChain` bean. `ragguard.*` properties:
  test set location, metric subset, parallelism, report output dir. `RagGuardFacade` runs
  the evaluation.
- **HTML reports (M2)**: self-contained single-file reports (per-case scores, aggregates,
  drill-down) with run-over-run diff against a locally persisted `ragguard-latest-run.json`.
- **End-to-end example (M2)**: `examples/spring-ai-es-demo` — Spring AI + Elasticsearch vector
  retrieval, quality-gate test and a 20-run judge stability measurement, both gated behind
  environment variables so CI stays offline.
- **Release engineering (M2)**: Sonatype Central Portal publishing profile (`-P release` with
  sources/javadoc/GPG), release checklist in `docs/release.md`.
- **Core metric engine (M1)**: four Ragas-paper metrics, judge/embedding abstractions,
  `EvaluationEngine` with bounded concurrency, `MetricResult` with per-claim drill-down,
  YAML/JSON test-set loader, offline unit tests with fixed mocks
- **JUnit 5 extension (M1)**: `@RagTest` + supplier annotations, report parameter injection,
  `RagAssertions` with drill-down failure messages
- M0: multi-module Maven skeleton, CI workflow, README (EN + zh-CN), design notes,
  Apache-2.0 LICENSE
