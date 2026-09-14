package com.aizerohub.ragguard.core.engine;

import com.aizerohub.ragguard.core.judge.EmbeddingModel;
import com.aizerohub.ragguard.core.judge.Judge;
import com.aizerohub.ragguard.core.metric.AnswerRelevanceMetric;
import com.aizerohub.ragguard.core.metric.ContextPrecisionMetric;
import com.aizerohub.ragguard.core.metric.ContextRecallMetric;
import com.aizerohub.ragguard.core.metric.FaithfulnessMetric;
import com.aizerohub.ragguard.core.metric.Metric;
import com.aizerohub.ragguard.core.model.CaseResult;
import com.aizerohub.ragguard.core.model.EvaluationInput;
import com.aizerohub.ragguard.core.model.EvaluationReport;
import com.aizerohub.ragguard.core.model.MetricResult;
import com.aizerohub.ragguard.core.model.MetricStatus;
import com.aizerohub.ragguard.core.model.MetricType;
import com.aizerohub.ragguard.core.model.RagAnswer;
import com.aizerohub.ragguard.core.model.RagChain;
import com.aizerohub.ragguard.core.model.RagTestCase;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs a list of test cases against a {@link RagChain} with the configured
 * metrics and produces an {@link EvaluationReport}.
 *
 * <p>Default metrics are all four P0 metrics. Batch evaluation uses bounded
 * concurrency ({@link Builder#parallelism(int)}, default 4); with
 * parallelism 1 everything runs sequentially in the calling thread. Results
 * are always returned in test-case order regardless of concurrency.
 */
public final class EvaluationEngine {

    /** Default bounded concurrency for batch evaluation. */
    public static final int DEFAULT_PARALLELISM = 4;

    private final RagChain chain;
    private final List<Metric> metrics;
    private final int parallelism;

    private EvaluationEngine(RagChain chain, List<Metric> metrics, int parallelism) {
        this.chain = chain;
        this.metrics = List.copyOf(metrics);
        this.parallelism = parallelism;
    }

    public EvaluationReport run(List<RagTestCase> testCases) {
        Objects.requireNonNull(testCases, "testCases");
        if (testCases.isEmpty()) {
            throw new IllegalArgumentException("test case list is empty");
        }
        List<RagTestCase> cases = normalize(testCases);
        if (parallelism <= 1) {
            List<CaseResult> results = new ArrayList<>(cases.size());
            for (RagTestCase tc : cases) {
                results.add(evaluateCase(tc));
            }
            return EvaluationReport.of(results);
        }
        ExecutorService executor = Executors.newFixedThreadPool(parallelism, daemonThreads());
        try {
            List<CompletableFuture<CaseResult>> futures = new ArrayList<>(cases.size());
            for (RagTestCase tc : cases) {
                futures.add(CompletableFuture.supplyAsync(() -> evaluateCase(tc), executor));
            }
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            List<CaseResult> results = new ArrayList<>(cases.size());
            for (CompletableFuture<CaseResult> f : futures) {
                results.add(f.join());
            }
            return EvaluationReport.of(results);
        } finally {
            executor.shutdownNow();
        }
    }

    private CaseResult evaluateCase(RagTestCase testCase) {
        RagAnswer ragAnswer;
        try {
            ragAnswer = chain.answer(testCase.question());
        } catch (RuntimeException e) {
            List<MetricResult> errors = metrics.stream()
                    .map(m -> MetricResult.builder(m.type())
                            .status(MetricStatus.ERROR)
                            .message("RagChain failed: " + e.getMessage())
                            .build())
                    .toList();
            return new CaseResult(testCase.id(), testCase.question(), errors);
        }
        EvaluationInput input = new EvaluationInput(
                testCase.id(), testCase.question(), ragAnswer.answer(), ragAnswer.contexts(),
                testCase.expectedAnswer());
        List<MetricResult> results = new ArrayList<>(metrics.size());
        for (Metric metric : metrics) {
            results.add(evaluateMetric(metric, input));
        }
        return new CaseResult(testCase.id(), testCase.question(), results);
    }

    private static MetricResult evaluateMetric(Metric metric, EvaluationInput input) {
        try {
            return metric.evaluate(input);
        } catch (RuntimeException e) {
            return MetricResult.builder(metric.type())
                    .status(MetricStatus.ERROR)
                    .message("metric threw: " + e)
                    .build();
        }
    }

    private static List<RagTestCase> normalize(List<RagTestCase> testCases) {
        List<RagTestCase> normalized = new ArrayList<>(testCases.size());
        for (int i = 0; i < testCases.size(); i++) {
            RagTestCase tc = Objects.requireNonNull(testCases.get(i), "test case must not be null");
            String id = tc.id() == null || tc.id().isBlank() ? "case-" + (i + 1) : tc.id();
            if (tc.question() == null || tc.question().isBlank()) {
                throw new IllegalArgumentException("test case '" + id + "' has a blank question");
            }
            normalized.add(tc.id() != null && tc.id().equals(id)
                    ? tc
                    : new RagTestCase(id, tc.question(), tc.expectedAnswer(), tc.expectedContexts()));
        }
        return normalized;
    }

    private static ThreadFactory daemonThreads() {
        AtomicInteger counter = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, "ragguard-eval-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private RagChain chain;
        private Judge judge;
        private EmbeddingModel embeddingModel;
        private Set<MetricType> metricTypes = EnumSet.allOf(MetricType.class);
        private int parallelism = DEFAULT_PARALLELISM;

        private Builder() {
        }

        public Builder chain(RagChain chain) {
            this.chain = Objects.requireNonNull(chain, "chain");
            return this;
        }

        public Builder judge(Judge judge) {
            this.judge = Objects.requireNonNull(judge, "judge");
            return this;
        }

        public Builder embedding(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
            return this;
        }

        /** Restricts the metrics to evaluate; defaults to all four. */
        public Builder metrics(MetricType... types) {
            this.metricTypes = types.length == 0
                    ? EnumSet.noneOf(MetricType.class)
                    : EnumSet.copyOf(List.of(types));
            return this;
        }

        public Builder parallelism(int parallelism) {
            if (parallelism < 1) {
                throw new IllegalArgumentException("parallelism must be >= 1");
            }
            this.parallelism = parallelism;
            return this;
        }

        public EvaluationEngine build() {
            Objects.requireNonNull(chain, "chain is required");
            Objects.requireNonNull(judge, "judge is required");
            List<Metric> metrics = new ArrayList<>(metricTypes.size());
            if (metricTypes.contains(MetricType.FAITHFULNESS)) {
                metrics.add(new FaithfulnessMetric(judge));
            }
            if (metricTypes.contains(MetricType.ANSWER_RELEVANCE)) {
                metrics.add(new AnswerRelevanceMetric(judge,
                        Objects.requireNonNull(embeddingModel,
                                "embedding is required for ANSWER_RELEVANCE")));
            }
            if (metricTypes.contains(MetricType.CONTEXT_RECALL)) {
                metrics.add(new ContextRecallMetric(judge));
            }
            if (metricTypes.contains(MetricType.CONTEXT_PRECISION)) {
                metrics.add(new ContextPrecisionMetric(judge));
            }
            if (metrics.isEmpty()) {
                throw new IllegalStateException("no metrics selected");
            }
            return new EvaluationEngine(chain, metrics, parallelism);
        }
    }
}
