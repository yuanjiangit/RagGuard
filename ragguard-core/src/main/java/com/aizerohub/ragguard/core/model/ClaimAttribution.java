package com.aizerohub.ragguard.core.model;

/**
 * A claim decomposed from the expected answer, together with the judge's
 * attribution verdict for the context recall metric: whether the claim
 * can be attributed to one of the retrieved contexts, and which one.
 *
 * @param claim              the atomic claim from the expected answer
 * @param attributable       whether the claim can be attributed to a context
 * @param attributedContextIndex 0-based index into the context list, or -1 if not attributable
 * @param reason             the judge's explanation, for report drill-down
 */
public record ClaimAttribution(Claim claim, boolean attributable, int attributedContextIndex, String reason) {
}
