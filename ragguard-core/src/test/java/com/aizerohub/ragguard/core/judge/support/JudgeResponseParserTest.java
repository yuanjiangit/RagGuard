package com.aizerohub.ragguard.core.judge.support;

import com.aizerohub.ragguard.core.model.ClaimAttribution;
import com.aizerohub.ragguard.core.model.ClaimVerdict;
import com.aizerohub.ragguard.core.model.ContextUsefulness;
import com.aizerohub.ragguard.core.model.SupportVerdict;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JudgeResponseParserTest {

    @Test
    void faithfulness_plainObject() {
        String raw = "{\"claims\":[{\"text\":\"a\",\"verdict\":\"SUPPORTED\",\"reason\":\"ok\"},"
                + "{\"text\":\"b\",\"verdict\":\"refuted\"}]}";
        List<ClaimVerdict> verdicts = JudgeResponseParser.parseFaithfulness(raw);
        assertEquals(2, verdicts.size());
        assertEquals(SupportVerdict.SUPPORTED, verdicts.get(0).verdict());
        assertEquals("ok", verdicts.get(0).reason());
        assertEquals(SupportVerdict.REFUTED, verdicts.get(1).verdict());
    }

    @Test
    void faithfulness_markdownFencesAndProseTolerated() {
        String raw = "Sure!\n```json\n{\"claims\":[{\"text\":\"a\",\"verdict\":\"NOT_ENOUGH_INFO\"}]}\n```\nDone.";
        List<ClaimVerdict> verdicts = JudgeResponseParser.parseFaithfulness(raw);
        assertEquals(1, verdicts.size());
        assertEquals(SupportVerdict.NOT_ENOUGH_INFO, verdicts.get(0).verdict());
    }

    @Test
    void faithfulness_invalidVerdict_throws() {
        assertThrows(JudgeParseException.class, () -> JudgeResponseParser.parseFaithfulness(
                "{\"claims\":[{\"text\":\"a\",\"verdict\":\"MAYBE\"}]}"));
    }

    @Test
    void faithfulness_emptyClaims_throws() {
        assertThrows(JudgeParseException.class,
                () -> JudgeResponseParser.parseFaithfulness("{\"claims\":[]}"));
    }

    @Test
    void contextRecall_missingFieldsDefault() {
        List<ClaimAttribution> result = JudgeResponseParser.parseContextRecall(
                "{\"claims\":[{\"text\":\"r\",\"attributable\":true,\"contextIndex\":2}]}");
        assertEquals(1, result.size());
        assertEquals(true, result.get(0).attributable());
        assertEquals(2, result.get(0).attributedContextIndex());
    }

    @Test
    void contextPrecision_indexValidation() {
        List<ContextUsefulness> ok = JudgeResponseParser.parseContextPrecision(
                "{\"contexts\":[{\"index\":1,\"useful\":false},{\"index\":0,\"useful\":true}]}", 2);
        assertEquals(0, ok.get(0).contextIndex());
        assertThrows(JudgeParseException.class, () -> JudgeResponseParser.parseContextPrecision(
                "{\"contexts\":[{\"index\":0,\"useful\":true},{\"index\":0,\"useful\":true}]}", 2));
        assertThrows(JudgeParseException.class, () -> JudgeResponseParser.parseContextPrecision(
                "{\"contexts\":[{\"index\":0,\"useful\":true}]}", 2));
    }

    @Test
    void reverseQuestions_truncatedToN() {
        List<String> questions = JudgeResponseParser.parseReverseQuestions(
                "{\"questions\":[\"q1\",\"q2\",\"q3\",\"q4\"]}", 3);
        assertEquals(List.of("q1", "q2", "q3"), questions);
    }

    @Test
    void garbage_throws() {
        assertThrows(JudgeParseException.class, () -> JudgeResponseParser.parseFaithfulness("no json here"));
        assertThrows(JudgeParseException.class, () -> JudgeResponseParser.parseFaithfulness(null));
        assertThrows(JudgeParseException.class, () -> JudgeResponseParser.parseFaithfulness("{\"wrong\":1}"));
    }
}
