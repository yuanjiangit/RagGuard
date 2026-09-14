package com.aizerohub.ragguard.core.generate;

/**
 * A generated, deduplicated QA candidate awaiting human confirmation.
 *
 * @param question      the generated question
 * @param expectedAnswer the grounded answer
 * @param sourceExcerpt the chunk the pair was generated from (for review)
 */
public record GeneratedTestCase(String question, String expectedAnswer, String sourceExcerpt) {
}
