package com.aizerohub.ragguard.core.judge.support;

import com.aizerohub.ragguard.core.model.Claim;
import com.aizerohub.ragguard.core.model.ClaimAttribution;
import com.aizerohub.ragguard.core.model.ClaimVerdict;
import com.aizerohub.ragguard.core.model.ContextUsefulness;
import com.aizerohub.ragguard.core.model.SupportVerdict;
import org.yaml.snakeyaml.Yaml;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses raw judge model output into domain objects. Tolerant of markdown
 * code fences and surrounding prose; strict about schema (missing fields,
 * invalid verdicts, wrong verdict/context counts raise
 * {@link JudgeParseException} so adapters can retry and finally surface
 * JUDGE_FAILED instead of silently scoring).
 *
 * <p>JSON is parsed with SnakeYAML (JSON is a YAML subset), keeping
 * ragguard-core free of a dedicated JSON library.
 */
public final class JudgeResponseParser {

    private JudgeResponseParser() {
    }

    public static List<ClaimVerdict> parseFaithfulness(String raw) {
        Map<?, ?> root = parseObject(raw);
        List<Map<?, ?>> claims = requireList(root, "claims");
        List<ClaimVerdict> result = new ArrayList<>();
        for (Map<?, ?> entry : claims) {
            String text = requireText(entry, "text");
            SupportVerdict verdict = parseVerdict(requireText(entry, "verdict"));
            result.add(new ClaimVerdict(new Claim(text), verdict, optionalText(entry, "reason")));
        }
        if (result.isEmpty()) {
            throw new JudgeParseException("judge returned an empty claims list");
        }
        return List.copyOf(result);
    }

    public static List<ClaimAttribution> parseContextRecall(String raw) {
        Map<?, ?> root = parseObject(raw);
        List<Map<?, ?>> claims = requireList(root, "claims");
        List<ClaimAttribution> result = new ArrayList<>();
        for (Map<?, ?> entry : claims) {
            String text = requireText(entry, "text");
            boolean attributable = Boolean.TRUE.equals(entry.get("attributable"));
            int index = entry.get("contextIndex") instanceof Number n ? n.intValue() : -1;
            result.add(new ClaimAttribution(new Claim(text), attributable, index,
                    optionalText(entry, "reason")));
        }
        if (result.isEmpty()) {
            throw new JudgeParseException("judge returned an empty claims list");
        }
        return List.copyOf(result);
    }

    public static List<ContextUsefulness> parseContextPrecision(String raw, int contextCount) {
        Map<?, ?> root = parseObject(raw);
        List<Map<?, ?>> contexts = requireList(root, "contexts");
        if (contexts.size() != contextCount) {
            throw new JudgeParseException("judge returned " + contexts.size()
                    + " usefulness verdicts for " + contextCount + " contexts");
        }
        ContextUsefulness[] byIndex = new ContextUsefulness[contextCount];
        for (Map<?, ?> entry : contexts) {
            int index = entry.get("index") instanceof Number n ? n.intValue() : -1;
            if (index < 0 || index >= contextCount || byIndex[index] != null) {
                throw new JudgeParseException("judge returned invalid or duplicate context index: " + index);
            }
            boolean useful = Boolean.TRUE.equals(entry.get("useful"));
            byIndex[index] = new ContextUsefulness(index, useful, optionalText(entry, "reason"));
        }
        return List.of(byIndex);
    }

    public static List<String> parseReverseQuestions(String raw, int n) {
        Map<?, ?> root = parseObject(raw);
        List<?> questions = requireRawList(root, "questions");
        List<String> result = new ArrayList<>();
        for (Object q : questions) {
            if (q instanceof String s && !s.isBlank()) {
                result.add(s);
            }
            if (result.size() == n) {
                break;
            }
        }
        if (result.isEmpty()) {
            throw new JudgeParseException("judge returned no reverse questions");
        }
        return List.copyOf(result);
    }

    // ----- tolerant JSON extraction -----

    @SuppressWarnings("unchecked")
    private static Map<?, ?> parseObject(String raw) {
        String json = extract(raw);
        try {
            Object loaded = new Yaml().load(json);
            if (!(loaded instanceof Map)) {
                throw new JudgeParseException("judge did not return a JSON object: " + preview(raw));
            }
            return (Map<?, ?>) loaded;
        } catch (JudgeParseException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new JudgeParseException("cannot parse judge output as JSON: " + preview(raw), e);
        }
    }

    static String extract(String raw) {
        if (raw == null) {
            throw new JudgeParseException("judge returned no content");
        }
        String s = raw.strip();
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
            throw new JudgeParseException("no JSON structure found in judge output: " + preview(raw));
        } else if (arrStart < 0 || (objStart >= 0 && objStart < arrStart)) {
            start = objStart;
        } else {
            start = arrStart;
        }
        char open = s.charAt(start);
        char close = open == '{' ? '}' : ']';
        int end = s.lastIndexOf(close);
        if (end <= start) {
            throw new JudgeParseException("unterminated JSON structure in judge output: " + preview(raw));
        }
        return s.substring(start, end + 1);
    }

    // ----- schema helpers -----

    private static List<Map<?, ?>> requireList(Map<?, ?> root, String field) {
        Object value = root.get(field);
        if (!(value instanceof List<?> list)) {
            throw new JudgeParseException("judge JSON is missing the '" + field + "' array");
        }
        List<Map<?, ?>> entries = new ArrayList<>(list.size());
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                throw new JudgeParseException("judge JSON '" + field + "' entries must be objects");
            }
            entries.add(map);
        }
        return entries;
    }

    private static List<?> requireRawList(Map<?, ?> root, String field) {
        Object value = root.get(field);
        if (!(value instanceof List<?> list)) {
            throw new JudgeParseException("judge JSON is missing the '" + field + "' array");
        }
        return list;
    }

    private static String requireText(Map<?, ?> entry, String field) {
        Object value = entry.get(field);
        if (!(value instanceof String s) || s.isBlank()) {
            throw new JudgeParseException("judge JSON entry is missing '" + field + "'");
        }
        return s;
    }

    private static String optionalText(Map<?, ?> entry, String field) {
        Object value = entry.get(field);
        return value instanceof String s ? s : "";
    }

    private static SupportVerdict parseVerdict(String raw) {
        try {
            return SupportVerdict.valueOf(raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new JudgeParseException("invalid verdict '" + raw + "' — expected SUPPORTED, REFUTED or NOT_ENOUGH_INFO");
        }
    }

    private static String preview(String raw) {
        String s = raw == null ? "null" : raw.strip();
        return s.length() <= 120 ? s : s.substring(0, 117) + "...";
    }
}
