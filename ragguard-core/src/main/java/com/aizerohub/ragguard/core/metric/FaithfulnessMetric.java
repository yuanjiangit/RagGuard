package com.aizerohub.ragguard.core.metric;

import com.aizerohub.ragguard.core.judge.Judge;
import com.aizerohub.ragguard.core.model.ClaimVerdict;
import com.aizerohub.ragguard.core.model.EvaluationInput;
import com.aizerohub.ragguard.core.model.MetricResult;
import com.aizerohub.ragguard.core.model.MetricStatus;
import com.aizerohub.ragguard.core.model.MetricType;
import com.aizerohub.ragguard.core.model.SupportVerdict;

import java.util.List;
import java.util.Objects;

/**
 * Faithfulness = supported claims / total claims. The judge decomposes the
 * answer into atomic claims and verifies each against the retrieved
 * contexts in one structured call. REFUTED and NOT_ENOUGH_INFO both count
 * towards the denominator (see design notes §1).
 *
 * <p>Short-circuits without calling the judge:
 * blank answer → SKIPPED; empty contexts → score 0 (nothing can support a claim).
 */
public final class FaithfulnessMetric implements Metric {

    private final Judge judge;

    public FaithfulnessMetric(Judge judge) {
        this.judge = Objects.requireNonNull(judge, "judge");
    }

    @Override
    public MetricType type() {
        return MetricType.FAITHFULNESS;
    }

    @Override
    public MetricResult evaluate(EvaluationInput input) {
        if (isBlank(input.answer())) {
            return skipped("answer is blank");
        }
        if (input.contexts().isEmpty()) {
            return MetricResult.builder(MetricType.FAITHFULNESS)
                    .score(0)
                    .message("no contexts retrieved; claims cannot be supported")
                    .build();
        }
        List<ClaimVerdict> verdicts;
        try {
            verdicts = judge.verifyFaithfulness(input.answer(), input.contexts());
        } catch (RuntimeException e) {
            return judgeFailed("judge call failed: " + e.getMessage());
        }
        if (verdicts == null || verdicts.isEmpty()) {
            return judgeFailed("judge returned no claims");
        }
        long supported = verdicts.stream()
                .filter(v -> v.verdict() == SupportVerdict.SUPPORTED)
                .count();
        double score = (double) supported / verdicts.size();
        return MetricResult.builder(MetricType.FAITHFULNESS)
                .score(score)
                .claimVerdicts(verdicts)
                .build();
    }

    private static MetricResult skipped(String message) {
        return MetricResult.builder(MetricType.FAITHFULNESS)
                .status(MetricStatus.SKIPPED)
                .message(message)
                .build();
    }

    private static MetricResult judgeFailed(String message) {
        return MetricResult.builder(MetricType.FAITHFULNESS)
                .status(MetricStatus.JUDGE_FAILED)
                .message(message)
                .build();
    }

    static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
