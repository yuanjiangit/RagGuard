package com.aizerohub.ragguard.report;

import com.aizerohub.ragguard.core.model.CaseResult;
import com.aizerohub.ragguard.core.model.ClaimAttribution;
import com.aizerohub.ragguard.core.model.ClaimVerdict;
import com.aizerohub.ragguard.core.model.ContextUsefulness;
import com.aizerohub.ragguard.core.model.EvaluationReport;
import com.aizerohub.ragguard.core.model.MetricResult;
import com.aizerohub.ragguard.core.model.MetricStatus;
import com.aizerohub.ragguard.core.model.MetricType;
import com.aizerohub.ragguard.core.model.ReverseQuestion;
import com.aizerohub.ragguard.core.model.SupportVerdict;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Renders an {@link EvaluationReport} as a single self-contained HTML file
 * (inline CSS, no external assets): overall score, per-metric aggregates
 * with a run-over-run diff, per-case score table, and full drill-down
 * (claims, attributions, context verdicts, reverse questions).
 */
public final class HtmlReportRenderer {

    private final String title;

    public HtmlReportRenderer(String title) {
        this.title = title == null || title.isBlank() ? "RagGuard Evaluation Report" : title;
    }

    public String render(EvaluationReport report, RunSummary previousRun) {
        StringBuilder html = new StringBuilder(16 * 1024);
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
                .append("<title>").append(escape(title)).append("</title>\n")
                .append(STYLES).append("</head>\n<body>\n");
        header(html, report);
        aggregateTable(html, report, previousRun);
        caseTable(html, report, previousRun);
        drillDown(html, report);
        html.append("</body>\n</html>\n");
        return html.toString();
    }

    private void header(StringBuilder html, EvaluationReport report) {
        html.append("<h1>").append(escape(title)).append("</h1>\n");
        double overall = report.overallScore();
        html.append("<p class=\"overall\">Overall score: <strong class=\"")
                .append(scoreClass(overall)).append("\">")
                .append(formatScore(overall)).append("</strong>")
                .append(" &middot; ").append(report.caseCount()).append(" test case(s)</p>\n");
    }

    private void aggregateTable(StringBuilder html, EvaluationReport report, RunSummary previous) {
        html.append("<h2>Metrics</h2>\n<table>\n<tr><th>Metric</th><th>Score</th><th>vs previous run</th></tr>\n");
        for (MetricType type : MetricType.values()) {
            double score = report.aggregateScore(type);
            html.append("<tr><td>").append(type).append("</td><td class=\"")
                    .append(scoreClass(score)).append("\">").append(formatScore(score))
                    .append("</td><td>").append(diffCell(previous, type, score)).append("</td></tr>\n");
        }
        html.append("</table>\n");
    }

    private void caseTable(StringBuilder html, EvaluationReport report, RunSummary previous) {
        html.append("<h2>Test cases</h2>\n<table>\n<tr><th>Case</th><th>Question</th>");
        for (MetricType type : MetricType.values()) {
            html.append("<th>").append(type).append("</th>");
        }
        html.append("</tr>\n");
        for (CaseResult caseResult : report.caseResults()) {
            html.append("<tr><td><code>").append(escape(caseResult.caseId()))
                    .append("</code></td><td>").append(escape(truncate(caseResult.question(), 120)));
            for (MetricType type : MetricType.values()) {
                Optional<MetricResult> mr = caseResult.metric(type);
                Double previousScore = previous == null ? null : previous.caseScores()
                        .getOrDefault(caseResult.caseId(), java.util.Map.of()).get(type);
                html.append("<td class=\"").append(mr.map(r -> scoreClass(r.score())).orElse("na"))
                        .append("\">").append(mr.map(MetricResult::score).map(HtmlReportRenderer::formatScore)
                                .orElse("n/a"));
                if (previousScore != null && mr.isPresent() && mr.get().hasScore()) {
                    html.append(" ").append(deltaBadge(mr.get().score() - previousScore));
                }
                html.append("</td>");
            }
            html.append("</tr>\n");
        }
        html.append("</table>\n");
    }

