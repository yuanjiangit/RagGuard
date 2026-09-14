# Example: Spring AI + Elasticsearch

End-to-end RagGuard demo (M2): a Spring Boot app that retrieves from an Elasticsearch vector store and generates answers with Spring AI, evaluated by the RagGuard Spring Boot starter.

## What's inside

| File | Role |
|---|---|
| [`DemoRagChain.java`](src/main/java/com/aizerohub/ragguard/example/DemoRagChain.java) | **The system under test**: top-3 vector search → ChatClient answer. Implements RagGuard's `RagChain`. |
| [`KnowledgeBaseIngester.java`](src/main/java/com/aizerohub/ragguard/example/KnowledgeBaseIngester.java) | One-time ingestion of `src/main/resources/docs/*.md` (activate with the `ingest` profile) |
| [`ragguard-test-set.yml`](src/main/resources/ragguard-test-set.yml) | 5-question test set about the knowledge base |
| [`RagEvaluationTest.java`](src/test/java/com/aizerohub/ragguard/example/RagEvaluationTest.java) | The quality gate: runs the evaluation, asserts metric thresholds |
| [`JudgeStabilityTest.java`](src/test/java/com/aizerohub/ragguard/example/JudgeStabilityTest.java) | 20-run variance measurement (M2 stability data; one-off, manual) |
| [`application.yml`](src/main/resources/application.yml) | `ragguard.*` + Spring AI configuration |

## Running it

Prerequisites: `OPENAI_API_KEY` environment variable and a running Elasticsearch (default `localhost:9200`).

```bash
# 1. ingest the knowledge base (one-time)
export OPENAI_API_KEY=sk-...
mvn -pl examples/spring-ai-es-demo spring-boot:run -Dspring-boot.run.profiles=ingest

# 2. run the quality gate (writes reports to ragguard-reports/)
mvn -pl examples/spring-ai-es-demo test -Dtest=RagEvaluationTest

# 3. (one-off) measure judge variance across 20 full runs
RAGGUARD_STABILITY_RUN=true mvn -pl examples/spring-ai-es-demo test -Dtest=JudgeStabilityTest
```

Without `OPENAI_API_KEY` both tests are skipped (`@EnabledIfEnvironmentVariable`), so a plain `mvn verify` stays green in CI.

## What to look at

- `ragguard-reports/ragguard-report-*.html` — per-question scores, per-claim verdicts, run-over-run diff and the trend chart.
- The quality gate fails the build when faithfulness / context recall / context precision drop below their thresholds — try degrading `DemoRagChain` (e.g. retrieve only 1 document) and watch context recall collapse.
