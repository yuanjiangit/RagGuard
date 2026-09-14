package com.aizerohub.ragguard.junit5;

import com.aizerohub.ragguard.core.model.Claim;
import com.aizerohub.ragguard.core.model.ClaimAttribution;
import com.aizerohub.ragguard.core.model.ClaimVerdict;
import com.aizerohub.ragguard.core.model.ContextUsefulness;
import com.aizerohub.ragguard.core.model.EvaluationReport;
import com.aizerohub.ragguard.core.model.MetricResult;
import com.aizerohub.ragguard.core.model.MetricType;
import com.aizerohub.ragguard.core.model.SupportVerdict;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagAssertionsTest {

    private static MetricResult result(double score) {
        return MetricResult.builder(MetricType.FAITHFULNESS)
                .score(score)
                .claimVerdicts(List.of(
                        new ClaimVerdict(new Claim("claim one"), SupportVerdict.SUPPORTED, "ok"),
                        new ClaimVerdict(new Claim("claim two"), SupportVerdict.REFUTED, "contradicts ctx")))
                .build();
    }

    @Test
    void passingScore_doesNotThrow() {
        assertDoesNotThrow(() -> RagAssertions.assertScoreAtLeast(result(0.9), 0.8));
    }

    @Test
    void failingScore_throwsWithClaimDrillDown() {
        AssertionFailedError error = assertThrows(AssertionFailedError.class,
                () -> RagAssertions.assertScoreAtLeast(result(0.5), 0.8));
        assertEquals(0.8, (Double) error.getExpected().getValue(), 1e-9);
        assertEquals(0.5, (Double) error.getActual().getValue(), 1e-9);
        String message = error.getMessage();
        assertTrue(message.contains("0.500"), message);
        assertTrue(message.contains("claim two"), message);
        assertTrue(message.contains("REFUTED"), message);
    }

    @Test
    void judgeFailedResult_throwsWithStatusAndMessage() {
        MetricResult failed = MetricResult.builder(MetricType.FAITHFULNESS)
                .status(com.aizerohub.ragguard.core.model.MetricStatus.JUDGE_FAILED)
                .message("judge returned no claims")
                .build();
        AssertionFailedError error = assertThrows(AssertionFailedError.class,
                () -> RagAssertions.assertScoreAtLeast(failed, 0.8));
        assertTrue(error.getMessage().contains("JUDGE_FAILED"));
        assertTrue(error.getMessage().contains("judge returned no claims"));
    }

    @Test
    void contextPrecisionDrillDownIncluded() {
        MetricResult precision = MetricResult.builder(MetricType.CONTEXT_PRECISION)
                .score(0.2)
                .contextVerdicts(List.of(new ContextUsefulness(0, false, "irrelevant")))
                .build();
        AssertionFailedError error = assertThrows(AssertionFailedError.class,
                () -> RagAssertions.assertScoreAtLeast(precision, 0.8));
        assertTrue(error.getMessage().contains("context[0]: NOT_USEFUL — irrelevant"));
    }

    @Test
    void contextRecallDrillDownIncluded() {
        MetricResult recall = MetricResult.builder(MetricType.CONTEXT_RECALL)
                .score(0.0)
                .claimAttributions(List.of(
                        new ClaimAttribution(new Claim("needs fact"), false, -1, "missing")))
                .build();
        AssertionFailedError error = assertThrows(AssertionFailedError.class,
                () -> RagAssertions.assertScoreAtLeast(recall, 0.8));
        assertTrue(error.getMessage().contains("NOT_ATTRIBUTED"));
    }

    @Test
    void missingCase_throwsHelpfulError() {
        EvaluationReport report = EvaluationReport.of(List.of(
                new com.aizerohub.ragguard.core.model.CaseResult("q1", "question",
                        List.of(result(0.9)))));
        AssertionFailedError error = assertThrows(AssertionFailedError.class,
                () -> RagAssertions.assertCaseMetricAtLeast(report, "nope", MetricType.FAITHFULNESS, 0.5));
        assertTrue(error.getMessage().contains("nope"));
    }
}
