package com.aizerohub.ragguard.core.model;

/**
 * A claim together with the judge's verdict on whether the retrieved
 * contexts support it. Produced by the (merged) decompose-and-verify
 * judge call used by the faithfulness metric.
 *
 * @param claim  the atomic claim text
 * @param verdict support verdict against the contexts
 * @param reason the judge's explanation, for report drill-down
 */
public record ClaimVerdict(Claim claim, SupportVerdict verdict, String reason) {
}
