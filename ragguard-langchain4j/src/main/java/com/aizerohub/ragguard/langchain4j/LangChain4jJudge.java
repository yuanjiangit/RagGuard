package com.aizerohub.ragguard.langchain4j;

import com.aizerohub.ragguard.core.judge.Judge;
import com.aizerohub.ragguard.core.judge.JudgeCache;
import com.aizerohub.ragguard.core.judge.support.JudgeParseException;
import com.aizerohub.ragguard.core.judge.support.JudgePrompts;
import com.aizerohub.ragguard.core.judge.support.JudgeResponseParser;
import com.aizerohub.ragguard.core.model.ClaimAttribution;
import com.aizerohub.ragguard.core.model.ClaimVerdict;
import com.aizerohub.ragguard.core.model.ContextUsefulness;
import dev.langchain4j.model.chat.ChatModel;

import java.util.List;
import java.util.Objects;

/**
 * LLM-as-Judge backed by a langchain4j {@link ChatModel}.
 *
 * <p>Prompts and parsing are shared with all other adapters via
 * {@code ragguard-core} ({@link JudgePrompts} / {@link JudgeResponseParser});
 * this class only provides model plumbing and one retry after a parse
 * failure. Configure temperature 0 on the underlying model
 * (e.g. {@code OpenAiChatModel.builder().temperature(0.0)}).
 */
public final class LangChain4jJudge implements Judge {

    private static final int MAX_ATTEMPTS = 2;

    private final ChatModel chatModel;
    private final int numReverseQuestions;
    private final com.aizerohub.ragguard.core.judge.JudgeCache cache; // nullable — disabled when null
    private final String modelId;

    public LangChain4jJudge(ChatModel chatModel) {
        this(chatModel, 3);
    }

    public LangChain4jJudge(ChatModel chatModel, int numReverseQuestions) {
        this(chatModel, numReverseQuestions, null, "default");
    }

    /**
     * @param cache optional judge cache; when present, raw model responses are
     *              reused across runs keyed by (model, prompt version, input)
     */
    public LangChain4jJudge(ChatModel chatModel, int numReverseQuestions,
                            com.aizerohub.ragguard.core.judge.JudgeCache cache, String modelId) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        if (numReverseQuestions < 1) {
            throw new IllegalArgumentException("numReverseQuestions must be >= 1");
        }
        this.numReverseQuestions = numReverseQuestions;
        this.cache = cache;
        this.modelId = modelId == null || modelId.isBlank() ? "default" : modelId;
    }

    @Override
    public List<ClaimVerdict> verifyFaithfulness(String answer, List<String> contexts) {
        return callParseAndCache(JudgePrompts.FAITHFULNESS_SYSTEM,
                JudgePrompts.faithfulnessUser(answer, contexts), "",
                JudgeResponseParser::parseFaithfulness);
    }

    @Override
    public List<ClaimAttribution> attributeContextRecall(String expectedAnswer, List<String> contexts) {
        return callParseAndCache(JudgePrompts.RECALL_SYSTEM,
                JudgePrompts.recallUser(expectedAnswer, contexts), "",
                JudgeResponseParser::parseContextRecall);
    }

    @Override
    public List<ContextUsefulness> judgeContextPrecision(String question, List<String> contexts) {
        return callParseAndCache(JudgePrompts.PRECISION_SYSTEM,
                JudgePrompts.precisionUser(question, contexts), "",
                raw -> JudgeResponseParser.parseContextPrecision(raw, contexts.size()));
    }

    @Override
    public List<String> generateReverseQuestions(String answer, int n) {
        int count = Math.min(n, numReverseQuestions);
        return callParseAndCache(JudgePrompts.REVERSE_SYSTEM,
                JudgePrompts.reverseUser(answer, count), String.valueOf(n),
                raw -> JudgeResponseParser.parseReverseQuestions(raw, n));
    }

    // ----- model plumbing -----

    /**
     * Cache-hit → parse cached raw text; miss → up to two model calls,
     * caching only raw text whose parse succeeded so a malformed response
     * is never frozen into the cache.
     */
    private <T> T callParseAndCache(String system, String user, String variant,
                                    java.util.function.Function<String, T> parser) {
        String cacheKey = cache == null
                ? null
                : JudgeCache.keyFor(modelId, JudgePrompts.PROMPT_VERSION, system, user + ' ' + variant);
        if (cacheKey != null) {
            java.util.Optional<String> cached = cache.get(cacheKey);
            if (cached.isPresent()) {
                return parser.apply(cached.get());
            }
        }
        JudgeParseException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String userAttempt = attempt == 1
                    ? user
                    : user + "\n\nIMPORTANT: respond with ONLY the JSON object. No markdown, no prose.";
            String raw = chat(system, userAttempt);
            try {
                T parsed = parser.apply(raw);
                if (cacheKey != null) {
                    cache.put(cacheKey, raw);
                }
                return parsed;
            } catch (JudgeParseException e) {
                lastFailure = e;
            }
        }
        throw lastFailure;
    }

    private String chat(String system, String user) {
        dev.langchain4j.model.chat.response.ChatResponse response = chatModel.chat(
                dev.langchain4j.data.message.SystemMessage.from(system),
                dev.langchain4j.data.message.UserMessage.from(user));
        String text = response == null || response.aiMessage() == null
                ? null
                : response.aiMessage().text();
        if (text == null || text.isBlank()) {
            throw new JudgeParseException("judge model returned blank content");
        }
        return text;
    }

    public String promptVersion() {
        return JudgePrompts.PROMPT_VERSION;
    }
}
