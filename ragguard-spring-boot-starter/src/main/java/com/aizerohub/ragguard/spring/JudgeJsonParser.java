package com.aizerohub.ragguard.spring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tolerant JSON extraction for LLM output: strips markdown code fences and
 * leading/trailing prose, then parses the first JSON object/array.
 */
final class JudgeJsonParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JudgeJsonParser() {
    }

    static JsonNode parseObject(String raw) {
        String json = extract(raw);
        try {
            JsonNode node = MAPPER.readTree(json);
            if (node == null || !node.isObject()) {
                throw new JudgeCallException("judge did not return a JSON object: " + preview(raw));
            }
            return node;
        } catch (JudgeCallException e) {
            throw e;
        } catch (Exception e) {
            throw new JudgeCallException("cannot parse judge output as JSON: " + preview(raw), e);
        }
    }

    /**
     * Strips markdown fences and prose around the outermost JSON structure.
     */
    static String extract(String raw) {
        if (raw == null) {
            throw new JudgeCallException("judge returned no content");
        }
        String s = raw.strip();
        // strip ```json ... ``` fences
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            int lastFence = s.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                s = s.substring(firstNewline + 1, lastFence).strip();
            }
        }
        int objStart = s.indexOf('{');
        int arrStart = s.indexOf('[');
        int start;
        if (objStart < 0 && arrStart < 0) {
            throw new JudgeCallException("no JSON structure found in judge output: " + preview(raw));
        } else if (arrStart < 0 || (objStart >= 0 && objStart < arrStart)) {
            start = objStart;
        } else {
            start = arrStart;
        }
        char open = s.charAt(start);
        char close = open == '{' ? '}' : ']';
        int end = s.lastIndexOf(close);
        if (end <= start) {
            throw new JudgeCallException("unterminated JSON structure in judge output: " + preview(raw));
        }
        return s.substring(start, end + 1);
    }

    private static String preview(String raw) {
        String s = raw == null ? "null" : raw.strip();
        return s.length() <= 120 ? s : s.substring(0, 117) + "...";
    }
}
