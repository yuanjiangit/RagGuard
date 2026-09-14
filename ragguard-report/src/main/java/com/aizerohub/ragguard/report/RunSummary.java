package com.aizerohub.ragguard.report;

import com.aizerohub.ragguard.core.model.MetricType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Immutable summary of one evaluation run: aggregate scores per metric and
 * per-case scores. Persisted to {@code ragguard-latest-run.json} by
 * {@link ReportWriter} so the next run can diff against it.
 */
public final class RunSummary {

    private final Map<MetricType, Double> aggregateScores;
    private final Map<String, Map<MetricType, Double>> caseScores;
    private final int caseCount;

    public RunSummary(Map<MetricType, Double> aggregateScores,
                      Map<String, Map<MetricType, Double>> caseScores,
                      int caseCount) {
        this.aggregateScores = Map.copyOf(aggregateScores);
        Map<String, Map<MetricType, Double>> cases = new java.util.LinkedHashMap<>();
        caseScores.forEach((id, scores) -> cases.put(id, Map.copyOf(scores)));
        this.caseScores = Map.copyOf(cases);
        this.caseCount = caseCount;
    }

    public Map<MetricType, Double> aggregateScores() {
        return aggregateScores;
    }

    public Map<String, Map<MetricType, Double>> caseScores() {
        return caseScores;
    }

    public int caseCount() {
        return caseCount;
    }

    public Double aggregate(MetricType type) {
        return aggregateScores.get(type);
    }

    /** Delta of the aggregate score versus this (previous) summary; null when not comparable. */
    public Double deltaFor(MetricType type, double currentScore) {
        Double previous = aggregateScores.get(type);
        return previous == null ? null : currentScore - previous;
    }
}
