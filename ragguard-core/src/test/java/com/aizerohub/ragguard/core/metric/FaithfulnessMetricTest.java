package com.aizerohub.ragguard.core.metric;

import com.aizerohub.ragguard.core.model.EvaluationInput;
import com.aizerohub.ragguard.core.model.MetricResult;
import com.aizerohub.ragguard.core.model.MetricStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FaithfulnessMetricTest {

    private final MockJudge judge = new MockJudge();
    private final FaithfulnessMetric metric = new FaithfulnessMetric(judge);

    private static EvaluationInput input(String answer) {
        return new EvaluationInput("c1", "q", answer, List.of("ctx1", "ctx2"), "");
    }

    @Test
    void allClaimsSupported_scoresOne() {
        judge.faithfulnessVerdicts = List.of(MockJudge.supported("a"), MockJudge.supported("b"));
        MetricResult result = metric.evaluate(input("answer"));
        assertEquals(MetricStatus.OK, result.status());
        assertEquals(1.0, result.score());
        assertEquals(2, result.claimVerdicts().size());
    }

    @Test
    void refutedAndNotEnoughInfoBothCountTowardsDenominator() {
        // 1 of 3 claims supported: REFUTED and NOT_ENOUGH_INFO both stay in the
        // denominator (design notes §1 — strict scoring)
        judge.faithfulnessVerdicts = List.of(
                MockJudge.supported("a"), MockJudge.refuted("b"), MockJudge.notEnoughInfo("c"));
        MetricResult result = metric.evaluate(input("answer"));
        assertEquals(1.0 / 3.0, result.score(), 1e-9);
    }

    @Test
    void emptyAnswer_isSkippedWithoutJudgeCall() {
        MetricResult result = metric.evaluate(input("  "));
        assertEquals(MetricStatus.SKIPPED, result.status());
        assertFalse(result.hasScore());
    }

    @Test
    void emptyContexts_scoresZeroWithoutJudgeCall() {
        MetricResult result = metric.evaluate(
                new EvaluationInput("c1", "q", "answer", List.of(), ""));
        assertEquals(MetricStatus.OK, result.status());
        assertEquals(0.0, result.score());
        assertTrue(result.message().isPresent());
    }

    @Test
    void judgeReturnsNoClaims_isJudgeFailed() {
        judge.faithfulnessVerdicts = List.of();
        MetricResult result = metric.evaluate(input("answer"));
        assertEquals(MetricStatus.JUDGE_FAILED, result.status());
        assertFalse(result.hasScore());
    }

    @Test
    void judgeThrows_isJudgeFailedWithMessage() {
        judge.faithfulnessError = new IllegalStateException("boom");
        MetricResult result = metric.evaluate(input("answer"));
        assertEquals(MetricStatus.JUDGE_FAILED, result.status());
        assertTrue(result.message().orElse("").contains("boom"));
    }
}
