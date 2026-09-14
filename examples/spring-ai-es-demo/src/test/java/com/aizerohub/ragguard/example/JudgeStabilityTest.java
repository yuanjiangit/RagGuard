package com.aizerohub.ragguard.example;

import com.aizerohub.ragguard.core.model.EvaluationReport;
import com.aizerohub.ragguard.core.model.MetricType;
import com.aizerohub.ragguard.spring.RagGuardFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Judge stability measurement (M2 deliverable): runs the full evaluation
 * N times and prints per-metric mean and standard deviation. The numbers
 * go into the README ("LLM scoring reliability" answered with data).
 *
 * <p>Manual run only — needs OPENAI_API_KEY + Elasticsearch and costs N
 * full evaluations:
 *
 * <pre>
 * OPENAI_API_KEY=... mvn -pl examples/spring-ai-es-demo test \
 *   -Dtest=JudgeStabilityTest -Dspring-boot.run.profiles=...
 * </pre>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RAGGUARD_STABILITY_RUN", matches = "true")
class JudgeStabilityTest {

    private static final int RUNS = 20;

    @Autowired
    RagGuardFacade ragGuard;

    @Test
    void measureVariance() {
        List<Double> faithfulness = new ArrayList<>();
        List<Double> answerRelevance = new ArrayList<>();
        List<Double> contextRecall = new ArrayList<>();
        List<Double> contextPrecision = new ArrayList<>();
        for (int i = 1; i <= RUNS; i++) {
            EvaluationReport report = ragGuard.run();
            collect(report.aggregateScore(MetricType.FAITHFULNESS), faithfulness);
            collect(report.aggregateScore(MetricType.ANSWER_RELEVANCE), answerRelevance);
            collect(report.aggregateScore(MetricType.CONTEXT_RECALL), contextRecall);
            collect(report.aggregateScore(MetricType.CONTEXT_PRECISION), contextPrecision);
            System.out.printf(Locale.ROOT, "run %d/%d done%n", i, RUNS);
        }
        System.out.println("\n=== Judge stability over " + RUNS + " runs ===");
        printStats("faithfulness      ", faithfulness);
        printStats("answer relevance  ", answerRelevance);
        printStats("context recall    ", contextRecall);
        printStats("context precision ", contextPrecision);
    }

    private static void collect(double score, List<Double> into) {
        if (!Double.isNaN(score)) {
            into.add(score);
        }
    }

    private static void printStats(String label, List<Double> scores) {
        if (scores.isEmpty()) {
            System.out.println(label + ": no valid scores");
            return;
        }
        double mean = scores.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        double variance = scores.stream()
                .mapToDouble(s -> (s - mean) * (s - mean))
                .average()
                .orElse(Double.NaN);
        System.out.printf(Locale.ROOT, "%s n=%d mean=%.4f stddev=%.4f min=%.3f max=%.3f%n",
                label, scores.size(), mean, Math.sqrt(variance),
                scores.stream().min(Double::compare).orElse(Double.NaN),
                scores.stream().max(Double::compare).orElse(Double.NaN));
    }
}
