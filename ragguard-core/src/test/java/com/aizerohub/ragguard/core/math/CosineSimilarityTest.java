package com.aizerohub.ragguard.core.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CosineSimilarityTest {

    @Test
    void identicalVectors_similarityOne() {
        assertEquals(1.0, CosineSimilarity.of(new double[]{1, 2, 3}, new double[]{1, 2, 3}), 1e-9);
    }

    @Test
    void orthogonalVectors_similarityZero() {
        assertEquals(0.0, CosineSimilarity.of(new double[]{1, 0}, new double[]{0, 1}), 1e-9);
    }

    @Test
    void oppositeVectors_similarityMinusOne() {
        assertEquals(-1.0, CosineSimilarity.of(new double[]{1, 0}, new double[]{-1, 0}), 1e-9);
    }

    @Test
    void magnitudeIndependent() {
        assertEquals(1.0, CosineSimilarity.of(new double[]{1, 0}, new double[]{5, 0}), 1e-9);
    }

    @Test
    void zeroVector_similarityZeroNotNaN() {
        assertEquals(0.0, CosineSimilarity.of(new double[]{0, 0}, new double[]{1, 0}), 1e-9);
    }

    @Test
    void lengthMismatch_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> CosineSimilarity.of(new double[]{1}, new double[]{1, 2}));
        assertThrows(IllegalArgumentException.class,
                () -> CosineSimilarity.of(null, new double[]{1}));
    }
}
