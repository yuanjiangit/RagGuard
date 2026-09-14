# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html)
during the 0.x phase with a "move fast" caveat: APIs may change between 0.x releases.

## [Unreleased]

### Added

- **Core metric engine (M1)**, offline-verifiable with fixed mock judge/embedding outputs:
  - Four Ragas-paper metrics: faithfulness, answer relevance, context recall, context precision
  - `Judge` / `EmbeddingModel` abstractions — `ragguard-core` stays framework-free
  - `EvaluationEngine` with bounded-concurrency batch evaluation (default 4), order-preserving
  - `EvaluationReport` with per-metric aggregates and overall score; every score carries
    per-claim / per-context drill-down detail
  - YAML (and JSON) test-set loader (`TestSetLoader`, schema: `question` + `expectedAnswer` + `expectedContexts`)
- **JUnit 5 extension (M1)**: `@RagTest` + `@RagChainSupplier` / `@RagJudgeSupplier` /
  `@RagEmbeddingSupplier`, evaluation-report parameter injection, and `RagAssertions`
  (`assertMetricAtLeast` / `assertCaseMetricAtLeast` / `assertScoreAtLeast`) with
  drill-down failure messages
- M0: multi-module Maven skeleton, CI workflow, README (EN + zh-CN), design notes,
  Apache-2.0 LICENSE
