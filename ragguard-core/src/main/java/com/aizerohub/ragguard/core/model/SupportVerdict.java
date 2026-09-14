package com.aizerohub.ragguard.core.model;

/**
 * Three-value verdict for a single atomic claim, judged against the
 * retrieved contexts. {@link #REFUTED} and {@link #NOT_ENOUGH_INFO}
 * both count towards the denominator of faithfulness; only
 * {@link #SUPPORTED} counts towards the numerator. The distinction
 * matters for diagnosis: REFUTED means the answer contradicts the
 * contexts (worse), NOT_ENOUGH_INFO means the contexts did not cover it.
 */
public enum SupportVerdict {
    SUPPORTED,
    REFUTED,
    NOT_ENOUGH_INFO
}
