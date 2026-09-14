package com.aizerohub.ragguard.report;

import com.aizerohub.ragguard.core.model.CaseResult;
import com.aizerohub.ragguard.core.model.EvaluationReport;
import com.aizerohub.ragguard.core.model.MetricResult;
import com.aizerohub.ragguard.core.model.MetricType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Reads/writes {@link RunSummary} as JSON in the report output directory.
 * One file ({@code ragguard-latest-run.json}) holds the most recent run —
 * the "previous run" for the next diff, as plain local-file state.
 */
public final class RunSummaryStore {

    /** The file name of the latest-run summary inside the report output directory. */
    public static final String FILE_NAME = "ragguard-latest-run.json";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RunSummaryStore() {
    }

    public static void write(Path outputDir, RunSummary summary) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("caseCount", summary.caseCount());
        ObjectNode aggregates = root.putObject("aggregateScores");
        summary.aggregateScores().forEach((type, score) -> aggregates.put(type.name(), score));
        ObjectNode cases = root.putObject("caseScores");
        summary.caseScores().forEach((caseId, scores) -> {
            ObjectNode caseNode = cases.putObject(caseId);
            scores.forEach((type, score) -> caseNode.put(type.name(), score));
        });
        try {
            Files.createDirectories(outputDir);
            Files.writeString(outputDir.resolve(FILE_NAME),
                    MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write run summary to " + outputDir, e);
        }
    }

    public static Optional<RunSummary> read(Path outputDir) {
        Path file = outputDir.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            JsonNode root = MAPPER.readTree(Files.readString(file, StandardCharsets.UTF_8));
            Map<MetricType, Double> aggregates = readScores(root.get("aggregateScores"));
            Map<String, Map<MetricType, Double>> cases = new LinkedHashMap<>();
            JsonNode caseNode = root.get("caseScores");
            if (caseNode != null) {
                caseNode.fieldNames().forEachRemaining(name ->
                        cases.put(name, readScores(caseNode.get(name))));
            }
            int caseCount = root.path("caseCount").asInt(cases.size());
            return Optional.of(new RunSummary(aggregates, cases, caseCount));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read run summary from " + file, e);
        }
    }

    private static Map<MetricType, Double> readScores(JsonNode node) {
        Map<MetricType, Double> scores = new LinkedHashMap<>();
        if (node != null) {
            node.fieldNames().forEachRemaining(name -> {
                MetricType type = MetricType.valueOf(name);
                scores.put(type, node.get(name).asDouble());
            });
        }
        return scores;
    }

    /** Extracts the persisted summary from a fresh evaluation report. */
    public static RunSummary from(EvaluationReport report) {
        Map<MetricType, Double> aggregates = new LinkedHashMap<>();
        for (MetricType type : MetricType.values()) {
            double score = report.aggregateScore(type);
            if (!Double.isNaN(score)) {
                aggregates.put(type, score);
            }
        }
        Map<String, Map<MetricType, Double>> cases = new LinkedHashMap<>();
        for (CaseResult caseResult : report.caseResults()) {
            Map<MetricType, Double> scores = new LinkedHashMap<>();
            for (MetricResult mr : caseResult.metricResults()) {
                if (mr.hasScore()) {
                    scores.put(mr.metricType(), mr.score());
                }
            }
            cases.put(caseResult.caseId(), scores);
        }
        return new RunSummary(aggregates, cases, report.caseCount());
    }
}
