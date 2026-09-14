package com.aizerohub.ragguard.core.model;

/**
 * The system under test: anything that answers a question and can expose
 * the contexts its retrieval step used. Implement this to evaluate any
 * RagChain — Spring AI and langchain4j adapters are thin wrappers around
 * this interface.
 *
 * <p>Implementations must be thread-safe when used with parallelism &gt; 1.
 */
@FunctionalInterface
public interface RagChain {

    RagAnswer answer(String question);
}
