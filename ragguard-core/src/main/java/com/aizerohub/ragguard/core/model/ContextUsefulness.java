package com.aizerohub.ragguard.core.model;

/**
 * The judge's usefulness verdict for a single retrieved context, used by
 * the context precision metric.
 *
 * @param contextIndex 0-based index into the context list (i.e. the retrieval rank)
 * @param useful       whether the context is useful for answering the question
 * @param reason       the judge's explanation, for report drill-down
 */
public record ContextUsefulness(int contextIndex, boolean useful, String reason) {
}
