package com.aizerohub.ragguard.report;

import com.aizerohub.ragguard.core.model.EvaluationReport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * One-stop report output: diffs the current run against the persisted
 * previous run, renders the HTML report, and updates the latest-run
 * summary file ({@link RunSummaryStore#FILE_NAME}).
 */
public final class ReportWriter {

    private final Path outputDir;
    private final HtmlReportRenderer renderer;

    public ReportWriter(Path outputDir, String title) {
        this.outputDir = outputDir;
        this.renderer = new HtmlReportRenderer(title);
    }

    /**
     * Writes the report and returns the HTML file path. The run-over-run
     * diff compares against the summary persisted by the previous call, and
     * the trend chart includes all prior runs recorded in the history file.
     */
    public Path write(EvaluationReport report) {
        java.util.List<RunSummary> history = RunSummaryStore.readHistory(outputDir);
        RunSummary previous = history.isEmpty() ? null : history.get(history.size() - 1);
        RunSummary current = RunSummaryStore.from(report);
        java.util.List<RunSummary> priorRuns = new java.util.ArrayList<>(history);
        if (previous != null && previous.timestamp().equals(current.timestamp())) {
            priorRuns = priorRuns.subList(0, priorRuns.size() - 1); // avoid double-plotting same run
        }
        String html = renderer.render(report, previous, priorRuns);
        RunSummaryStore.write(outputDir, current);
        String fileName = "ragguard-report-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + ".html";
        Path htmlFile = outputDir.resolve(fileName);
        try {
            Files.createDirectories(outputDir);
            Files.writeString(htmlFile, html, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write HTML report to " + htmlFile, e);
        }
        return htmlFile;
    }
}
