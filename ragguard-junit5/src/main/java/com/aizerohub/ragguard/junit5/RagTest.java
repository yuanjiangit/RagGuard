package com.aizerohub.ragguard.junit5;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test class as a RAG evaluation test. The class must provide:
 *
 * <ul>
 *   <li>a static method annotated {@link RagChainSupplier @RagChainSupplier}
 *       returning the {@code RagChain} under test,</li>
 *   <li>a static method annotated {@link RagJudgeSupplier @RagJudgeSupplier}
 *       returning the {@code Judge},</li>
 *   <li>optionally, a static method annotated
 *       {@link RagEmbeddingSupplier @RagEmbeddingSupplier} returning the
 *       {@code EmbeddingModel} (required for the answer relevance metric).</li>
 * </ul>
 *
 * <p>Test methods may declare an {@code EvaluationReport} parameter; the
 * extension evaluates the test set once per class and injects the report:
 *
 * <pre>{@code
 * @RagTest(testSet = "rag-test-set.yml")
 * class MyRagRegressionTest {
 *
 *     @RagChainSupplier
 *     static RagChain chain() { return ...; }
 *
 *     @RagJudgeSupplier
 *     static Judge judge() { return ...; }
 *
 *     @Test
 *     void faithfulnessStaysAboveThreshold(EvaluationReport report) {
 *         RagAssertions.assertMetricAtLeast(report, MetricType.FAITHFULNESS, 0.8);
 *     }
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@ExtendWith(RagGuardExtension.class)
public @interface RagTest {

    /**
     * Test set location: a classpath resource (optionally prefixed with
     * {@code classpath:}) or a file path relative to the working directory.
     * Defaults to {@code ragguard-test-set.yml} on the classpath.
     */
    String testSet() default "ragguard-test-set.yml";
}
