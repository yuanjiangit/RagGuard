package com.aizerohub.ragguard.langchain4j;

import com.aizerohub.ragguard.core.judge.EmbeddingModel;

import java.util.Objects;

/**
 * Adapts a langchain4j {@code dev.langchain4j.model.embedding.EmbeddingModel}
 * to the framework-free RagGuard {@link EmbeddingModel} interface.
 */
public final class LangChain4jEmbeddingAdapter implements EmbeddingModel {

    private final dev.langchain4j.model.embedding.EmbeddingModel delegate;

    public LangChain4jEmbeddingAdapter(dev.langchain4j.model.embedding.EmbeddingModel delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public double[] embed(String text) {
        float[] vector = delegate.embed(text).content().vector();
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
