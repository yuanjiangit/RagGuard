package com.aizerohub.ragguard.core.metric;

import com.aizerohub.ragguard.core.judge.Judge;
import com.aizerohub.ragguard.core.model.ClaimAttribution;
import com.aizerohub.ragguard.core.model.EvaluationInput;
import com.aizerohub.ragguard.core.model.MetricResult;
import com.aizerohub.ragguard.core.model.MetricStatus;
import com.aizerohub.ragguard.core.model.MetricType;

import java.util.List;
import java.util.Objects;

/**
 * Context recall = attributable claims / total claims (design notes §3):
 * the expected answer is decomposed into atomic claims and each is judged
 * on whether it can be attributed to one of the retrieved contexts. Measures
 * retrieval coverage — independent of the generated answer.
 *
 * <p>Note: the {@code expectedContexts} test-set field does NOT participate
 * in this algorithm (see design notes §3 for why it is kept in the schema).
 *
 * <p>Short-circuits without calling the judge: blank expected answer →
 * SKIPPED; empty contexts → score 0 (nothing can be attributed).
 */
public final class ContextRecallMetric implements Metric {

    private final Judge judge;

    public ContextRecallMetric(Judge judge) {
        this.judge = Objects.requireNonNull(judge, "judge");
    }

    @Override
    public MetricType type() {
        return MetricType.CONTEXT_RECALL;
    }

    @Override
    public MetricResult evaluate(EvaluationInput input) {
        if (FaithfulnessMetric.isBlank(input.expectedAnswer())) {
            return MetricResult.builder(MetricType.CONTEXT_RECALL)
                    .status(MetricStatus.SKIPPED)
                    .message("expected answer is blank")
                    .build();
        }
        if (input.contexts().isEmpty()) {
            return MetricResult.builder(MetricType.CONTEXT_RECALL)
                    .score(0)
                    .message("no contexts retrieved; nothing can be attributed")
                    .build();
        }
        List<ClaimAttribution> attributions;
        try {
            attributions = judge.attributeContextRecall(input.expectedAnswer(), input.contexts());
        } catch (RuntimeException e) {
            return MetricResult.builder(MetricType.CONTEXT_RECALL)
                    .status(MetricStatus.JUDGE_FAILED)
                    .message("judge call failed: " + e.getMessage())
                    .build();
        }
        if (attributions == null || attributions.isEmpty()) {
            return MetricResult.builder(MetricType.CONTEXT_RECALL)
                    .status(MetricStatus.JUDGE_FAILED)
                    .message("judge returned no claims")
                    .build();
        }
        long attributable = attributions.stream().filter(ClaimAttribution::attributable).count();
        double score = (double) attributable / attributions.size();
        return MetricResult.builder(MetricType.CONTEXT_RECALL)
                .score(score)
                .claimAttributions(attributions)
                .build();
    }
}
