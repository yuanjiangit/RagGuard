package com.aizerohub.ragguard.core.generate;

/**
 * A QA pair generated from a source chunk by a {@link QuestionGenerator}.
 *
 * @param question the generated question
 * @param answer   the expected answer, grounded in the source chunk
 */
public record GeneratedQuestion(String question, String answer) {

    public GeneratedQuestion {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("generated question must not be blank");
        }
        if (answer == null || answer.isBlank()) {
            throw new IllegalArgumentException("generated answer must not be blank");
        }
    }
}
