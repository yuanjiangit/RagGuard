package com.aizerohub.ragguard.spring;

import com.aizerohub.ragguard.core.generate.GeneratedQuestion;
import com.aizerohub.ragguard.core.generate.QuestionGenerator;
import com.aizerohub.ragguard.core.judge.support.JudgeParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * {@link QuestionGenerator} backed by a Spring AI {@link ChatModel}: emits
 * QA pairs grounded in the source chunk, with structured JSON output.
 */
public final class SpringAiQuestionGenerator implements QuestionGenerator {

    private static final int MAX_ATTEMPTS = 2;

    private static final String SYSTEM = """
            You create evaluation test cases for a retrieval-augmented generation
            system from source material.
            TASK: write up to N question/answer pairs that can be answered from the
            SOURCE material only. Questions must be self-contained (no "in this
            document"), specific, and diverse. Answers must be short, factual, and
            grounded verbatim-or-paraphrase in the source. Write in the same
            language as the source.
            Respond with ONLY a JSON object, no prose, in this exact schema:
            {"questions":[{"question":"<question>","answer":"<expected answer>"}]}
            """;

    private static final String ONLY_JSON_SUFFIX = "\n\nIMPORTANT: respond with ONLY the JSON object.";

    private final ChatModel chatModel;

    public SpringAiQuestionGenerator(ChatModel chatModel) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
    }

    @Override
    public List<GeneratedQuestion> generateFor(String chunk, int n) {
        JudgeParseException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String user = "SOURCE:\n" + chunk + "\n\nGenerate " + n + " question/answer pairs."
                    + (attempt == 1 ? "" : ONLY_JSON_SUFFIX);
            try {
                return parse(call(user), n);
            } catch (JudgeParseException e) {
                lastFailure = e;
            }
        }
        throw lastFailure;
    }

    private String call(String user) {
        ChatOptions options = ChatOptions.builder().temperature(0.0).build();
        ChatResponse response = chatModel.call(new Prompt(
                List.of(new SystemMessage(SYSTEM), new UserMessage(user)), options));
        if (response == null || response.getResults().isEmpty()
                || response.getResults().get(0).getOutput() == null) {
            throw new JudgeParseException("generator model returned an empty response");
        }
        String text = response.getResults().get(0).getOutput().getText();
        if (text == null || text.isBlank()) {
            throw new JudgeParseException("generator model returned blank content");
        }
        return text;
    }

    private static List<GeneratedQuestion> parse(String raw, int n) {
        JsonNode root;
        try {
            root = new ObjectMapper().readTree(JudgeJsonParser.extract(raw));
        } catch (Exception e) {
            throw new JudgeParseException("cannot parse generator output: " + raw, e);
        }
        JsonNode questions = root.get("questions");
        if (questions == null || !questions.isArray()) {
            throw new JudgeParseException("generator JSON is missing the 'questions' array");
        }
        List<GeneratedQuestion> result = new ArrayList<>();
        for (JsonNode q : questions) {
            String question = q.path("question").asText(null);
            String answer = q.path("answer").asText(null);
            if (question == null || question.isBlank() || answer == null || answer.isBlank()) {
                throw new JudgeParseException("generator JSON entry missing 'question' or 'answer'");
            }
            result.add(new GeneratedQuestion(question, answer));
            if (result.size() == n) {
                break;
            }
        }
        if (result.isEmpty()) {
            throw new JudgeParseException("generator returned no questions");
        }
        return List.copyOf(result);
    }
}
