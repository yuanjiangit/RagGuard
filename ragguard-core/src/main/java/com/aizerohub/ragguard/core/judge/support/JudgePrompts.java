package com.aizerohub.ragguard.core.judge.support;

import java.util.List;

/**
 * Built-in judge prompts (open-source and replaceable — community
 * calibration is a design goal). Shared verbatim by all framework adapters
 * so scores stay comparable across Spring AI and langchain4j.
 *
 * <p>Prompts are versioned; {@link #PROMPT_VERSION} is meant to participate
 * in the judge-result cache key once caching lands. Bump it on any change.
 */
public final class JudgePrompts {

    /** Version of the built-in judge prompts; bump on any prompt change. */
    public static final String PROMPT_VERSION = "v1";

    public static final String FAITHFULNESS_SYSTEM = """
            You are a strict evaluator for retrieval-augmented generation systems.
            TASK: decompose the ANSWER into atomic claims, then verify each claim
            against the CONTEXTS only.
            An atomic claim is an indivisible factual statement; a sentence with two
            facts becomes two claims.
            Verdicts: SUPPORTED (the contexts entail the claim), REFUTED (the contexts
            contradict the claim), NOT_ENOUGH_INFO (the contexts do not cover it).
            Respond with ONLY a JSON object, no prose, in this exact schema:
            {"claims":[{"text":"<claim>","verdict":"SUPPORTED|REFUTED|NOT_ENOUGH_INFO","reason":"<short reason>"}]}
            """;

    public static final String RECALL_SYSTEM = """
            You are a strict evaluator for retrieval-augmented generation systems.
            TASK: decompose the EXPECTED ANSWER into atomic claims, then attribute
            each claim to one of the CONTEXTS.
            A claim is attributable if and only if a context contains the information
            needed to support it (paraphrase counts; nothing else does).
            contextIndex is the 0-based index of the attributing context, or -1 when
            not attributable.
            Respond with ONLY a JSON object, no prose, in this exact schema:
            {"claims":[{"text":"<claim>","attributable":true|false,"contextIndex":<int>,"reason":"<short reason>"}]}
            """;

    public static final String PRECISION_SYSTEM = """
            You are a strict evaluator for retrieval-augmented generation systems.
            TASK: judge whether each retrieved context is USEFUL for answering the
            QUESTION. Judge every context; do not skip any.
            Respond with ONLY a JSON object, no prose, in this exact schema:
            {"contexts":[{"index":<0-based index>,"useful":true|false,"reason":"<short reason>"}]}
            """;

    public static final String REVERSE_SYSTEM = """
            You generate plausible questions a user might have asked to receive the
            given ANSWER. Keep the questions in the same language as the answer and
            match the style of a natural user query.
            Respond with ONLY a JSON object, no prose, in this exact schema:
            {"questions":["<question 1>","<question 2>","<question 3>"]}
            """;

    private JudgePrompts() {
    }

    public static String numberedContexts(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            sb.append("[").append(i).append("] ").append(items.get(i)).append("\n");
        }
        return sb.toString();
    }

    public static String faithfulnessUser(String answer, List<String> contexts) {
        return "ANSWER:\n" + answer + "\n\nCONTEXTS:\n" + numberedContexts(contexts);
    }

    public static String recallUser(String expectedAnswer, List<String> contexts) {
        return "EXPECTED ANSWER:\n" + expectedAnswer + "\n\nCONTEXTS:\n" + numberedContexts(contexts);
    }

    public static String precisionUser(String question, List<String> contexts) {
        return "QUESTION:\n" + question + "\n\nCONTEXTS:\n" + numberedContexts(contexts);
    }

    public static String reverseUser(String answer, int n) {
        return "ANSWER:\n" + answer + "\n\nGenerate " + n + " questions.";
    }
}
