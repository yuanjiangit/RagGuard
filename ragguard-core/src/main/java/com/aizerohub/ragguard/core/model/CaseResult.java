package com.aizerohub.ragguard.core.model;

import java.util.List;
import java.util.Optional;

/**
 * The result of evaluating one test case across all metrics.
 *
 * @param caseId        the test case id
 * @param question      the user question
 * @param metricResults one result per metric, in metric order
 */
public record CaseResult(String caseId, String question, List<MetricResult> metricResults) {

    public CaseResult {
        metricResults = metricResults == null ? List.of() : List.copyOf(metricResults);
    }

    public Optional<MetricResult> metric(MetricType type) {
        return metricResults.stream().filter(r -> r.metricType() == type).findFirst();
    }
}
