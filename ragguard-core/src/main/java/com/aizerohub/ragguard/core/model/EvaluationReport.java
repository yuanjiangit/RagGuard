package com.aizerohub.ragguard.core.model;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The result of evaluating a list of test cases: per-case results plus
 * aggregate scores per metric (mean over the cases that produced a valid
 * score; cases with SKIPPED/JUDGE_FAILED/ERROR are excluded from the mean
 * but visible in {@link #caseResults()}).
 */
public final class EvaluationReport {

    private final List<CaseResult> caseResults;
    private final Map<MetricType, Double> aggregateScores;

    private EvaluationReport(List<CaseResult> caseResults) {
        this.caseResults = List.copyOf(caseResults);
        this.aggregateScores = aggregate(this.caseResults);
    }

    public static EvaluationReport of(List<CaseResult> caseResults) {
        return new EvaluationReport(caseResults);
    }

    public List<CaseResult> caseResults() {
        return caseResults;
    }

    public int caseCount() {
        return caseResults.size();
    }

    /**
     * Aggregate (mean) score for a metric, or NaN when no case produced a
     * valid score for it.
     */
    public double aggregateScore(MetricType type) {
        return aggregateScores.getOrDefault(type, Double.NaN);
    }

    /** Mean of all available aggregate scores, or NaN when nothing was scored. */
    public double overallScore() {
        double sum = 0;
        int n = 0;
        for (double v : aggregateScores.values()) {
            if (!Double.isNaN(v)) {
                sum += v;
                n++;
            }
        }
        return n == 0 ? Double.NaN : sum / n;
    }

    private static Map<MetricType, Double> aggregate(List<CaseResult> caseResults) {
        Map<MetricType, Double> sums = new EnumMap<>(MetricType.class);
        Map<MetricType, Integer> counts = new EnumMap<>(MetricType.class);
        for (CaseResult cr : caseResults) {
            for (MetricResult mr : cr.metricResults()) {
                if (mr.hasScore()) {
                    sums.merge(mr.metricType(), mr.score(), Double::sum);
                    counts.merge(mr.metricType(), 1, Integer::sum);
                }
            }
        }
        Map<MetricType, Double> means = new EnumMap<>(MetricType.class);
        sums.forEach((type, sum) -> means.put(type, sum / counts.get(type)));
        return means;
    }
}
