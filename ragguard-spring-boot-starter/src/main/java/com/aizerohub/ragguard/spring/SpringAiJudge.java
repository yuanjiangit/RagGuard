package com.aizerohub.ragguard.spring;

import com.aizerohub.ragguard.core.judge.Judge;
import com.aizerohub.ragguard.core.judge.support.JudgeParseException;
import com.aizerohub.ragguard.core.judge.support.JudgePrompts;
import com.aizerohub.ragguard.core.judge.support.JudgeResponseParser;
import com.aizerohub.ragguard.core.model.ClaimAttribution;
import com.aizerohub.ragguard.core.model.ClaimVerdict;
import com.aizerohub.ragguard.core.model.ContextUsefulness;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Objects;

/**
 * LLM-as-Judge backed by a Spring AI {@link ChatModel}.
 *
 * <p>All prompts and output parsing live in {@code ragguard-core}
 * ({@link JudgePrompts} / {@link JudgeResponseParser}) so the Spring AI and
 * langchain4j adapters behave identically; this class only provides model
 * plumbing: temperature 0 runtime options and a retry with an explicit
 * "only JSON" instruction after a parse failure.
 */
public final class SpringAiJudge implements Judge {

    private static final int MAX_ATTEMPTS = 2;

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
        return parseWithRetry(() -> call(JudgePrompts.FAITHFULNESS_SYSTEM,
                JudgePrompts.faithfulnessUser(answer, contexts)),
                JudgeResponseParser::parseFaithfulness);
    }

    @Override
    public List<ClaimAttribution> attributeContextRecall(String expectedAnswer, List<String> contexts) {
        return parseWithRetry(() -> call(JudgePrompts.RECALL_SYSTEM,
                JudgePrompts.recallUser(expectedAnswer, contexts)),
                JudgeResponseParser::parseContextRecall);
    }

    @Override
    public List<ContextUsefulness> judgeContextPrecision(String question, List<String> contexts) {
        return parseWithRetry(() -> call(JudgePrompts.PRECISION_SYSTEM,
                JudgePrompts.precisionUser(question, contexts)),
                raw -> JudgeResponseParser.parseContextPrecision(raw, contexts.size()));
    }

    @Override
    public List<String> generateReverseQuestions(String answer, int n) {
        int count = Math.min(n, numReverseQuestions);
        return parseWithRetry(() -> call(JudgePrompts.REVERSE_SYSTEM,
                        JudgePrompts.reverseUser(answer, count)),
                raw -> JudgeResponseParser.parseReverseQuestions(raw, n));
    }

    // ----- model plumbing -----

    private interface JudgeCall {
        String execute();
    }

    private <T> T parseWithRetry(JudgeCall judgeCall, java.util.function.Function<String, T> parser) {
        JudgeParseException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String raw = attempt == 1
                        ? judgeCall.execute()
                        : judgeCall.execute() + retriedSuffix();
                return parser.apply(raw);
            } catch (JudgeParseException e) {
                lastFailure = e;
            }
        }
        throw lastFailure;
    }

    private static String retriedSuffix() {
        return "\n\nIMPORTANT: respond with ONLY the JSON object. No markdown, no prose.";
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

    public String promptVersion() {
        return JudgePrompts.PROMPT_VERSION;
    }
}
