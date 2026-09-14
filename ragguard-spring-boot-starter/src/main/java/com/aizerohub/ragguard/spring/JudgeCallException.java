package com.aizerohub.ragguard.spring;

/**
 * Thrown when the judge model cannot produce a parseable, schema-valid
 * response after retries. Metrics translate this into
 * {@code MetricStatus.JUDGE_FAILED} instead of silently scoring.
 */
public class JudgeCallException extends RuntimeException {

    public JudgeCallException(String message) {
        super(message);
    }

    public JudgeCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
