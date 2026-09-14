package com.aizerohub.ragguard.core.judge;

/**
 * Pluggable embedding model (Spring AI adapter first, langchain4j in P1).
 * Used by the answer relevance metric to compare reverse-generated
 * questions with the original question.
 *
 * <p>Implementations must be thread-safe when used with parallelism &gt; 1.
 */
@FunctionalInterface
public interface EmbeddingModel {

    /** Embeds the text; the returned vector is never null and must not be modified. */
    double[] embed(String text);
}
