package com.aizerohub.ragguard.core;

/**
 * Root entry point of RagGuard core.
 *
 * <p>RagGuard is a regression testing framework for RAG applications:
 * it evaluates faithfulness, answer relevance, context recall and
 * context precision — following the metric definitions of the Ragas
 * paper — and turns them into unit-testable assertions.
 *
 * <p>This module is intentionally framework-free. Spring AI and
 * langchain4j integration live in dedicated adapter modules.
 *
 * <p>Metric engine implementation lands in M1 — see
 * {@code docs/design/01-ragas-metrics-notes.md} for the algorithm notes.
 */
public final class RagGuard {

    /** Framework version, kept in sync with the Maven project version. */
    public static final String VERSION = "0.1.0-SNAPSHOT";

    private RagGuard() {
    }
}
