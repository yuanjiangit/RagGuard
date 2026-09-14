package com.aizerohub.ragguard.core.model;

import java.util.List;
import java.util.Optional;

/**
 * The outcome of evaluating one metric for one test case. Carries the
 * score plus full drill-down detail (per-claim verdicts, per-context
 * usefulness, reverse questions) so every score can be explained.
 */
public final class MetricResult {

    private final MetricType metricType;
    private final MetricStatus status;
    private final double score; // NaN unless status == OK
    private final String message;

    private final List<ClaimVerdict> claimVerdicts;         // faithfulness
    private final List<ClaimAttribution> claimAttributions; // context recall
    private final List<ContextUsefulness> contextVerdicts;  // context precision
    private final List<ReverseQuestion> reverseQuestions;   // answer relevance

    private MetricResult(Builder b) {
        this.metricType = b.metricType;
        this.status = b.status;
        this.score = b.status == MetricStatus.OK ? b.score : Double.NaN;
        this.message = b.message;
        this.claimVerdicts = copyOf(b.claimVerdicts);
        this.claimAttributions = copyOf(b.claimAttributions);
        this.contextVerdicts = copyOf(b.contextVerdicts);
        this.reverseQuestions = copyOf(b.reverseQuestions);
    }

    public static Builder builder(MetricType metricType) {
        return new Builder(metricType);
    }

    public MetricType metricType() {
        return metricType;
    }

    public MetricStatus status() {
        return status;
    }

    /** The score in [0, 1], or NaN when status is not OK. */
    public double score() {
        return score;
    }

    /** True when the score is valid and can participate in aggregation/assertions. */
    public boolean hasScore() {
        return !Double.isNaN(score);
    }

    public Optional<String> message() {
        return Optional.ofNullable(message);
    }

    public List<ClaimVerdict> claimVerdicts() {
        return claimVerdicts;
    }

    public List<ClaimAttribution> claimAttributions() {
        return claimAttributions;
    }

    public List<ContextUsefulness> contextVerdicts() {
        return contextVerdicts;
    }

    public List<ReverseQuestion> reverseQuestions() {
        return reverseQuestions;
    }

    private static <T> List<T> copyOf(List<T> list) {
        return list == null ? List.of() : List.copyOf(list);
    }

    public static final class Builder {

        private final MetricType metricType;
        private MetricStatus status = MetricStatus.OK;
        private double score = Double.NaN;
        private String message;
        private List<ClaimVerdict> claimVerdicts;
        private List<ClaimAttribution> claimAttributions;
        private List<ContextUsefulness> contextVerdicts;
        private List<ReverseQuestion> reverseQuestions;

        private Builder(MetricType metricType) {
            this.metricType = metricType;
        }

        public Builder score(double score) {
            if (score < 0.0 || score > 1.0) {
                throw new IllegalArgumentException("metric score must be within [0, 1], got " + score);
            }
            this.score = score;
            return this;
        }

        public Builder status(MetricStatus status) {
            this.status = status;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder claimVerdicts(List<ClaimVerdict> claimVerdicts) {
            this.claimVerdicts = claimVerdicts;
            return this;
        }

        public Builder claimAttributions(List<ClaimAttribution> claimAttributions) {
            this.claimAttributions = claimAttributions;
            return this;
        }

        public Builder contextVerdicts(List<ContextUsefulness> contextVerdicts) {
            this.contextVerdicts = contextVerdicts;
            return this;
        }

        public Builder reverseQuestions(List<ReverseQuestion> reverseQuestions) {
            this.reverseQuestions = reverseQuestions;
            return this;
        }

        public MetricResult build() {
            if (status == MetricStatus.OK && Double.isNaN(score)) {
                throw new IllegalStateException("OK metric result requires a score: " + metricType);
            }
            return new MetricResult(this);
        }
    }

    @Override
    public String toString() {
        return "MetricResult{" + metricType + ", status=" + status
                + ", score=" + (hasScore() ? String.valueOf(score) : "n/a")
                + (message != null ? ", message=" + message : "") + "}";
    }
}
