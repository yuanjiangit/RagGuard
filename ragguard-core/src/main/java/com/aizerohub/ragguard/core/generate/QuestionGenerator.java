package com.aizerohub.ragguard.core.generate;

/**
 * LLM abstraction for test-set generation: produce plausible QA pairs from
 * a source chunk. Implementations must ground the answers in the chunk —
 * the chunk ships as the source excerpt for human review.
 */
@FunctionalInterface
public interface QuestionGenerator {

    /**
     * @param chunk a self-contained source text excerpt
     * @param n     the number of QA pairs to generate
     */
    java.util.List<GeneratedQuestion> generateFor(String chunk, int n);
}
