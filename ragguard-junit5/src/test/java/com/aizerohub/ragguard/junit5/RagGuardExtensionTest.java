package com.aizerohub.ragguard.junit5;

import com.aizerohub.ragguard.core.judge.Judge;
import com.aizerohub.ragguard.core.model.Claim;
import com.aizerohub.ragguard.core.model.ClaimAttribution;
import com.aizerohub.ragguard.core.model.ClaimVerdict;
import com.aizerohub.ragguard.core.model.ContextUsefulness;
import com.aizerohub.ragguard.core.model.EvaluationReport;
import com.aizerohub.ragguard.core.model.MetricType;
import com.aizerohub.ragguard.core.model.RagAnswer;
import com.aizerohub.ragguard.core.model.RagChain;
import com.aizerohub.ragguard.core.model.SupportVerdict;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End-to-end extension test: {@code @RagTest} evaluates the bundled test set
 * once and injects the report into test parameters.
 */
@RagTest(testSet = "junit-test-set.yml")
class RagGuardExtensionTest {

    @RagChainSupplier
    static RagChain chain() {
        return question -> new RagAnswer("RagGuard is a regression testing framework for RAG.",
                List.of("RagGuard turns RAG evaluation into unit tests."));
    }

    @RagJudgeSupplier
    static Judge judge() {
        MockJudge mock = new MockJudge();
        mock.faithfulnessVerdicts = List.of(
                new ClaimVerdict(new Claim("RagGuard is a regression testing framework"),
                        SupportVerdict.SUPPORTED, "stated in context"));
        mock.recallAttributions = List.of(
                new ClaimAttribution(new Claim("RagGuard is a regression testing framework"),
                        true, 0, "stated in context"));
        mock.precisionVerdicts = List.of(new ContextUsefulness(0, true, "directly relevant"));
        mock.reverseQuestions = List.of("What is RagGuard?");
        return mock;
    }

    @RagEmbeddingSupplier
    static com.aizerohub.ragguard.core.judge.EmbeddingModel embedding() {
        return text -> new double[] {1, 0};
    }

    @Test
    void reportIsInjectedAndEvaluated(EvaluationReport report) {
        assertEquals(2, report.caseCount());
        assertEquals(1.0, report.aggregateScore(MetricType.FAITHFULNESS), 1e-9);
        assertEquals(1.0, report.aggregateScore(MetricType.CONTEXT_RECALL), 1e-9);
        assertEquals(1.0, report.aggregateScore(MetricType.CONTEXT_PRECISION), 1e-9);
        assertEquals(1.0, report.aggregateScore(MetricType.ANSWER_RELEVANCE), 1e-9);
    }

    @Test
    void thresholdsAreAssertable(EvaluationReport report) {
        RagAssertions.assertMetricAtLeast(report, MetricType.FAITHFULNESS, 0.8);
        RagAssertions.assertCaseMetricAtLeast(report, "q2", MetricType.CONTEXT_RECALL, 0.8);
    }
}
