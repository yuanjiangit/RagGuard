package com.aizerohub.ragguard.junit5;

import com.aizerohub.ragguard.core.model.RagChain;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a static, zero-argument method returning the {@link RagChain}
 * under test. Required on every {@link RagTest @RagTest} class.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RagChainSupplier {
}
