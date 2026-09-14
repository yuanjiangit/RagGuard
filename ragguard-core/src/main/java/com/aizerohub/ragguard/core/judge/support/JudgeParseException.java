package com.aizerohub.ragguard.core.judge.support;

/**
 * Thrown when a judge response cannot be parsed or violates the expected
 * JSON schema. Framework adapters translate this into a retry (with an
 * "only JSON" instruction) and finally into {@code MetricStatus.JUDGE_FAILED}.
 */
public class JudgeParseException extends RuntimeException {

    public JudgeParseException(String message) {
        super(message);
    }

    public JudgeParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
