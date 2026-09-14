package com.aizerohub.ragguard.core.model;

/**
 * The four core metrics, following the metric definitions of the
 * Ragas paper (arXiv:2309.15217). See docs/design/01-ragas-metrics-notes.md.
 */
public enum MetricType {

    /** How much of the answer is supported by the retrieved contexts. */
    FAITHFULNESS,

    /** How well the answer addresses the question (reverse-question embedding similarity). */
    ANSWER_RELEVANCE,

    /** How much of the ground-truth answer can be attributed to the retrieved contexts. */
    CONTEXT_RECALL,

    /** Ranking quality of the retrieved contexts (average precision of useful contexts). */
    CONTEXT_PRECISION
}
