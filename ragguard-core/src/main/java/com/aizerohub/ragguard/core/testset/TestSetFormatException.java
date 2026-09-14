package com.aizerohub.ragguard.core.testset;

/**
 * Thrown when a test set file is missing, malformed, or fails validation.
 */
public class TestSetFormatException extends RuntimeException {

    public TestSetFormatException(String message) {
        super(message);
    }

    public TestSetFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
