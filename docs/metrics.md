# Metrics Reference

> All four metrics follow the [Ragas paper](https://arxiv.org/abs/2309.15217) definitions; the engineering details (three-value verdicts, merged calls, attribution indexes) are documented in [design note 01](design/01-ragas-metrics-notes.md). This page is the user-facing summary.

All scores are in **[0, 1]**, higher is better. Per-metric aggregates are the mean over cases that produced a valid score; the report's overall score is the mean of available aggregates.

## 1. Faithfulness — "Is the answer grounded?"

Measures how much of the generated answer is supported by the retrieved contexts — the hallucination detector.

- The judge decomposes the answer into **atomic claims** (one sentence with two facts → two claims) and verifies each against the contexts in a single structured call.
- Verdicts are three-valued: `SUPPORTED`, `REFUTED` (contradicts the contexts), `NOT_ENOUGH_INFO` (not covered).
- **Strict scoring**: `REFUTED` and `NOT_ENOUGH_INFO` both count in the denominator; only `SUPPORTED` counts in the numerator.

```
faithfulness = |supported claims| / |all claims|
```

Requires: the chain's **answer + contexts**. Short-circuits: blank answer → SKIPPED; empty contexts → score 0 (nothing can support a claim).

Drill-down: every claim with its verdict and the judge's reason — the report shows exactly which sentence was fabricated.

**Typical failure it catches**: the generator inventing facts, or paraphrasing beyond what the context supports.

## 2. Answer Relevance — "Does the answer address the question?"

Measures how well the answer addresses the question, **without needing a ground truth**.

- The judge reverse-generates `N` plausible questions from the answer (default `N=3`, same language as the answer).
- Each reverse question is embedded and compared to the original question by cosine similarity.

```
answer_relevance = mean(cos(E(q̂ᵢ), E(q)))
```

Requires: **question + answer** + an `EmbeddingModel`. Short-circuits: blank answer → SKIPPED.

Drill-down: each reverse question with its similarity.

**Typical failure it catches**: verbose, evasive, or off-topic answers.

## 3. Context Recall — "Did retrieval catch everything needed?"

Measures retrieval **coverage** against the ground-truth answer — the leak detector. Independent of the generated answer, so it isolates retrieval problems.

- The judge decomposes `expectedAnswer` into atomic claims and attributes each to one of the retrieved contexts (paraphrase counts).
- Each attribution names the **context index** it was found in — the report can literally show "claim 3's information is missing from everything retrieved".

```
context_recall = |attributable claims| / |all claims|
```

Requires: **expectedAnswer + contexts**. Short-circuits: blank expected answer → SKIPPED; empty contexts → score 0.

> Note: the `expectedContexts` test-set field does **not** participate in this algorithm (it is reserved for human review and future metrics — see [design note 01 §3](design/01-ragas-metrics-notes.md)).

**Typical failure it catches**: the retriever missing the documents/chunks that contain the answer (wrong index, bad chunking, bad embedding model).

## 4. Context Precision — "Is the best chunk ranked first?"

Measures the **ranking quality** of retrieved contexts — this is the metric that directly scores your BM25 + vector + rerank fusion.

- The judge marks each retrieved context useful / not useful for answering the question.
- Useful contexts ranked earlier contribute a higher weight (average precision):

```
context_precision = (1/|useful|) · Σ_{i: usefulᵢ} precision@i
```

Example: useful at ranks 1 and 3 of 3 → `(1 + 2/3) / 2 ≈ 0.833`. Useful at ranks 1 and 2 → `1.0`. Same two useful chunks, worse ordering → lower score.

Requires: **question + contexts**. Short-circuits: blank question or empty contexts → SKIPPED. The judge must return exactly one verdict per context, or the metric is marked JUDGE_FAILED (never silently scored).

**Typical failure it catches**: useful chunks buried under noise — tune fusion weights or add a reranker.

## Symptom → metric → fix

| Symptom | Check first | Likely fix layer |
|---|---|---|
| Answer states things not in the context | faithfulness ↓ | generation (prompt, grounding instructions) |
| Answer is verbose / evasive / off-topic | answer relevance ↓ | generation (prompt) |
| Question goes unanswered although the docs contain the answer | context recall ↓ | retrieval (chunking, embeddings, query rewrite) |
| Answer is right but slow / drifts with small perturbations / noisy citations | context precision ↓ | retrieval ranking (fusion, rerank) |

## Thresholds

Start conservative and tighten from your own baseline (run once, look at the report, set thresholds slightly under it):

| Metric | Suggested starting gate |
|---|---|
| faithfulness | ≥ 0.7–0.8 |
| context recall | ≥ 0.6 |
| context precision | ≥ 0.6 |
| answer relevance | ≥ 0.5 (embedding-dependent, noisier) |

Judge variance is the floor for how tight a threshold can be: measure it once with `JudgeStabilityTest` in the example app, then keep thresholds at least 2 standard deviations above the gate.
