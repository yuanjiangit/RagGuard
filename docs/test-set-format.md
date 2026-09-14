# Test Set Format

> The test set is the ground truth your RAG is judged against — keep it in version control and review changes like code.

## Schema

```yaml
testCases:
  - id: q1                        # optional; defaults to case-1, case-2, …
    question: What is RagGuard?   # required, non-blank
    expectedAnswer: A RAG evaluation framework for the JVM.   # optional (needed for context recall)
    expectedContexts:             # optional
      - RagGuard evaluates RAG applications.
```

JSON works too (the loader parses JSON as a YAML subset):

```json
{"testCases": [{"id": "j1", "question": "JSON works?", "expectedAnswer": "yes"}]}
```

## Field reference

| Field | Required | Used by | Notes |
|---|---|---|---|
| `id` | no | reports, `assertCaseMetricAtLeast` | auto-assigned `case-N` when blank; must be unique for per-case assertions |
| `question` | **yes** | all metrics | loader rejects blank questions with `TestSetFormatException` |
| `expectedAnswer` | for context recall | context recall | omitting it marks that case's context recall SKIPPED (other metrics unaffected) |
| `expectedContexts` | no | **nothing in P0** | kept for human review and future metrics — the context recall algorithm compares against the *retrieved* contexts, not the expected ones (see [metrics.md](metrics.md) §3 and [design note 01 §3](design/01-ragas-metrics-notes.md)) |

## Validation rules

- Root must be a mapping with a non-empty `testCases` list.
- Every entry must be a mapping with a non-blank `question`.
- Malformed YAML, missing fields, or wrong types raise `TestSetFormatException` naming the offending index — test sets fail fast, never silently.

## Writing good test cases

1. **Cover the real question distribution** — mine support tickets, search logs, and chat history; generated candidates (see below) tend to mirror document phrasing, so human-authored "how users actually ask" cases are the highest-value additions.
2. **One fact per expected answer where possible** — context recall decomposes into atomic claims; focused answers produce sharper attribution.
3. **Include adversarial cases** — questions your docs *cannot* answer (expect faithfulness to hold while the answer says "I don't know"), and near-duplicate phrasings that stress retrieval.
4. **Same language as your users** — the judge prompts preserve the answer's language; mixed-language test sets dilute embedding similarity.

## Generating candidates from documents

`TestSetGenerator` chunks documents on paragraph boundaries, generates grounded QA pairs per chunk, deduplicates (normalized text + optional embedding similarity), and writes a candidate YAML:

```java
TestSetGenerator generator = TestSetGenerator.builder(new SpringAiQuestionGenerator(chatModel))
        .embedding(embeddingModel)     // optional: near-duplicate detection
        .maxQuestionsPerChunk(3)       // default 3
        .maxChunkChars(1600)           // default 1600
        .build();
List<GeneratedTestCase> candidates = generator.generate(Map.of("manual.md", documentText));
Files.writeString(Path.of("candidates.yml"), TestSetWriter.toYaml(candidates));
```

`candidates.yml` is a **human-confirmation checklist**, not a ready test set: generated pairs can hallucinate and skew towards document phrasing. Review it (delete wrong pairs, fix phrasing, add real user questions), rename to `ragguard-test-set.yml`, and commit. The candidate format is identical to the final format — one round trip through `TestSetLoader` is part of the test suite.

## Where the file lives

| Context | Default | How to override |
|---|---|---|
| Spring Boot starter | `ragguard-test-set.yml` on the classpath | `ragguard.test-set` (supports `classpath:` prefix or file path) |
| JUnit 5 extension | `ragguard-test-set.yml` on the classpath | `@RagTest(testSet = "...")` |
| Programmatic | — | `engine.run(List<RagTestCase>)` directly |
