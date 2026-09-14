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
import java.time.Instant;
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

    /** Append-only history file (one JSON object per run) for the trend chart. */
    public static final String HISTORY_FILE_NAME = "ragguard-history.jsonl";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RunSummaryStore() {
    }

    public static void write(Path outputDir, RunSummary summary) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("timestamp", summary.timestamp().toString());
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
            appendHistory(outputDir, root);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write run summary to " + outputDir, e);
        }
    }

    /** Appends one compact line (timestamp + aggregates) to the history file. */
    private static void appendHistory(Path outputDir, ObjectNode fullRun) throws IOException {
        ObjectNode line = MAPPER.createObjectNode();
        line.put("timestamp", fullRun.get("timestamp").asText());
        line.set("aggregateScores", fullRun.get("aggregateScores"));
        line.put("caseCount", fullRun.get("caseCount").asInt());
        Files.writeString(outputDir.resolve(HISTORY_FILE_NAME),
                MAPPER.writeValueAsString(line) + "\n",
                StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
    }

    /** Reads the append-only history, oldest first. Unparseable lines are skipped. */
    public static java.util.List<RunSummary> readHistory(Path outputDir) {
        Path file = outputDir.resolve(HISTORY_FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return java.util.List.of();
        }
        java.util.List<RunSummary> history = new java.util.ArrayList<>();
        try {
            for (String lineContent : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (lineContent.isBlank()) {
                    continue;
                }
                try {
                    JsonNode node = MAPPER.readTree(lineContent);
                    Map<MetricType, Double> scores = readScores(node.get("aggregateScores"));
                    history.add(new RunSummary(Instant.parse(node.path("timestamp").asText()),
                            scores, java.util.Map.of(), node.path("caseCount").asInt()));
                } catch (RuntimeException ignored) {
                    // tolerate a torn/corrupt line — trend charts are best-effort
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read run history from " + file, e);
        }
        return java.util.List.copyOf(history);
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
            String ts = root.path("timestamp").asText(null);
            Instant timestamp = ts == null ? Instant.now() : Instant.parse(ts);
            return Optional.of(new RunSummary(timestamp, aggregates, cases, caseCount));
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
        return from(report, Instant.now());
    }

    public static RunSummary from(EvaluationReport report, Instant timestamp) {
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
        return new RunSummary(timestamp, aggregates, cases, report.caseCount());
    }
}
