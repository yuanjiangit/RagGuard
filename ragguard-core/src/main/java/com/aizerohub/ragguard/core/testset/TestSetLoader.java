package com.aizerohub.ragguard.core.testset;

import com.aizerohub.ragguard.core.model.RagTestCase;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads a test set from YAML (JSON works too — YAML is a superset of JSON).
 *
 * <pre>
 * testCases:
 *   - id: q1                        # optional, defaults to case-N
 *     question: What is RagGuard?
 *     expectedAnswer: A RAG evaluation framework.
 *     expectedContexts:             # optional
 *       - RagGuard evaluates RAG applications.
 * </pre>
 */
public final class TestSetLoader {

    private TestSetLoader() {
    }

    public static List<RagTestCase> loadYaml(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return loadYaml(reader);
        } catch (IOException e) {
            throw new TestSetFormatException("cannot read test set file: " + path, e);
        }
    }

    public static List<RagTestCase> loadYaml(String yamlContent) {
        return loadYaml(new StringReader(yamlContent));
    }

    @SuppressWarnings("unchecked")
    public static List<RagTestCase> loadYaml(Reader reader) {
        Object root;
        try {
            root = new Yaml().load(reader);
        } catch (RuntimeException e) {
            throw new TestSetFormatException("invalid YAML: " + e.getMessage(), e);
        }
        if (!(root instanceof Map<?, ?> rootMap)) {
            throw new TestSetFormatException(
                    "test set root must be a mapping with a 'testCases' list, got: "
                            + (root == null ? "empty document" : root.getClass().getSimpleName()));
        }
        Object casesObj = rootMap.get("testCases");
        if (!(casesObj instanceof List<?> caseList) || caseList.isEmpty()) {
            throw new TestSetFormatException("'testCases' must be a non-empty list");
        }
        List<RagTestCase> result = new ArrayList<>(caseList.size());
        for (int i = 0; i < caseList.size(); i++) {
            Object entry = caseList.get(i);
            if (!(entry instanceof Map<?, ?> entryMap)) {
                throw new TestSetFormatException("testCases[" + i + "] must be a mapping");
            }
            result.add(toTestCase((Map<String, Object>) entryMap, i));
        }
        return List.copyOf(result);
    }

    private static RagTestCase toTestCase(Map<String, Object> map, int index) {
        String id = string(map.get("id"));
        String question = string(map.get("question"));
        if (question == null || question.isBlank()) {
            throw new TestSetFormatException("testCases[" + index + "] is missing 'question'");
        }
        String expectedAnswer = string(map.get("expectedAnswer"));
        List<String> expectedContexts = stringList(map.get("expectedContexts"));
        return new RagTestCase(id, question, expectedAnswer, expectedContexts);
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static List<String> stringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new TestSetFormatException("'expectedContexts' must be a list of strings");
        }
        List<String> result = new ArrayList<>(list.size());
        for (Object item : list) {
            result.add(item == null ? "" : String.valueOf(item));
        }
        return result;
    }
}
