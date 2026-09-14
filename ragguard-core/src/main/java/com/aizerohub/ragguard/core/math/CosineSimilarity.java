package com.aizerohub.ragguard.core.math;

/**
 * Cosine similarity for dense float/double vectors. Zero-magnitude vectors
 * yield a similarity of 0 (no direction), never NaN.
 */
public final class CosineSimilarity {

    private CosineSimilarity() {
    }

    public static double of(double[] a, double[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            throw new IllegalArgumentException(
                    "vectors must be non-empty and of equal length (" + length(a) + " vs " + length(b) + ")");
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static int length(double[] v) {
        return v == null ? -1 : v.length;
    }
}
