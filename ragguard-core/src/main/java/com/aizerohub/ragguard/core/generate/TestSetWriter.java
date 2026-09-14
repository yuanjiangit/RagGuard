package com.aizerohub.ragguard.core.generate;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders generated candidates as a YAML test set for human confirmation:
 * review, delete wrong pairs, fix phrasing — then point RagGuard at the
 * file. Ids are assigned as {@code gen-1, gen-2, ...}.
 */
public final class TestSetWriter {

    private TestSetWriter() {
    }

    public static String toYaml(List<GeneratedTestCase> candidates) {
        List<Map<String, Object>> testCases = new java.util.ArrayList<>(candidates.size());
        int id = 1;
        for (GeneratedTestCase candidate : candidates) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", "gen-" + id++);
            entry.put("question", candidate.question());
            entry.put("expectedAnswer", candidate.expectedAnswer());
            testCases.add(entry);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("testCases", testCases);
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        return new Yaml(options).dump(root);
    }
}
