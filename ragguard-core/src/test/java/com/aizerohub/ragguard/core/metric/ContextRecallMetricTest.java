package com.aizerohub.ragguard.core.metric;

import com.aizerohub.ragguard.core.model.Claim;
import com.aizerohub.ragguard.core.model.ClaimAttribution;
import com.aizerohub.ragguard.core.model.EvaluationInput;
import com.aizerohub.ragguard.core.model.MetricResult;
import com.aizerohub.ragguard.core.model.MetricStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextRecallMetricTest {

    private final MockJudge judge = new MockJudge();
    private final ContextRecallMetric metric = new ContextRecallMetric(judge);

    private static EvaluationInput input() {
        return new EvaluationInput("c1", "q", "answer", List.of("ctx1", "ctx2"), "expected answer");
    }

    private static ClaimAttribution attributed(String text, int index) {
        return new ClaimAttribution(new Claim(text), true, index, "in context " + index);
    }

    private static ClaimAttribution notAttributed(String text) {
        return new ClaimAttribution(new Claim(text), false, -1, "missing from contexts");
    }

    @Test
    void attributableClaimsOverTotal() {
        judge.recallAttributions = List.of(
                attributed("s1", 0), notAttributed("s2"), attributed("s3", 1));
        MetricResult result = metric.evaluate(input());
        assertEquals(MetricStatus.OK, result.status());
        assertEquals(2.0 / 3.0, result.score(), 1e-9);
        assertEquals(3, result.claimAttributions().size());
    }

    @Test
    void emptyExpectedAnswer_isSkipped() {
        MetricResult result = metric.evaluate(
                new EvaluationInput("c1", "q", "answer", List.of("ctx"), " "));
        assertEquals(MetricStatus.SKIPPED, result.status());
        assertFalse(result.hasScore());
    }

    @Test
    void emptyContexts_scoresZeroWithoutJudgeCall() {
        MetricResult result = metric.evaluate(
                new EvaluationInput("c1", "q", "answer", List.of(), "expected"));
        assertEquals(MetricStatus.OK, result.status());
        assertEquals(0.0, result.score());
    }

    @Test
    void judgeReturnsNoClaims_isJudgeFailed() {
        judge.recallAttributions = List.of();
        MetricResult result = metric.evaluate(input());
        assertEquals(MetricStatus.JUDGE_FAILED, result.status());
    }

    @Test
    void judgeThrows_isJudgeFailedWithMessage() {
        judge.recallError = new IllegalStateException("boom");
        MetricResult result = metric.evaluate(input());
        assertEquals(MetricStatus.JUDGE_FAILED, result.status());
        assertTrue(result.message().orElse("").contains("boom"));
    }
}
