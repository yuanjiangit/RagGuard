package com.aizerohub.ragguard.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * {@code ragguard.*} configuration properties.
 */
@ConfigurationProperties(prefix = "ragguard")
public class RagGuardProperties {

    /** Master switch for the RagGuard auto-configuration. */
    private boolean enabled = true;

    /**
     * Test set location: a classpath resource (optionally prefixed with
     * {@code classpath:}) or a file path relative to the working directory.
     */
    private String testSet = "ragguard-test-set.yml";

    /** Metrics to evaluate; empty means all four. Values: FAITHFULNESS, ANSWER_RELEVANCE, CONTEXT_RECALL, CONTEXT_PRECISION. */
    private Set<String> metrics = new LinkedHashSet<>();

    /** Bounded concurrency for batch evaluation. */
    private int parallelism = 4;

    /**
     * Directory for HTML reports and the latest-run summary; empty disables
     * report writing.
     */
    private String reportOutputDir;

    /** Title shown in the HTML report. */
    private String reportTitle = "RagGuard Evaluation Report";

    /** Enables the judge response cache (keyed by model + prompt version + input). */
    private boolean judgeCacheEnabled = false;

    /** Cache file location (YAML); relative to the working directory. */
    private String judgeCacheFile = "ragguard-judge-cache.yml";

    /**
     * Model identity participating in the cache key (e.g. "gpt-4o-mini") —
     * cached verdicts are reused only for the same model.
     */
    private String judgeModelId = "default";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTestSet() {
        return testSet;
    }

    public void setTestSet(String testSet) {
        this.testSet = testSet;
    }

    public Set<String> getMetrics() {
        return metrics;
    }

    public void setMetrics(Set<String> metrics) {
        this.metrics = metrics;
    }

    public int getParallelism() {
        return parallelism;
    }

    public void setParallelism(int parallelism) {
        this.parallelism = parallelism;
    }

    public String getReportOutputDir() {
        return reportOutputDir;
    }

    public void setReportOutputDir(String reportOutputDir) {
        this.reportOutputDir = reportOutputDir;
    }

    public String getReportTitle() {
        return reportTitle;
    }

    public void setReportTitle(String reportTitle) {
        this.reportTitle = reportTitle;
    }

    public boolean isJudgeCacheEnabled() {
        return judgeCacheEnabled;
    }

    public void setJudgeCacheEnabled(boolean judgeCacheEnabled) {
        this.judgeCacheEnabled = judgeCacheEnabled;
    }

    public String getJudgeCacheFile() {
        return judgeCacheFile;
    }

    public void setJudgeCacheFile(String judgeCacheFile) {
        this.judgeCacheFile = judgeCacheFile;
    }

    public String getJudgeModelId() {
        return judgeModelId;
    }

    public void setJudgeModelId(String judgeModelId) {
        this.judgeModelId = judgeModelId;
    }
}
