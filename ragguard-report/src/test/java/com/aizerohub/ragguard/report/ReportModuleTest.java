package com.aizerohub.ragguard.report;

import com.aizerohub.ragguard.core.model.CaseResult;
import com.aizerohub.ragguard.core.model.Claim;
import com.aizerohub.ragguard.core.model.ClaimVerdict;
import com.aizerohub.ragguard.core.model.EvaluationReport;
import com.aizerohub.ragguard.core.model.MetricResult;
import com.aizerohub.ragguard.core.model.MetricType;
import com.aizerohub.ragguard.core.model.SupportVerdict;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportModuleTest {

    private static EvaluationReport report(double faithfulness) {
        MetricResult faith = MetricResult.builder(MetricType.FAITHFULNESS)
                .score(faithfulness)
                .claimVerdicts(List.of(new ClaimVerdict(new Claim("c1"), SupportVerdict.SUPPORTED, "ok")))
                .build();
        MetricResult recall = MetricResult.builder(MetricType.CONTEXT_RECALL).score(0.9).build();
        CaseResult caseResult = new CaseResult("q1", "What is RagGuard?", List.of(faith, recall));
        return EvaluationReport.of(List.of(caseResult));
    }

    @TempDir
    Path tempDir;

    @Test
    void runSummaryStore_writeReadRoundtrip() {
        RunSummary summary = RunSummaryStore.from(report(0.75));
        RunSummaryStore.write(tempDir, summary);
        RunSummary loaded = RunSummaryStore.read(tempDir).orElseThrow();
        assertEquals(0.75, loaded.aggregate(MetricType.FAITHFULNESS), 1e-9);
        assertEquals(1, loaded.caseCount());
        assertEquals(0.9, loaded.caseScores().get("q1").get(MetricType.CONTEXT_RECALL), 1e-9);
        assertEquals(0.15, loaded.deltaFor(MetricType.FAITHFULNESS, 0.9), 1e-9);
    }

    @Test
    void runSummaryStore_missingFile_returnsEmpty() {
        assertTrue(RunSummaryStore.read(tempDir).isEmpty());
    }

    @Test
    void reportWriter_rendersHtmlAndPersistsSummary() throws Exception {
        ReportWriter writer = new ReportWriter(tempDir, "Demo Run");
        Path html = writer.write(report(0.9));
        assertTrue(Files.isRegularFile(html));
        String content = Files.readString(html);
        assertTrue(content.contains("Demo Run"));
        assertTrue(content.contains("q1"));
        assertTrue(content.contains("first run"));
        assertTrue(content.contains("ATTRIBUTED") || content.contains("[SUPPORTED]"));

        // second run: diff against persisted summary appears in HTML
        Path second = writer.write(report(0.6));
        String secondContent = Files.readString(second);
        assertTrue(secondContent.contains("▼"));
        assertTrue(secondContent.contains("was 0.900"));
    }

    @Test
    void reportWriter_trendChartAppearsFromSecondRun() throws Exception {
        ReportWriter writer = new ReportWriter(tempDir, "Trend Run");
        Path first = writer.write(report(0.9));
        assertTrue(!Files.readString(first).contains("<svg"), "first run has no trend yet");
        Path second = writer.write(report(0.7));
        String content = Files.readString(second);
        assertTrue(content.contains("<svg"), "trend SVG appears from the second run");
        assertTrue(content.contains("Trend"));
        // history file has one line per run
        Path historyFile = tempDir.resolve(RunSummaryStore.HISTORY_FILE_NAME);
        assertEquals(2, Files.readAllLines(historyFile).size());
        // history roundtrip: both runs readable, oldest first
        var history = RunSummaryStore.readHistory(tempDir);
        assertEquals(2, history.size());
        assertEquals(0.9, history.get(0).aggregate(MetricType.FAITHFULNESS), 1e-9);
        assertEquals(0.7, history.get(1).aggregate(MetricType.FAITHFULNESS), 1e-9);
    }

    @Test
    void htmlEscaping_preventsInjection() throws Exception {
        MetricResult result = MetricResult.builder(MetricType.FAITHFULNESS).score(0.0)
                .claimVerdicts(List.of(new ClaimVerdict(new Claim("<script>alert(1)</script>"),
                        SupportVerdict.REFUTED, "x")))
                .build();
        EvaluationReport malicious = EvaluationReport.of(List.of(
                new CaseResult("q<script>", "question", List.of(result))));
        Path html = new ReportWriter(tempDir, "t").write(malicious);
        String content = Files.readString(html);
        assertTrue(content.contains("&lt;script&gt;"));
        assertTrue(!content.contains("<script>alert"));
    }
}
