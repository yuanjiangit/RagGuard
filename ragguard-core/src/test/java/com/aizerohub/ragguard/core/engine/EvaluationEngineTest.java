package com.aizerohub.ragguard.core.engine;

import com.aizerohub.ragguard.core.metric.MockJudge;
import com.aizerohub.ragguard.core.model.CaseResult;
import com.aizerohub.ragguard.core.model.EvaluationReport;
import com.aizerohub.ragguard.core.model.MetricResult;
import com.aizerohub.ragguard.core.model.MetricStatus;
import com.aizerohub.ragguard.core.model.MetricType;
import com.aizerohub.ragguard.core.model.RagAnswer;
import com.aizerohub.ragguard.core.model.RagChain;
import com.aizerohub.ragguard.core.model.RagTestCase;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluationEngineTest {

    private final MockJudge judge = new MockJudge();

    private EvaluationEngine engine(RagChain chain, int parallelism) {
        return EvaluationEngine.builder()
                .chain(chain)
                .judge(judge)
                .embedding(text -> new double[] {1, 0})
                .parallelism(parallelism)
                .build();
    }

    private void primeJudge() {
        judge.faithfulnessVerdicts = List.of(MockJudge.supported("s1"));
        judge.recallAttributions = List.of(
                new com.aizerohub.ragguard.core.model.ClaimAttribution(
                        new com.aizerohub.ragguard.core.model.Claim("s1"), true, 0, "ok"));
        judge.precisionVerdicts = List.of(
                new com.aizerohub.ragguard.core.model.ContextUsefulness(0, true, "ok"));
        judge.reverseQuestions = List.of("q̂1");
    }

    private static final RagChain STUB = q -> new RagAnswer("answer to " + q, List.of("ctx"));

    @Test
    void runsAllFourMetrics_perCaseAndAggregate() {
        primeJudge();
        EvaluationReport report = engine(STUB, 1).run(List.of(
                new RagTestCase("q1", "question one", "expected", List.of()),
                RagTestCase.of("question two")));
        assertEquals(2, report.caseCount());
        for (MetricType type : MetricType.values()) {
            assertEquals(1.0, report.aggregateScore(type), 1e-9, type.name());
        }
        assertEquals(1.0, report.overallScore(), 1e-9);
    }

    @Test
    void parallelResultsKeepTestCaseOrder() {
        primeJudge();
        List<RagTestCase> cases = new java.util.ArrayList<>();
        for (int i = 0; i < 12; i++) {
            cases.add(new RagTestCase("q" + i, "question " + i, null, List.of()));
        }
        EvaluationReport report = engine(STUB, 4).run(cases);
        for (int i = 0; i < cases.size(); i++) {
            assertEquals("q" + i, report.caseResults().get(i).caseId());
        }
    }

    @Test
    void missingEmbeddingWithAnswerRelevance_failsBuild() {
        assertThrows(NullPointerException.class, () -> EvaluationEngine.builder()
                .chain(STUB).judge(judge).build());
    }

    @Test
    void metricSubset_respected() {
        primeJudge();
        EvaluationEngine engine = EvaluationEngine.builder()
                .chain(STUB).judge(judge).metrics(MetricType.FAITHFULNESS)
                .build();
        EvaluationReport report = engine.run(List.of(RagTestCase.of("q")));
        CaseResult caseResult = report.caseResults().get(0);
        assertEquals(1, caseResult.metricResults().size());
        assertEquals(MetricType.FAITHFULNESS, caseResult.metricResults().get(0).metricType());
    }

    @Test
    void chainFailure_marksAllMetricsError() {
        RagChain failing = q -> {
            throw new IllegalStateException("chain down");
        };
        EvaluationReport report = engine(failing, 1).run(List.of(RagTestCase.of("q")));
        CaseResult caseResult = report.caseResults().get(0);
        assertEquals(MetricType.values().length, caseResult.metricResults().size());
        for (MetricResult mr : caseResult.metricResults()) {
            assertEquals(MetricStatus.ERROR, mr.status());
            assertTrue(mr.message().orElse("").contains("chain down"));
        }
    }

    @Test
    void blankQuestion_rejected() {
        assertThrows(IllegalArgumentException.class, () -> engine(STUB, 1).run(List.of(RagTestCase.of(" "))));
    }

    @Test
    void emptyCaseList_rejected() {
        assertThrows(IllegalArgumentException.class, () -> engine(STUB, 1).run(List.of()));
    }

    @Test
    void skippedCasesExcludedFromAggregateButReported() {
        primeJudge();
        judge.faithfulnessVerdicts = null;
        judge.faithfulnessError = new IllegalStateException("boom");
        // case with blank answer → faithfulness SKIPPED, others still OK
        EvaluationReport report = engine(STUB, 1).run(List.of(new RagTestCase(null, "q", "expected", List.of())));
        assertEquals("case-1", report.caseResults().get(0).caseId());
        assertEquals(MetricStatus.OK, report.caseResults().get(0).metric(MetricType.CONTEXT_RECALL)
                .orElseThrow().status());
    }

    @Test
    void parallelismStillAggregatesCorrectly() {
        primeJudge();
        List<RagTestCase> cases = List.of(
                new RagTestCase("q1", "question one", "expected", List.of()),
                new RagTestCase("q2", "question two", "expected", List.of()),
                new RagTestCase("q3", "question three", "expected", List.of()));
        EvaluationReport report = engine(STUB, 4).run(cases);
        assertEquals(1.0, report.aggregateScore(MetricType.FAITHFULNESS), 1e-9);
    }

    @Test
    void embeddingLookupMiss_scoresZeroSimilarity() {
        primeJudge();
        Map<String, double[]> vectors = new HashMap<>();
        vectors.put("q̂1", new double[] {1, 0});
        // the question is not in the map → zero vector → cosine 0 → answer relevance 0
        EvaluationEngine engine = EvaluationEngine.builder()
                .chain(STUB).judge(judge)
                .embedding(text -> vectors.getOrDefault(text, new double[] {0, 0}))
                .build();
        EvaluationReport report = engine.run(List.of(new RagTestCase("q1", "question one", "e", List.of())));
        assertEquals(0.0, report.aggregateScore(MetricType.ANSWER_RELEVANCE), 1e-9);
    }
}
