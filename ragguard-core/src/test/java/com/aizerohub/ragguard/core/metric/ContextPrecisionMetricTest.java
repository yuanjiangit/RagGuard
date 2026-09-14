package com.aizerohub.ragguard.core.metric;

import com.aizerohub.ragguard.core.model.ContextUsefulness;
import com.aizerohub.ragguard.core.model.EvaluationInput;
import com.aizerohub.ragguard.core.model.MetricResult;
import com.aizerohub.ragguard.core.model.MetricStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextPrecisionMetricTest {

    private final MockJudge judge = new MockJudge();
    private final ContextPrecisionMetric metric = new ContextPrecisionMetric(judge);

    private static EvaluationInput input(int contextCount) {
        List<String> contexts = new java.util.ArrayList<>(contextCount);
        for (int i = 0; i < contextCount; i++) {
            contexts.add("ctx" + i);
        }
        return new EvaluationInput("c1", "q", "answer", contexts, "expected");
    }

    private static ContextUsefulness useful(int index) {
        return new ContextUsefulness(index, true, "helps answer");
    }

    private static ContextUsefulness useless(int index) {
        return new ContextUsefulness(index, false, "irrelevant");
    }

    @Test
    void usefulAtRanksOneAndThree_averagePrecision() {
        // AP = (1/2) * (precision@1 + precision@3) = (1/2) * (1 + 2/3) ≈ 0.833
        judge.precisionVerdicts = List.of(useful(0), useless(1), useful(2));
        MetricResult result = metric.evaluate(input(3));
        assertEquals(MetricStatus.OK, result.status());
        assertEquals(5.0 / 6.0, result.score(), 1e-9);
    }

    @Test
    void usefulFirstScoresHigherThanUsefulLast() {
        judge.precisionVerdicts = List.of(useful(0), useful(1), useless(2));
        double goodRanking = metric.evaluate(input(3)).score();
        judge.precisionVerdicts = List.of(useless(0), useless(1), useful(2));
        double badRanking = metric.evaluate(input(3)).score();
        assertEquals(1.0, goodRanking, 1e-9);
        assertEquals((1.0 / 3.0), badRanking, 1e-9);
        assertTrue(goodRanking > badRanking);
    }

    @Test
    void noUsefulContexts_scoresZero() {
        judge.precisionVerdicts = List.of(useless(0), useless(1));
        MetricResult result = metric.evaluate(input(2));
        assertEquals(MetricStatus.OK, result.status());
        assertEquals(0.0, result.score());
    }

    @Test
    void emptyContexts_isSkipped() {
        MetricResult result = metric.evaluate(new EvaluationInput("c1", "q", "a", List.of(), "e"));
        assertEquals(MetricStatus.SKIPPED, result.status());
        assertFalse(result.hasScore());
    }

    @Test
    void verdictCountMismatch_isJudgeFailed() {
        judge.precisionVerdicts = List.of(useful(0));
        MetricResult result = metric.evaluate(input(2));
        assertEquals(MetricStatus.JUDGE_FAILED, result.status());
        assertTrue(result.message().orElse("").contains("1 verdicts for 2 contexts"));
    }

    @Test
    void judgeThrows_isJudgeFailedWithMessage() {
        judge.precisionError = new IllegalStateException("boom");
        MetricResult result = metric.evaluate(input(2));
        assertEquals(MetricStatus.JUDGE_FAILED, result.status());
        assertTrue(result.message().orElse("").contains("boom"));
    }
}
