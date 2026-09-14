# Architecture

> RagGuard at a glance: a framework-free metric engine, thin framework adapters, and a JUnit 5 layer that turns evaluation into build gates.

## Module map

```
ragguard/
├── ragguard-core                    # EVERYTHING framework-free
│   ├── model/        RagChain, RagAnswer, RagTestCase, MetricResult, EvaluationReport …
│   ├── judge/        Judge, EmbeddingModel, JudgeCache (+ FileJudgeCache)
│   │   └── support/  JudgePrompts, JudgeResponseParser   ← shared by ALL adapters
│   ├── metric/       Metric + the four implementations
│   ├── engine/       EvaluationEngine (bounded-concurrency batch runner)
│   ├── generate/     TestSetGenerator, QuestionGenerator, TestSetWriter, Chunker
│   ├── testset/      TestSetLoader (YAML/JSON), TestSetFormatException
│   └── math/         CosineSimilarity
├── ragguard-junit5                  # @RagTest + RagAssertions (JUnit 5 only)
├── ragguard-report                  # HTML rendering + run history (Jackson only)
├── ragguard-spring-boot-starter     # Spring AI adapter + auto-configuration
├── ragguard-langchain4j             # langchain4j adapter
└── examples/
    ├── spring-ai-es-demo            # Spring Boot + ES vector store + quality gate
    └── langchain4j-demo             # no Spring: JUnit 5 extension route
```

**Hard boundary**: `ragguard-core` depends on nothing but SnakeYAML (YAML/JSON parsing for test sets and judge output). Every framework dependency — Spring, Spring AI, langchain4j, Jackson — lives in an adapter or optional module. This is what keeps RagGuard portable across framework release churn (see the risk plan in [design note 02](design/02-python-ecosystem-notes.md)).

## Core abstractions

| Interface | You implement it for | Used by |
|---|---|---|
| `RagChain` | **your RAG application** (the system under test) — `answer(question) → RagAnswer(answer, contexts)` | `EvaluationEngine` |
| `Judge` | an LLM judge — four structured calls (faithfulness, recall attribution, precision, reverse questions) | the four metrics |
| `EmbeddingModel` | an embedding model — cosine similarity for answer relevance, near-duplicate detection | `AnswerRelevanceMetric`, `TestSetGenerator` |
| `QuestionGenerator` | an LLM that produces QA pairs from source text | `TestSetGenerator` |
| `JudgeCache` | a store for raw judge responses (default: local YAML file) | both framework judges |

Adapters ship implementations of `Judge` and `EmbeddingModel` for their ecosystem (`SpringAiJudge`, `LangChain4jJudge`), all sharing the **same prompts and the same tolerant JSON parser** from `core.judge.support` — so scores are comparable across ecosystems.

## Evaluation data flow

```
ragguard-test-set.yml ──TestSetLoader──► List<RagTestCase>
                                              │
                        EvaluationEngine.run()│  (bounded concurrency, default 4)
                                              ▼
                                     RagChain.answer(q)          ← the SUT
                                              │
                        ┌─────────────────────┼──────────────────────┐
                        ▼                     ▼                      ▼
              FaithfulnessMetric    ContextRecallMetric    AnswerRelevanceMetric …
                        │  each calls Judge / EmbeddingModel (cacheable, mockable)
                        ▼
                 MetricResult (score + status + per-claim drill-down)
                        │
                        ▼
              EvaluationReport ──► RagAssertions (build gate)
                               └─► ReportWriter (HTML + latest-run.json + history.jsonl)
```

## Status semantics (why CI stays clean)

Every `MetricResult` carries a `MetricStatus`:

| Status | Meaning | Counts in aggregate mean? | Fails tests as |
|---|---|---|---|
| `OK` | valid score (judge may have been short-circuited with a meaningful 0) | yes | only when below threshold |
| `JUDGE_FAILED` | judge infrastructure problem (call failed / output unparsable after retry) | no | error-ish assertion, explicit |
| `SKIPPED` | required input missing (blank answer, no expected answer) | no | explicit assertion message |
| `ERROR` | unexpected exception (e.g. the RagChain itself crashed) | no | error |

A quality regression shows up as a **low score**; an environment problem shows up as **JUDGE_FAILED/ERROR**. Mixing these two is the classic trap that makes LLM-based CI gates untrustworthy — RagGuard refuses to blur them.

## Caching model

Raw judge responses are cached **before parsing**, keyed by `sha256(modelId, promptVersion, systemPrompt, userPrompt + variant)`:

- cache hits skip the model entirely (cost → 0 for unchanged items),
- only parse-successful raw text enters the cache — malformed responses are retried once and, if still unparsable, never cached,
- bumping `JudgePrompts.PROMPT_VERSION` invalidates everything (prompts are part of the key).

See [cost-estimation.md](cost-estimation.md) for the money math.

## Extending RagGuard

- **New metric**: implement `Metric` (stateless, thread-safe), add the judge interaction you need to `Judge` + the shared parser, unit-test it offline with a fixed mock judge (see `MockJudge` in core tests).
- **New framework adapter**: implement `Judge` (+ optional `EmbeddingModel` adapter) over your ecosystem's model interfaces — prompts and parsing come for free from `core.judge.support`.
- **Custom judge prompts**: implement `Judge` directly; the built-ins are open source in `JudgePrompts` precisely so they can be calibrated.

## Threading model

`EvaluationEngine` runs test cases concurrently on a fixed daemon-thread pool (`parallelism`, default 4; `1` = fully sequential). All `Judge` / `EmbeddingModel` / `RagChain` implementations must therefore be thread-safe — the built-in adapters are. Results are always returned in test-set order regardless of concurrency.