    private void drillDown(StringBuilder html, EvaluationReport report) {
        html.append("<h2>Drill-down</h2>\n");
        for (CaseResult caseResult : report.caseResults()) {
            html.append("<details><summary><code>").append(escape(caseResult.caseId()))
                    .append("</code> — ").append(escape(truncate(caseResult.question(), 100)))
                    .append("</summary>\n");
            for (MetricResult mr : caseResult.metricResults()) {
                html.append("<h3>").append(mr.metricType()).append(" — ")
                        .append(mr.status());
                if (mr.hasScore()) {
                    html.append(" (").append(formatScore(mr.score())).append(")");
                }
                html.append("</h3>\n");
                mr.message().ifPresent(m ->
                        html.append("<p class=\"message\">").append(escape(m)).append("</p>\n"));
                html.append("<ul>\n");
                for (ClaimVerdict cv : mr.claimVerdicts()) {
                    html.append("<li class=\"").append(claimClass(cv.verdict())).append("\"><strong>[")
                            .append(cv.verdict()).append("]</strong> ").append(escape(cv.claim().text()));
                    if (cv.reason() != null && !cv.reason().isBlank()) {
                        html.append(" — ").append(escape(cv.reason()));
                    }
                    html.append("</li>\n");
                }
                for (ClaimAttribution ca : mr.claimAttributions()) {
                    html.append("<li class=\"").append(ca.attributable() ? "ok" : "bad").append("\"><strong>[")
                            .append(ca.attributable() ? "ATTRIBUTED to context " + ca.attributedContextIndex()
                                    : "NOT ATTRIBUTED")
                            .append("]</strong> ").append(escape(ca.claim().text()));
                    if (ca.reason() != null && !ca.reason().isBlank()) {
                        html.append(" — ").append(escape(ca.reason()));
                    }
                    html.append("</li>\n");
                }
                for (ContextUsefulness cu : mr.contextVerdicts()) {
                    html.append("<li class=\"").append(cu.useful() ? "ok" : "warn").append("\">context[")
                            .append(cu.contextIndex()).append("]: <strong>").append(cu.useful() ? "USEFUL" : "NOT USEFUL")
                            .append("</strong>");
                    if (cu.reason() != null && !cu.reason().isBlank()) {
                        html.append(" — ").append(escape(cu.reason()));
                    }
                    html.append("</li>\n");
                }
                for (ReverseQuestion rq : mr.reverseQuestions()) {
                    html.append(String.format(Locale.ROOT,
                            "<li>reverse question (similarity %.3f): %s</li>%n",
                            rq.similarity(), escape(rq.question())));
                }
                html.append("</ul>\n");
            }
            html.append("</details>\n");
        }
    }

    private static String diffCell(RunSummary previous, MetricType type, double currentScore) {
        if (previous == null) {
            return "<span class=\"na\">first run</span>";
        }
        Double delta = previous.deltaFor(type, Double.isNaN(currentScore) ? Double.NaN : currentScore);
        if (delta == null || delta.isNaN()) {
            return "<span class=\"na\">n/a</span>";
        }
        return deltaBadge(delta) + " (was " + formatScore(previous.aggregate(type)) + ")";
    }

    private static String deltaBadge(double delta) {
        String arrow = delta > 0.0005 ? "▲" : delta < -0.0005 ? "▼" : "＝";
        String css = delta > 0.0005 ? "up" : delta < -0.0005 ? "down" : "flat";
        return String.format(Locale.ROOT, "<span class=\"%s\">%s %.3f</span>", css, arrow, delta);
    }

    private static String claimClass(SupportVerdict verdict) {
        return switch (verdict) {
            case SUPPORTED -> "ok";
            case REFUTED -> "bad";
            case NOT_ENOUGH_INFO -> "warn";
        };
    }

    private static String scoreClass(double score) {
        if (Double.isNaN(score)) {
            return "na";
        }
        return score >= 0.8 ? "good" : score >= 0.5 ? "warn" : "bad";
    }

    private static String formatScore(double score) {
        return Double.isNaN(score) ? "n/a" : String.format(Locale.ROOT, "%.3f", score);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringJoiner joiner = new StringJoiner("");
        s.chars().forEachOrdered(c -> {
            switch (c) {
                case '<' -> joiner.add("&lt;");
                case '>' -> joiner.add("&gt;");
                case '&' -> joiner.add("&amp;");
                case '"' -> joiner.add("&quot;");
                default -> joiner.add(Character.toString(c));
            }
        });
        return joiner.toString();
    }

    private static final String STYLES = """
            <style>
              :root { color-scheme: light; }
              body { font-family: -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial,
                     "PingFang SC", "Microsoft YaHei", sans-serif; margin: 2rem auto;
                     max-width: 60rem; padding: 0 1rem; color: #1a202c; line-height: 1.5; }
              h1 { font-size: 1.5rem; border-bottom: 2px solid #2b6cb0; padding-bottom: .4rem; }
              h2 { font-size: 1.1rem; margin-top: 2rem; }
              table { border-collapse: collapse; width: 100%; margin: .5rem 0 1.5rem; font-size: .9rem; }
              th, td { border: 1px solid #e2e8f0; padding: .45rem .6rem; text-align: left;
                       vertical-align: top; }
              th { background: #edf2f7; }
              .overall { font-size: 1.05rem; }
              .good { color: #276749; font-weight: 600; }
              .warn { color: #975a16; font-weight: 600; }
              .bad  { color: #9b2c2c; font-weight: 600; }
              .na   { color: #718096; }
              .up   { color: #276749; }
              .down { color: #9b2c2c; }
              .flat { color: #718096; }
              .message { color: #718096; font-style: italic; }
              ul { margin: .3rem 0 .8rem; padding-left: 1.2rem; }
              li.ok   { border-left: 3px solid #68d391; padding-left: .5rem; margin: .2rem 0; }
              li.warn { border-left: 3px solid #f6e05e; padding-left: .5rem; margin: .2rem 0; }
              li.bad  { border-left: 3px solid #fc8181; padding-left: .5rem; margin: .2rem 0; }
              details { margin-bottom: 1rem; }
              summary { cursor: pointer; font-weight: 600; }
              h3 { font-size: .95rem; margin: .8rem 0 .3rem; }
              code { background: #edf2f7; padding: 0 .25rem; border-radius: 3px; }
            </style>
            """;
}
