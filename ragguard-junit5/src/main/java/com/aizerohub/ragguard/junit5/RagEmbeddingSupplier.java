package com.aizerohub.ragguard.junit5;

import com.aizerohub.ragguard.core.judge.EmbeddingModel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a static, zero-argument method returning the
 * {@link EmbeddingModel}. Optional; required only when the answer
 * relevance metric is asserted.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RagEmbeddingSupplier {
}
