# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
during the 0.x phase with a "move fast" caveat: APIs may change between 0.x releases.

## [Unreleased]

### Added

- **Spring Boot starter (M2)**: `RagGuardAutoConfiguration` wires a Spring AI `ChatModel`-backed
  judge (`SpringAiJudge` — temperature 0, structured JSON output, tolerant parsing with
  markdown-fence stripping and one retry) and an embedding adapter; users only define their
  own `RagChain` bean. `ragguard.*` properties: test set location, metric subset, parallelism,
  report output dir. `RagGuardFacade` runs the evaluation.
- **HTML reports (M2)**: self-contained single-file reports (per-case scores, aggregates,
  drill-down) with run-over-run diff against a locally persisted `ragguard-latest-run.json`.
- **End-to-end example (M2)**: `examples/spring-ai-es-demo` — Spring AI + Elasticsearch vector
  retrieval, quality-gate test and a 20-run judge stability measurement, both gated behind
  environment variables so CI stays offline.
- **Release engineering (M2)**: Sonatype Central Portal publishing profile (`-P release` with
  sources/javadoc/GPG), release checklist in `docs/release.md`.
- M1: core metric engine (four Ragas-paper metrics, judge/embedding abstractions, offline
  unit tests) and JUnit 5 extension (`@RagTest`, `RagAssertions` with drill-down failures)
- M0: multi-module Maven skeleton, CI workflow, README (EN + zh-CN), design notes,
  Apache-2.0 LICENSE
