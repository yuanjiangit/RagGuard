# Configuration Reference

> All `ragguard.*` properties (Spring Boot starter) and the JUnit 5 extension options.

## Spring Boot starter properties (`application.yml`)

| Property | Default | Description |
|---|---|---|
| `ragguard.enabled` | `true` | Master switch; `false` removes all RagGuard beans |
| `ragguard.test-set` | `ragguard-test-set.yml` | Test set location — classpath resource (optionally `classpath:`-prefixed) or file path relative to the working directory |
| `ragguard.metrics` | all four | Metric subset; comma-separated names: `FAITHFULNESS`, `ANSWER_RELEVANCE`, `CONTEXT_RECALL`, `CONTEXT_PRECISION` |
| `ragguard.parallelism` | `4` | Bounded concurrency for batch evaluation (`1` = sequential) |
| `ragguard.report-output-dir` | *(unset → reporting off)* | Directory for HTML reports and run summaries |
| `ragguard.report-title` | `RagGuard Evaluation Report` | Title shown in the HTML report |
| `ragguard.judge-cache-enabled` | `false` | Cache raw judge responses keyed by (model, prompt version, input hash) |
| `ragguard.judge-cache-file` | `ragguard-judge-cache.yml` | Cache file location; don't commit it (already in .gitignore) |
| `ragguard.judge-model-id` | `default` | Model identity in the cache key — set it to the judge model name (e.g. `gpt-4o-mini`) so switching judges invalidates the cache |

Notes:

- The auto-configuration activates when a Spring AI `ChatModel` bean exists. It registers a `SpringAiJudge`, a `SpringAiQuestionGenerator`, and — when you additionally define a `RagChain` bean — the `RagGuardFacade` entry point.
- `ANSWER_RELEVANCE` requires an embedding model: a Spring AI `EmbeddingModel` bean is adapted automatically; if none exists and no metric subset was requested, answer relevance is dropped (with a clear error if it was *explicitly* requested).
- All beans are `@ConditionalOnMissingBean` — define your own `Judge` / `QuestionGenerator` / `RagGuardFacade` to take over any layer.
- Provide your judge model with **temperature 0** (the starter also passes temperature 0 runtime options per call).

## JUnit 5 extension

```java
@RagTest(testSet = "rag-test-set.yml")
class MyRagRegressionTest {
    @RagChainSupplier static RagChain chain() { … }      // required
    @RagJudgeSupplier static Judge judge() { … }         // required
    @RagEmbeddingSupplier static EmbeddingModel emb() {…} // optional (answer relevance)

    @Test
    void quality(EvaluationReport report) { … }          // report injected per class
}
```

| Element | Default | Description |
|---|---|---|
| `@RagTest#testSet` | `ragguard-test-set.yml` | Classpath resource or file path |
| `@RagChainSupplier` / `@RagJudgeSupplier` / `@RagEmbeddingSupplier` | — | Static, zero-argument methods; evaluated once per class; missing suppliers fail with an explicit message |

## Report output files (`ragguard.report-output-dir`)

| File | Content |
|---|---|
| `ragguard-report-<yyyyMMdd-HHmmss>.html` | Self-contained report: aggregates, per-case table, drill-down, trend SVG (from the 2nd run) |
| `ragguard-latest-run.json` | Previous run's summary — the diff baseline |
| `ragguard-history.jsonl` | Append-only history (one line per run) — the trend data source |

## GitHub Action inputs

See [action/README.md](../action/README.md): `java-version`, `maven-command`, `report-dir`, `comment-on-pr`.

## Programmatic usage (no Spring, no JUnit)

```java
EvaluationEngine engine = EvaluationEngine.builder()
        .chain(myRagChain)
        .judge(new LangChain4jJudge(chatModel, 3,
                new FileJudgeCache(Path.of("ragguard-judge-cache.yml")), "gpt-4o-mini"))
        .embedding(text -> …)          // optional
        .metrics(MetricType.FAITHFULNESS, MetricType.CONTEXT_RECALL)
        .parallelism(4)
        .build();
EvaluationReport report = engine.run(TestSetLoader.loadYaml(Path.of("test-set.yml")));
```
