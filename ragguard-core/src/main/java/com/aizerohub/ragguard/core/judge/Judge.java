package com.aizerohub.ragguard.core.judge;

import com.aizerohub.ragguard.core.model.ClaimAttribution;
import com.aizerohub.ragguard.core.model.ClaimVerdict;
import com.aizerohub.ragguard.core.model.ContextUsefulness;

import java.util.List;

/**
 * LLM-as-Judge abstraction: one method per judge interaction the four
 * metrics need. Implementations call a real model (Spring AI adapter in
 * M2) with temperature 0 and structured output; tests inject fixed mocks.
 *
 * <p>All list results must be parallel to their input (one verdict per
 * claim / per context); returning a different size marks the metric
 * JUDGE_FAILED instead of silently producing a wrong score.
 *
 * <p>Implementations must be thread-safe when used with parallelism &gt; 1.
 */
public interface Judge {

    /**
     * Faithfulness: decompose the answer into atomic claims and verify each
     * against the retrieved contexts in a single structured call.
     *
     * @return one verdict per decomposed claim
     */
    List<ClaimVerdict> verifyFaithfulness(String answer, List<String> contexts);

    /**
     * Context recall: decompose the expected answer into atomic claims and
     * attribute each to one of the retrieved contexts.
     *
     * @return one attribution per decomposed claim
     */
    List<ClaimAttribution> attributeContextRecall(String expectedAnswer, List<String> contexts);

    /**
     * Context precision: judge whether each retrieved context (by rank) is
     * useful for answering the question.
     *
     * @return exactly one {@link ContextUsefulness} per input context, in rank order
     */
    List<ContextUsefulness> judgeContextPrecision(String question, List<String> contexts);

    /**
     * Answer relevance: reverse-generate plausible questions for the answer.
     *
     * @param n the number of questions to generate
     * @return the generated questions (fewer than n on model refusal is acceptable)
     */
    List<String> generateReverseQuestions(String answer, int n);
}
