package com.aizerohub.ragguard.spring;

import com.aizerohub.ragguard.core.model.EvaluationReport;
import com.aizerohub.ragguard.core.model.MetricType;
import com.aizerohub.ragguard.core.model.RagAnswer;
import com.aizerohub.ragguard.core.model.RagChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RagGuardAutoConfigurationTest {

    private static final String FAITHFULNESS_JSON = """
            {"claims":[{"text":"RagGuard evaluates RAG apps","verdict":"SUPPORTED","reason":"stated"}]}
            """;
    private static final String RECALL_JSON = """
            {"claims":[{"text":"evaluation framework","attributable":true,"contextIndex":0,"reason":"ok"}]}
            """;
    private static final String PRECISION_JSON = """
            {"contexts":[{"index":0,"useful":true,"reason":"relevant"}]}
            """;
    private static final String REVERSE_JSON = """
            {"questions":["What is RagGuard?"]}
            """;

    private static final Map<String, String> CANNED_JUDGE = Map.of(
            "decompose the ANSWER", FAITHFULNESS_JSON,
            "decompose the EXPECTED ANSWER", RECALL_JSON,
            "judge whether each retrieved context", PRECISION_JSON,
            "plausible questions", REVERSE_JSON);

    private static final RagChain STUB_CHAIN =
            question -> new RagAnswer("RagGuard evaluates RAG apps.", List.of("RagGuard evaluates RAG applications."));

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(FakeModels.class)
            .withBean("ragChain", RagChain.class, () -> STUB_CHAIN)
            .withBean(FakeModels.FakeChatModel.class, () -> new FakeModels.FakeChatModel(CANNED_JUDGE))
            .withBean(FakeModels.FakeEmbeddingModel.class, FakeModels.FakeEmbeddingModel::new)
            .withUserConfiguration(RagGuardAutoConfiguration.class);

    @Test
    void facadeCreatedAndRunsEndToEnd() {
        runner.withPropertyValues(
                        "ragguard.test-set=classpath:starter-test-set.yml",
                        "ragguard.parallelism=1"
                ).run(context -> {
                    assertThat(context).hasSingleBean(RagGuardFacade.class);
                    assertThat(context).hasSingleBean(SpringAiJudge.class);
                    EvaluationReport report = context.getBean(RagGuardFacade.class).run();
                    org.junit.jupiter.api.Assertions.assertEquals(2, report.caseCount());
                    org.junit.jupiter.api.Assertions.assertEquals(1.0,
                            report.aggregateScore(MetricType.FAITHFULNESS), 1e-9);
                    org.junit.jupiter.api.Assertions.assertEquals(1.0,
                            report.aggregateScore(MetricType.ANSWER_RELEVANCE), 1e-9);
                });
    }

    @Test
    void reportWrittenToConfiguredDir(@TempDir Path tempDir) throws Exception {
        runner.withPropertyValues(
                        "ragguard.test-set=classpath:starter-test-set.yml",
                        "ragguard.parallelism=1",
                        "ragguard.report-output-dir=" + tempDir.toString().replace('\\', '/')
                ).run(context -> {
                    context.getBean(RagGuardFacade.class).run();
                    assertThat(tempDir).exists();
                    try (var files = java.nio.file.Files.list(tempDir)) {
                        assertThat(files.count()).isEqualTo(2); // latest-run json + html
                    }
                    try (var files = java.nio.file.Files.list(tempDir)) {
                        assertThat(files.findFirst().orElseThrow().getFileName().toString())
                                .matches("ragguard-report-\\d{8}-\\d{6}\\.html|ragguard-latest-run\\.json");
                    }
                });
    }

    @Test
    void noRagChainBean_noFacade() {
        new ApplicationContextRunner()
                .withBean(FakeModels.FakeChatModel.class,
                        () -> new FakeModels.FakeChatModel(Map.of("decompose the ANSWER", FAITHFULNESS_JSON)))
                .withUserConfiguration(RagGuardAutoConfiguration.class)
                .run(context -> assertThat(context).doesNotHaveBean(RagGuardFacade.class));
    }

    @Test
    void disabledByProperty_noBeans() {
        runner.withPropertyValues("ragguard.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RagGuardFacade.class);
                    assertThat(context).doesNotHaveBean(SpringAiJudge.class);
                });
    }

    @Test
    void metricSubset_respected() {
        runner.withPropertyValues(
                        "ragguard.test-set=classpath:starter-test-set.yml",
                        "ragguard.metrics=FAITHFULNESS,CONTEXT_PRECISION"
                ).run(context -> {
                    EvaluationReport report = context.getBean(RagGuardFacade.class).run();
                    for (var caseResult : report.caseResults()) {
                        assertThat(caseResult.metricResults())
                                .extracting("metricType")
                                .containsExactly(MetricType.FAITHFULNESS, MetricType.CONTEXT_PRECISION);
                    }
                });
    }
}
