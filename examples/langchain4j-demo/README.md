# Example: langchain4j (no Spring)

End-to-end RagGuard demo (M3) using **langchain4j only — no Spring involved**. Shows the pure JUnit 5 route: the `@RagTest` extension drives everything through supplier annotations.

## What's inside

| File | Role |
|---|---|
| [`DemoRagChain.java`](src/main/java/com/aizerohub/ragguard/example/DemoRagChain.java) | **The system under test**: keyword-overlap "retrieval" over an in-memory knowledge base + langchain4j generator. Swap it for your real pipeline — RagGuard evaluates whatever `RagChain` does. |
| [`demo-test-set.yml`](src/test/resources/demo-test-set.yml) | 3-question test set about the knowledge base |
| [`RagEvaluationTest.java`](src/test/java/com/aizerohub/ragguard/example/RagEvaluationTest.java) | The whole evaluation wiring in one class: |

```java
@RagTest(testSet = "demo-test-set.yml")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class RagEvaluationTest {

    @RagChainSupplier
    static RagChain chain() { return new DemoRagChain(openAiChatModel()); }

    @RagJudgeSupplier
    static Judge judge() { return new LangChain4jJudge(openAiChatModel()); }

    @RagEmbeddingSupplier
    static EmbeddingModel embedding() {
        return new LangChain4jEmbeddingAdapter(OpenAiEmbeddingModel.builder()…build());
    }

    @Test
    void qualityGate(EvaluationReport report) {
        RagAssertions.assertMetricAtLeast(report, MetricType.FAITHFULNESS, 0.7);
    }
}
```

## Running it

```bash
export OPENAI_API_KEY=sk-...
mvn -pl examples/langchain4j-demo test -Dtest=RagEvaluationTest
```

Without the key the test is skipped, so a plain `mvn verify` stays green in CI. This demo needs no Elasticsearch — the "retriever" is in-memory.

## Modules used

- `ragguard-langchain4j` — `LangChain4jJudge` / `LangChain4jEmbeddingAdapter` over langchain4j 1.0
- `ragguard-junit5` — `@RagTest` extension, `RagAssertions`

See the [Spring AI + ES demo](../spring-ai-es-demo/README.md) for the Spring Boot route with report output and judge caching.
