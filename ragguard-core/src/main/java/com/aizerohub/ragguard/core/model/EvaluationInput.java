package com.aizerohub.ragguard.core.model;

import java.util.List;

/**
 * Everything a metric needs to evaluate one test case.
 *
 * @param caseId         the test case id
 * @param question       the user question
 * @param answer         the answer produced by the system under test (may be blank)
 * @param contexts       retrieved contexts in rank order, never null (may be empty)
 * @param expectedAnswer ground-truth answer, never null (may be blank)
 */
public record EvaluationInput(
        String caseId,
        String question,
        String answer,
        List<String> contexts,
        String expectedAnswer) {

    public EvaluationInput {
        contexts = contexts == null ? List.of() : List.copyOf(contexts);
        expectedAnswer = expectedAnswer == null ? "" : expectedAnswer;
    }
}
