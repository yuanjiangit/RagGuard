package com.aizerohub.ragguard.core.model;

import java.util.List;

/**
 * The answer produced by the system under test, together with the contexts
 * its retrieval step returned.
 *
 * @param answer   the generated answer; may be blank
 * @param contexts retrieved contexts in retrieval order (rank 0 first); may be empty
 */
public record RagAnswer(String answer, List<String> contexts) {

    public RagAnswer {
        contexts = contexts == null ? List.of() : List.copyOf(contexts);
    }
}
