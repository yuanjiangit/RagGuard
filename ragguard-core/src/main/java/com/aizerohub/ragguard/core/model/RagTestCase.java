package com.aizerohub.ragguard.core.model;

import java.util.List;

/**
 * A single evaluation input row: a question with its expected answer and
 * (optionally) expected contexts. Field names intentionally mirror the
 * Ragas terminology.
 *
 * @param id               unique id; may be blank (the engine assigns one)
 * @param question         the user question
 * @param expectedAnswer   ground-truth answer (used by context recall); may be blank
 * @param expectedContexts ground-truth contexts; reserved for future metrics,
 *                         human review and test-set generation — not used by the
 *                         P0 context recall algorithm (see design notes §3)
 */
public record RagTestCase(String id, String question, String expectedAnswer, List<String> expectedContexts) {

    public RagTestCase {
        expectedContexts = expectedContexts == null ? List.of() : List.copyOf(expectedContexts);
    }

    public static RagTestCase of(String question) {
        return new RagTestCase(null, question, null, List.of());
    }
}
