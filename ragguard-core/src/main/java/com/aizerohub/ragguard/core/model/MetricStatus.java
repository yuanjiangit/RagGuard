package com.aizerohub.ragguard.core.model;

/**
 * Lifecycle status of a single metric evaluation.
 *
 * <ul>
 *   <li>{@link #OK} — score is valid (judge may or may not have been called;
 *       short-circuited results with a meaningful score, e.g. "no contexts
 *       retrieved" for faithfulness, are also OK and carry an explanatory message)</li>
 *   <li>{@link #JUDGE_FAILED} — the judge could not produce a usable result
 *       (call failed, output missing or incomplete); score is NaN</li>
 *   <li>{@link #SKIPPED} — required input was missing/blank; score is NaN</li>
 *   <li>{@link #ERROR} — unexpected failure; score is NaN</li>
 * </ul>
 *
 * <p>JUDGE_FAILED (infrastructure problem) is deliberately separated from
 * a low score (real quality regression) so CI semantics stay clean.
 */
public enum MetricStatus {
    OK,
    JUDGE_FAILED,
    SKIPPED,
    ERROR
}
