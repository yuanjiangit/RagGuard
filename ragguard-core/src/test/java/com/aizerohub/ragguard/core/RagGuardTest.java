package com.aizerohub.ragguard.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Skeleton smoke test. Replaced by offline metric tests with fixed mock
 * data in M1 (see docs/design/01-ragas-metrics-notes.md).
 */
class RagGuardTest {

    @Test
    @DisplayName("version constant is populated")
    void versionIsPopulated() {
        assertNotEquals("", RagGuard.VERSION);
        assertEquals("0.1.0-SNAPSHOT", RagGuard.VERSION);
    }
}
