package com.aizerohub.ragguard.core.metric;

import com.aizerohub.ragguard.core.judge.Judge;
import com.aizerohub.ragguard.core.model.ContextUsefulness;
import com.aizerohub.ragguard.core.model.EvaluationInput;
import com.aizerohub.ragguard.core.model.MetricResult;
import com.aizerohub.ragguard.core.model.MetricStatus;
import com.aizerohub.ragguard.core.model.MetricType;

import java.util.List;
import java.util.Objects;

/**
 * Context precision = average precision of the retrieved contexts (design
 * notes §4): the judge marks each context useful/not useful, and useful
 * contexts ranked earlier contribute a higher precision@i weight. This
 * directly measures BM25 + vector + rerank fusion quality.
 *
 * <p>Requires exactly one usefulness verdict per input context; any
 * mismatch marks the metric JUDGE_FAILED rather than silently scoring.
 */
public final class ContextPrecisionMetric implements Metric {

    private final Judge judge;

    public ContextPrecisionMetric(Judge judge) {
        this.judge = Objects.requireNonNull(judge, "judge");
    }

    @Override
    public MetricType type() {
        return MetricType.CONTEXT_PRECISION;
    }

    @Override
    public MetricResult evaluate(EvaluationInput input) {
        if (FaithfulnessMetric.isBlank(input.question())) {
            return MetricResult.builder(MetricType.CONTEXT_PRECISION)
                    .status(MetricStatus.SKIPPED)
                    .message("question is blank")
                    .build();
        }
        if (input.contexts().isEmpty()) {
            return MetricResult.builder(MetricType.CONTEXT_PRECISION)
                    .status(MetricStatus.SKIPPED)
                    .message("no contexts retrieved; average precision is undefined")
                    .build();
        }
        List<ContextUsefulness> verdicts;
        try {
            verdicts = judge.judgeContextPrecision(input.question(), input.contexts());
        } catch (RuntimeException e) {
            return MetricResult.builder(MetricType.CONTEXT_PRECISION)
                    .status(MetricStatus.JUDGE_FAILED)
                    .message("judge call failed: " + e.getMessage())
                    .build();
        }
        if (verdicts == null || verdicts.size() != input.contexts().size()) {
            return MetricResult.builder(MetricType.CONTEXT_PRECISION)
                    .status(MetricStatus.JUDGE_FAILED)
                    .message("judge returned " + (verdicts == null ? 0 : verdicts.size())
                            + " verdicts for " + input.contexts().size() + " contexts")
                    .build();
        }
        double sum = 0;
        long usefulCount = 0;
        long usefulSoFar = 0;
        for (int i = 0; i < verdicts.size(); i++) {
            if (verdicts.get(i).useful()) {
                usefulSoFar++;
                usefulCount++;
                sum += (double) usefulSoFar / (i + 1); // precision@rank
            }
        }
        double score = usefulCount == 0 ? 0.0 : sum / usefulCount;
        return MetricResult.builder(MetricType.CONTEXT_PRECISION)
                .score(score)
                .contextVerdicts(verdicts)
                .build();
    }
}
