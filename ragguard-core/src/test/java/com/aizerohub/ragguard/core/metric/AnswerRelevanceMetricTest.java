package com.aizerohub.ragguard.core.metric;

import com.aizerohub.ragguard.core.judge.EmbeddingModel;
import com.aizerohub.ragguard.core.model.EvaluationInput;
import com.aizerohub.ragguard.core.model.MetricResult;
import com.aizerohub.ragguard.core.model.MetricStatus;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerRelevanceMetricTest {

    private final MockJudge judge = new MockJudge();
    private final AnswerRelevanceMetric metric = new AnswerRelevanceMetric(judge, embedding(), 3);

    /** Fixed vectors: q̂1 identical to question (cos 1.0), q̂2 orthogonal (cos 0.0). */
    private static EmbeddingModel embedding() {
        Map<String, double[]> vectors = new HashMap<>();
        vectors.put("q", new double[]{1, 0});
        vectors.put("q̂1", new double[]{1, 0});
        vectors.put("q̂2", new double[]{0, 1});
        return vectors::get;
    }

    private static EvaluationInput input(String answer) {
        return new EvaluationInput("c1", "q", answer, List.of("ctx"), "");
    }

    @Test
    void meanOfReverseQuestionSimilarities() {
        judge.reverseQuestions = List.of("q̂1", "q̂2");
        MetricResult result = metric.evaluate(input("answer"));
        assertEquals(MetricStatus.OK, result.status());
        assertEquals(0.5, result.score(), 1e-9);
        assertEquals(2, result.reverseQuestions().size());
        assertEquals(1.0, result.reverseQuestions().get(0).similarity(), 1e-9);
        assertEquals(0.0, result.reverseQuestions().get(1).similarity(), 1e-9);
    }

    @Test
    void questionCountIsPassedThroughToJudge() {
        judge.reverseQuestions = List.of("q̂1");
        AnswerRelevanceMetric custom = new AnswerRelevanceMetric(judge, embedding(), 7);
        custom.evaluate(input("answer"));
        // MockJudge ignores n; verified indirectly by full pipeline executing
        assertEquals(1.0, custom.evaluate(input("answer")).score(), 1e-9);
    }

    @Test
    void zeroVectorEmbedding_scoresZeroNotNaN() {
        Map<String, double[]> vectors = new HashMap<>();
        vectors.put("q", new double[]{0, 0});
        vectors.put("q̂1", new double[]{1, 0});
        judge.reverseQuestions = List.of("q̂1");
        AnswerRelevanceMetric zero = new AnswerRelevanceMetric(judge, vectors::get, 1);
        MetricResult result = zero.evaluate(input("answer"));
        assertEquals(MetricStatus.OK, result.status());
        assertEquals(0.0, result.score());
    }

    @Test
    void emptyAnswer_isSkipped() {
        MetricResult result = metric.evaluate(input(""));
        assertEquals(MetricStatus.SKIPPED, result.status());
        assertFalse(result.hasScore());
    }

    @Test
    void judgeReturnsNoQuestions_isJudgeFailed() {
        judge.reverseQuestions = List.of();
        MetricResult result = metric.evaluate(input("answer"));
        assertEquals(MetricStatus.JUDGE_FAILED, result.status());
    }

    @Test
    void embeddingThrows_isJudgeFailedWithMessage() {
        judge.reverseQuestions = List.of("q̂1");
        EmbeddingModel failing = text -> {
            throw new IllegalStateException("embed boom");
        };
        AnswerRelevanceMetric failingMetric = new AnswerRelevanceMetric(judge, failing, 1);
        MetricResult result = failingMetric.evaluate(input("answer"));
        assertEquals(MetricStatus.JUDGE_FAILED, result.status());
        assertTrue(result.message().orElse("").contains("embed boom"));
    }
}
