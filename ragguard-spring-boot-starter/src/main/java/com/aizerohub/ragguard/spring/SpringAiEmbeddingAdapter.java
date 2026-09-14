package com.aizerohub.ragguard.spring;

import com.aizerohub.ragguard.core.judge.EmbeddingModel;

import java.util.Objects;

/**
 * Adapts a Spring AI {@code org.springframework.ai.embedding.EmbeddingModel}
 * to the framework-free RagGuard {@link EmbeddingModel} interface.
 */
public final class SpringAiEmbeddingAdapter implements EmbeddingModel {

    private final org.springframework.ai.embedding.EmbeddingModel delegate;

    public SpringAiEmbeddingAdapter(org.springframework.ai.embedding.EmbeddingModel delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public double[] embed(String text) {
        float[] vector = delegate.embed(text);
        if (vector == null) {
            throw new IllegalStateException("embedding model returned null for: "
                    + (text == null ? "null" : text.substring(0, Math.min(50, text.length()))));
        }
        double[] result = new double[vector.length];
        for (int i = 0; i < vector.length; i++) {
            result[i] = vector[i];
        }
        return result;
    }
}
