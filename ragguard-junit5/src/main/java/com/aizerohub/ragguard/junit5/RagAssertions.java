package com.aizerohub.ragguard.junit5;

import com.aizerohub.ragguard.core.model.CaseResult;
import com.aizerohub.ragguard.core.model.EvaluationReport;
import com.aizerohub.ragguard.core.model.MetricResult;
import com.aizerohub.ragguard.core.model.MetricType;
import org.opentest4j.AssertionFailedError;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Assertion entry points for RAG evaluation: a score below threshold fails
 * the test with a drill-down message (per-claim verdicts, per-context
 * usefulness) so the failure explains itself.
 */
public final class RagAssertions {

    private RagAssertions() {
    }

    /** Asserts the aggregate score of a metric over the whole report. */
    public static void assertMetricAtLeast(EvaluationReport report, MetricType metricType, double min) {
        double score = report.aggregateScore(metricType);
        if (Double.isNaN(score)) {
            throw new AssertionFailedError(String.format(
                    "%s has no valid score in the report (all %d cases were SKIPPED/JUDGE_FAILED/ERROR)",
                    metricType, report.caseCount()), min, score);
        }
        if (score < min) {
            throw new AssertionFailedError(failureMessage(metricType, min, score, report),
                    min, score);
        }
    }

    /** Asserts the metric score of a single test case. */
    public static void assertCaseMetricAtLeast(EvaluationReport report, String caseId,
                                               MetricType metricType, double min) {
        MetricResult result = report.caseResults().stream()
                .filter(cr -> caseId.equals(cr.caseId()))
                .map(cr -> cr.metric(metricType))
                .flatMap(Optional::stream)
                .findFirst()
                .orElseThrow(() -> new AssertionFailedError(
                        "no " + metricType + " result for case '" + caseId + "' in the report"));
        assertScoreAtLeast(result, min);
    }

    /** Asserts a single metric result. */
    public static void assertScoreAtLeast(MetricResult result, double min) {
        if (!result.hasScore()) {
            throw new AssertionFailedError(String.format(
                    "%s produced no score (status=%s)%s",
                    result.metricType(),
                    result.status(),
                    result.message().map(m -> ": " + m).orElse("")),
                    min, Double.NaN);
        }
        if (result.score() < min) {
            throw new AssertionFailedError(drillDownMessage(result, min), min, result.score());
        }
    }

    private static String failureMessage(MetricType metricType, double min, double score,
                                         EvaluationReport report) {
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT,
                "%s aggregate score %.3f is below the required threshold %.3f%n",
                metricType, score, min));
        List<CaseResult> offenders = report.caseResults().stream()
                .filter(cr -> cr.metric(metricType).map(r -> r.hasScore() && r.score() < min).orElse(false))
                .toList();
        if (!offenders.isEmpty()) {
            sb.append("Cases below threshold:").append(System.lineSeparator());
            for (CaseResult cr : offenders) {
                cr.metric(metricType).ifPresent(r ->
                        sb.append(String.format(Locale.ROOT, "  - %s: %.3f%n", cr.caseId(), r.score())));
            }
        }
        return sb.toString();
    }

    private static String drillDownMessage(MetricResult result, double min) {
        StringJoiner joiner = new StringJoiner(System.lineSeparator());
        joiner.add(String.format(Locale.ROOT, "%s score %.3f is below the required threshold %.3f",
                result.metricType(), result.score(), min));
        result.claimVerdicts().forEach(cv -> joiner.add(String.format("  - [%s] %s — %s",
                cv.verdict(), cv.claim().text(), nullToEmpty(cv.reason()))));
        result.claimAttributions().forEach(ca -> joiner.add(String.format("  - [%s] %s — %s",
                ca.attributable() ? "ATTRIBUTED@" + ca.attributedContextIndex() : "NOT_ATTRIBUTED",
                ca.claim().text(), nullToEmpty(ca.reason()))));
        result.contextVerdicts().forEach(cu -> joiner.add(String.format("  - context[%d]: %s — %s",
                cu.contextIndex(), cu.useful() ? "USEFUL" : "NOT_USEFUL", nullToEmpty(cu.reason()))));
        result.reverseQuestions().forEach(rq -> joiner.add(String.format(Locale.ROOT,
                "  - reverse question (similarity %.3f): %s", rq.similarity(), rq.question())));
        return joiner.toString();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
