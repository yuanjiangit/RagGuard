package com.aizerohub.ragguard.core.model;

/**
 * An atomic factual statement decomposed from an answer (faithfulness,
 * context recall). One sentence containing two facts yields two claims.
 *
 * @param text the atomic claim text
 */
public record Claim(String text) {

    public Claim {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("claim text must not be blank");
        }
    }
}
