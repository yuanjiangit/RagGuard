package com.aizerohub.ragguard.example;

import com.aizerohub.ragguard.core.model.EvaluationReport;
import com.aizerohub.ragguard.core.model.MetricType;
import com.aizerohub.ragguard.junit5.RagAssertions;
import com.aizerohub.ragguard.spring.RagGuardFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The RAG regression gate: runs the demo test set against the real chain
 * (OpenAI + Elasticsearch) and fails on quality regressions.
 *
 * <p>Requires OPENAI_API_KEY and a running Elasticsearch (see application.yml);
 * skipped in CI and local builds without credentials. Ingest first with the
 * {@code ingest} profile.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class RagEvaluationTest {

    @Autowired
    RagGuardFacade ragGuard;

    @Test
    void qualityGate() {
        EvaluationReport report = ragGuard.run();
        RagAssertions.assertMetricAtLeast(report, MetricType.FAITHFULNESS, 0.7);
        RagAssertions.assertMetricAtLeast(report, MetricType.CONTEXT_RECALL, 0.6);
        RagAssertions.assertMetricAtLeast(report, MetricType.CONTEXT_PRECISION, 0.6);
        // answer relevance needs the embedding adapter; assert only when scored
        if (!Double.isNaN(report.aggregateScore(MetricType.ANSWER_RELEVANCE))) {
            RagAssertions.assertMetricAtLeast(report, MetricType.ANSWER_RELEVANCE, 0.5);
        }
        assertTrue(report.caseCount() > 0);
    }
}
