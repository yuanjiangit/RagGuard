package com.aizerohub.ragguard.langchain4j;

import com.aizerohub.ragguard.core.model.ClaimAttribution;
import com.aizerohub.ragguard.core.model.ClaimVerdict;
import com.aizerohub.ragguard.core.model.ContextUsefulness;
import dev.langchain4j.model.chat.ChatModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LangChain4jJudgeTest {

    private static final String FAITHFULNESS_JSON =
            "{\"claims\":[{\"text\":\"a\",\"verdict\":\"SUPPORTED\",\"reason\":\"ok\"}]}";
    private static final String RECALL_JSON =
            "{\"claims\":[{\"text\":\"r\",\"attributable\":true,\"contextIndex\":0,\"reason\":\"ok\"}]}";
    private static final String PRECISION_JSON =
            "{\"contexts\":[{\"index\":0,\"useful\":true,\"reason\":\"ok\"}]}";
    private static final String REVERSE_JSON =
            "{\"questions\":[\"What is RagGuard?\"]}";

    private LangChain4jJudge judge(Map<String, String> routes) {
        return new LangChain4jJudge(new FakeChatModel(routes));
    }

    @Test
    void endToEndWithFencedJson() {
        // markdown fence + prose must be tolerated
        LangChain4jJudge j = judge(Map.of(
                "decompose the ANSWER", "Here you go:\n```json\n" + FAITHFULNESS_JSON + "\n```\nHope that helps!",
                "decompose the EXPECTED ANSWER", RECALL_JSON,
                "judge whether each retrieved context", PRECISION_JSON,
                "plausible questions", REVERSE_JSON));
        List<ClaimVerdict> verdicts = j.verifyFaithfulness("answer", List.of("ctx"));
        assertEquals(1, verdicts.size());
        List<ClaimAttribution> attributions = j.attributeContextRecall("expected", List.of("ctx"));
        assertEquals(1, attributions.size());
        List<ContextUsefulness> usefulness = j.judgeContextPrecision("q", List.of("ctx"));
        assertEquals(1, usefulness.size());
        assertEquals(List.of("What is RagGuard?"), j.generateReverseQuestions("answer", 3));
    }

    @Test
    void parseFailureRetriesOnceThenThrows() {
        FakeChatModel bad = new FakeChatModel(Map.of("decompose the ANSWER", "not json at all"));
        LangChain4jJudge j = new LangChain4jJudge(bad);
        assertThrows(RuntimeException.class, () -> j.verifyFaithfulness("answer", List.of("ctx")));
        assertEquals(2, bad.calls); // initial attempt + one retry
    }

    @Test
    void blankModelResponse_throwsImmediately() {
        ChatModel blank = new ChatModel() {
            @Override
            public dev.langchain4j.model.chat.response.ChatResponse chat(
                    dev.langchain4j.data.message.ChatMessage... messages) {
                return dev.langchain4j.model.chat.response.ChatResponse.builder()
                        .aiMessage(dev.langchain4j.data.message.AiMessage.from("   "))
                        .build();
            }
        };
        LangChain4jJudge j = new LangChain4jJudge(blank);
        assertThrows(RuntimeException.class, () -> j.verifyFaithfulness("answer", List.of("ctx")));
    }
}
