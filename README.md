# RagGuard

**Stop guessing. Regression-test your RAG.**

[中文文档](README.zh-CN.md) | [设计笔记（指标算法）](docs/design/01-ragas-metrics-notes.md)

> RagGuard is a quality-assurance framework for RAG applications on the JVM: it turns RAG evaluation into unit tests, so a quality regression blocks the merge — instead of being discovered weeks later by user complaints.

**Status: 🚧 M4 — polish & hardening.** Judge response caching (cost lever), metric trend charts in HTML reports, Chinese docs, cost estimation — all in; the release checklist for 0.1.0 is in [docs/release.md](docs/release.md). Watch/star to follow along.

**Docs:** [Quick start](#quick-start-3-steps) · [中文快速上手](docs/zh/getting-started.md) · [Cost estimation](docs/cost-estimation.md) · [Design notes](docs/design/) · [GitHub Action](action/README.md)

## Quick start (3 steps)

**1. Add the starter:**

```xml
<dependency>
  <groupId>com.aizerohub.ragguard</groupId>
  <artifactId>ragguard-spring-boot-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

**2. Define your chain as a bean and a test set** (`ragguard-test-set.yml`):

```yaml
testCases:
  - id: q1
    question: What is RagGuard?
    expectedAnswer: A RAG evaluation framework for the JVM.
```

**3. Write the regression test:**

```java
@SpringBootTest
class MyRagRegressionTest {
    @Autowired RagGuardFacade ragGuard;

    @Test
    void qualityGate() {
        RagAssertions.assertMetricAtLeast(ragGuard.run(), MetricType.FAITHFULNESS, 0.8);
    }
}
```

Set `ragguard.report-output-dir` and every run also writes a self-contained HTML report with a diff against the previous run. See [examples/spring-ai-es-demo](examples/spring-ai-es-demo) for a full Spring AI + Elasticsearch application.

**Don't want to hand-write test cases?** Generate candidates from your documents (LLM + dedup, human-confirmed):

```java
TestSetGenerator generator = TestSetGenerator.builder(new SpringAiQuestionGenerator(chatModel)).build();
List<GeneratedTestCase> candidates = generator.generate(Map.of("docs.md", documentText));
Files.writeString(Path.of("candidates.yml"), TestSetWriter.toYaml(candidates)); // review, then use
```

Run RagGuard on every pull request with the ready-made GitHub Action ([action/](action/README.md)) — it fails the PR on quality regressions and comments the metric summary.

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
| `ragguard-core` | Metric engine, test-set model, judge abstraction, test-set generation — framework-free |
| `ragguard-junit5` | `@RagTest` / `RagAssertions` JUnit 5 extension |
| `ragguard-spring-boot-starter` | Spring AI auto-configuration (judge, embedding, test-set generator) |
| `ragguard-langchain4j` | langchain4j adapter (judge + embedding) |
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
