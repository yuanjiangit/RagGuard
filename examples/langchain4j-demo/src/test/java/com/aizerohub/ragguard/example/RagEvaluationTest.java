package com.aizerohub.ragguard.example;

import com.aizerohub.ragguard.core.judge.EmbeddingModel;
import com.aizerohub.ragguard.core.judge.Judge;
import com.aizerohub.ragguard.core.model.RagChain;
import com.aizerohub.ragguard.langchain4j.LangChain4jEmbeddingAdapter;
import com.aizerohub.ragguard.langchain4j.LangChain4jJudge;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.List;

/**
 * langchain4j end-to-end evaluation, NO Spring involved: the
 * ragguard-junit5 extension drives everything through supplier annotations.
 * Requires OPENAI_API_KEY — skipped otherwise.
 */
@com.aizerohub.ragguard.junit5.RagTest(testSet = "demo-test-set.yml")
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class RagEvaluationTest {

    @com.aizerohub.ragguard.junit5.RagChainSupplier
    static RagChain chain() {
        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-4o-mini")
                .temperature(0.0)
                .timeout(Duration.ofSeconds(60))
                .build();
        return new DemoRagChain(chatModel);
    }

    @com.aizerohub.ragguard.junit5.RagJudgeSupplier
    static Judge judge() {
        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-4o-mini")
                .temperature(0.0)
                .timeout(Duration.ofSeconds(60))
                .build();
        return new LangChain4jJudge(chatModel);
    }

    @com.aizerohub.ragguard.junit5.RagEmbeddingSupplier
    static EmbeddingModel embedding() {
        return new LangChain4jEmbeddingAdapter(OpenAiEmbeddingModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("text-embedding-3-small")
                .timeout(Duration.ofSeconds(60))
                .build());
    }

    @Test
    void qualityGate(com.aizerohub.ragguard.core.model.EvaluationReport report) {
        com.aizerohub.ragguard.junit5.RagAssertions.assertMetricAtLeast(
                report, com.aizerohub.ragguard.core.model.MetricType.FAITHFULNESS, 0.7);
        com.aizerohub.ragguard.junit5.RagAssertions.assertMetricAtLeast(
                report, com.aizerohub.ragguard.core.model.MetricType.CONTEXT_RECALL, 0.5);
    }
}
