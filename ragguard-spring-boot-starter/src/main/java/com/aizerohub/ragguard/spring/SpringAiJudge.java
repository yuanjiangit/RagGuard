package com.aizerohub.ragguard.spring;

import com.aizerohub.ragguard.core.judge.Judge;
import com.aizerohub.ragguard.core.model.Claim;
import com.aizerohub.ragguard.core.model.ClaimAttribution;
import com.aizerohub.ragguard.core.model.ClaimVerdict;
import com.aizerohub.ragguard.core.model.ContextUsefulness;
import com.aizerohub.ragguard.core.model.SupportVerdict;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * LLM-as-Judge backed by a Spring AI {@link ChatModel}.
 *
 * <p>Judge calls run with temperature 0 (generic runtime options merged
 * into whatever the configured model defaults to) and expect structured
 * JSON output. On parse or schema failure the call is retried once with an
 * explicit "only JSON" instruction; a second failure raises
 * {@link JudgeCallException}, which metrics surface as JUDGE_FAILED.
 *
 * <p>Prompts are versioned ({@link #PROMPT_VERSION}); the version is meant
 * to participate in the judge-result cache key once caching lands.
 */
public final class SpringAiJudge implements Judge {

    /** Version of the built-in judge prompts; bump on any prompt change. */
    public static final String PROMPT_VERSION = "v1";

    private static final int MAX_ATTEMPTS = 2;

    private static final String FAITHFULNESS_SYSTEM = """
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

    private static final String RECALL_SYSTEM = """
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

    private static final String PRECISION_SYSTEM = """
            You are a strict evaluator for retrieval-augmented generation systems.
            TASK: judge whether each retrieved context is USEFUL for answering the
            QUESTION. Judge every context; do not skip any.
            Respond with ONLY a JSON object, no prose, in this exact schema:
            {"contexts":[{"index":<0-based index>,"useful":true|false,"reason":"<short reason>"}]}
            """;

    private static final String REVERSE_SYSTEM = """
            You generate plausible questions a user might have asked to receive the
            given ANSWER. Keep the questions in the same language as the answer and
            match the style of a natural user query.
            Respond with ONLY a JSON object, no prose, in this exact schema:
            {"questions":["<question 1>","<question 2>","<question 3>"]}
            """;

    private static final String ONLY_JSON_SUFFIX = """

            IMPORTANT: respond with ONLY the JSON object. No markdown, no prose.""";

    private final ChatModel chatModel;
    private final int numReverseQuestions;

    public SpringAiJudge(ChatModel chatModel) {
        this(chatModel, 3);
    }

    public SpringAiJudge(ChatModel chatModel, int numReverseQuestions) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        if (numReverseQuestions < 1) {
            throw new IllegalArgumentException("numReverseQuestions must be >= 1");
        }
        this.numReverseQuestions = numReverseQuestions;
    }

    @Override
    public List<ClaimVerdict> verifyFaithfulness(String answer, List<String> contexts) {
        String user = new StringBuilder()
                .append("ANSWER:\n").append(answer).append("\n\nCONTEXTS:\n")
                .append(numberedList(contexts))
                .toString();
        JsonNode root = callWithRetry(FAITHFULNESS_SYSTEM, user);
        JsonNode claims = requireArray(root, "claims");
        List<ClaimVerdict> result = new ArrayList<>();
        for (JsonNode claimNode : claims) {
            String text = requireText(claimNode, "text");
            SupportVerdict verdict = parseVerdict(requireText(claimNode, "verdict"));
            result.add(new ClaimVerdict(new Claim(text), verdict, textOrEmpty(claimNode, "reason")));
        }
        if (result.isEmpty()) {
            throw new JudgeCallException("judge returned an empty claims list");
        }
        return List.copyOf(result);
    }

    @Override
    public List<ClaimAttribution> attributeContextRecall(String expectedAnswer, List<String> contexts) {
        String user = new StringBuilder()
                .append("EXPECTED ANSWER:\n").append(expectedAnswer).append("\n\nCONTEXTS:\n")
                .append(numberedList(contexts))
                .toString();
        JsonNode root = callWithRetry(RECALL_SYSTEM, user);
        JsonNode claims = requireArray(root, "claims");
        List<ClaimAttribution> result = new ArrayList<>();
        for (JsonNode claimNode : claims) {
            String text = requireText(claimNode, "text");
            boolean attributable = claimNode.path("attributable").asBoolean(false);
            int index = claimNode.path("contextIndex").asInt(-1);
            result.add(new ClaimAttribution(new Claim(text), attributable, index,
                    textOrEmpty(claimNode, "reason")));
        }
        if (result.isEmpty()) {
            throw new JudgeCallException("judge returned an empty claims list");
        }
        return List.copyOf(result);
    }

    @Override
    public List<ContextUsefulness> judgeContextPrecision(String question, List<String> contexts) {
        String user = new StringBuilder()
                .append("QUESTION:\n").append(question).append("\n\nCONTEXTS:\n")
                .append(numberedList(contexts))
                .toString();
        JsonNode root = callWithRetry(PRECISION_SYSTEM, user);
        JsonNode contextsNode = requireArray(root, "contexts");
        if (contextsNode.size() != contexts.size()) {
            throw new JudgeCallException("judge returned " + contextsNode.size()
                    + " usefulness verdicts for " + contexts.size() + " contexts");
        }
        ContextUsefulness[] byIndex = new ContextUsefulness[contexts.size()];
        for (JsonNode node : contextsNode) {
            int index = node.path("index").asInt(-1);
            if (index < 0 || index >= contexts.size() || byIndex[index] != null) {
                throw new JudgeCallException("judge returned invalid or duplicate context index: " + index);
            }
            byIndex[index] = new ContextUsefulness(index,
                    node.path("useful").asBoolean(false), textOrEmpty(node, "reason"));
        }
        return List.of(byIndex);
    }

    @Override
    public List<String> generateReverseQuestions(String answer, int n) {
        String user = "ANSWER:\n" + answer
                + "\n\nGenerate " + Math.min(n, numReverseQuestions) + " questions.";
        JsonNode root = callWithRetry(REVERSE_SYSTEM, user);
        JsonNode questions = requireArray(root, "questions");
        List<String> result = new ArrayList<>();
        for (JsonNode q : questions) {
            if (q.isTextual() && !q.asText().isBlank()) {
                result.add(q.asText());
            }
            if (result.size() == n) {
                break;
            }
        }
        if (result.isEmpty()) {
            throw new JudgeCallException("judge returned no reverse questions");
        }
        return List.copyOf(result);
    }

    // ----- judge call plumbing -----

    private JsonNode callWithRetry(String system, String user) {
        JudgeCallException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String suffix = attempt == 1 ? "" : ONLY_JSON_SUFFIX;
            try {
                String content = call(system, user + suffix);
                return JudgeJsonParser.parseObject(content);
            } catch (JudgeCallException e) {
                lastFailure = e;
            }
        }
        throw lastFailure;
    }

    private String call(String system, String user) {
        List<Message> messages = List.of(new SystemMessage(system), new UserMessage(user));
        ChatOptions options = ChatOptions.builder().temperature(0.0).build();
        ChatResponse response = chatModel.call(new Prompt(messages, options));
        if (response == null || response.getResults().isEmpty()) {
            throw new JudgeCallException("judge model returned an empty response");
        }
        AssistantMessage output = response.getResults().get(0).getOutput();
        String text = output == null ? null : output.getText();
        if (text == null || text.isBlank()) {
            throw new JudgeCallException("judge model returned blank content");
        }
        return text;
    }

    // ----- schema helpers -----

    private static JsonNode requireArray(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isArray()) {
            throw new JudgeCallException("judge JSON is missing the '" + field + "' array");
        }
        return node;
    }

    private static String requireText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new JudgeCallException("judge JSON entry is missing '" + field + "'");
        }
        return value.asText();
    }

    private static String textOrEmpty(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isTextual() ? "" : value.asText();
    }

    private static SupportVerdict parseVerdict(String raw) {
        try {
            return SupportVerdict.valueOf(raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new JudgeCallException("invalid verdict '" + raw
                    + "' — expected one of " + Set.of(SupportVerdict.values()));
        }
    }

    private static String numberedList(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            sb.append("[").append(i).append("] ").append(items.get(i)).append("\n");
        }
        return sb.toString();
    }

    public String promptVersion() {
        return PROMPT_VERSION;
    }
}
