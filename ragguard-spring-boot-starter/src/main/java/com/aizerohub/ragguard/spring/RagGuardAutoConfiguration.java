package com.aizerohub.ragguard.spring;

import com.aizerohub.ragguard.core.engine.EvaluationEngine;
import com.aizerohub.ragguard.core.judge.EmbeddingModel;
import com.aizerohub.ragguard.core.judge.Judge;
import com.aizerohub.ragguard.core.model.MetricType;
import com.aizerohub.ragguard.core.model.RagChain;
import com.aizerohub.ragguard.report.ReportWriter;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * RagGuard auto-configuration:
 *
 * <ul>
 *   <li>a {@link SpringAiJudge} bean when a Spring AI {@link ChatModel} exists,</li>
 *   <li>a core {@link EmbeddingModel} adapter when a Spring AI embedding model exists,</li>
 *   <li>a {@link RagGuardFacade} when the user additionally defines their own
 *       {@link RagChain} bean.</li>
 * </ul>
 *
 * <p>Users supply the {@code RagChain} bean (their RAG pipeline) and
 * optionally {@code ragguard.*} properties (test set location, report dir,
 * metric subset, parallelism).
 */
@AutoConfiguration
@ConditionalOnClass(ChatModel.class)
@ConditionalOnProperty(prefix = "ragguard", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RagGuardProperties.class)
public class RagGuardAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(Judge.class)
    public SpringAiJudge ragGuardJudge(ChatModel chatModel) {
        return new SpringAiJudge(chatModel);
    }

    @Bean
    @ConditionalOnMissingBean(EmbeddingModel.class)
    @ConditionalOnBean(org.springframework.ai.embedding.EmbeddingModel.class)
    public SpringAiEmbeddingAdapter ragGuardEmbeddingModel(
            org.springframework.ai.embedding.EmbeddingModel delegate) {
        return new SpringAiEmbeddingAdapter(delegate);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(RagChain.class)
    public RagGuardFacade ragGuardFacade(RagChain ragChain, Judge judge,
                                         ObjectProvider<EmbeddingModel> embeddingModel,
                                         RagGuardProperties properties) {
        Set<MetricType> metrics = resolveMetrics(properties);
        EmbeddingModel embedding = embeddingModel.getIfAvailable();
        if (embedding == null && metrics.contains(MetricType.ANSWER_RELEVANCE)) {
            metrics = EnumSet.copyOf(metrics);
            metrics.remove(MetricType.ANSWER_RELEVANCE);
            if (metrics.isEmpty()) {
                throw new IllegalStateException(
                        "ragguard.metrics only contains ANSWER_RELEVANCE, which needs an embedding model, "
                                + "but no Spring AI EmbeddingModel bean is defined");
            }
        }
        EvaluationEngine.Builder builder = EvaluationEngine.builder()
                .chain(ragChain)
                .judge(judge)
                .parallelism(properties.getParallelism());
        if (embedding != null) {
            builder.embedding(embedding);
        }
        builder.metrics(metrics.toArray(MetricType[]::new));
        Optional<ReportWriter> reportWriter = Optional.empty();
        if (properties.getReportOutputDir() != null && !properties.getReportOutputDir().isBlank()) {
            reportWriter = Optional.of(new ReportWriter(Path.of(properties.getReportOutputDir()),
                    properties.getReportTitle()));
        }
        return new RagGuardFacade(builder.build(), properties.getTestSet(), reportWriter);
    }

    private static Set<MetricType> resolveMetrics(RagGuardProperties properties) {
        if (properties.getMetrics() == null || properties.getMetrics().isEmpty()) {
            return EnumSet.allOf(MetricType.class);
        }
        EnumSet<MetricType> resolved = EnumSet.noneOf(MetricType.class);
        for (String name : properties.getMetrics()) {
            resolved.add(MetricType.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT)));
        }
        if (resolved.isEmpty()) {
            throw new IllegalStateException("ragguard.metrics contains no valid metric names: "
                    + properties.getMetrics());
        }
        return resolved;
    }
}
