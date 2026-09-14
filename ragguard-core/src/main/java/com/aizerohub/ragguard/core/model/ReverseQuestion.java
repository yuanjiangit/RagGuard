package com.aizerohub.ragguard.core.model;

/**
 * A question reverse-generated from the answer (answer relevance metric),
 * with its embedding similarity to the original question.
 *
 * @param question   the reverse-generated question
 * @param similarity cosine similarity between the reverse question and the original question
 */
public record ReverseQuestion(String question, double similarity) {
}
