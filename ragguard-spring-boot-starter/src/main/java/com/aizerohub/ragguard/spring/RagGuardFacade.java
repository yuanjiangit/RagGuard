package com.aizerohub.ragguard.spring;

import com.aizerohub.ragguard.core.engine.EvaluationEngine;
import com.aizerohub.ragguard.core.model.EvaluationReport;
import com.aizerohub.ragguard.core.model.RagTestCase;
import com.aizerohub.ragguard.core.testset.TestSetFormatException;
import com.aizerohub.ragguard.core.testset.TestSetLoader;
import com.aizerohub.ragguard.report.ReportWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * The Spring-facing entry point: runs the configured test set against the
 * application's {@code RagChain} bean and (optionally) writes the HTML
 * report with run-over-run diff to {@code ragguard.report-output-dir}.
 *
 * <pre>{@code
 * @Autowired RagGuardFacade ragGuard;
 *
 * EvaluationReport report = ragGuard.run();   // blocking
 * }</pre>
 */
public final class RagGuardFacade {

    private static final Logger log = LoggerFactory.getLogger(RagGuardFacade.class);

    private final EvaluationEngine engine;
    private final String testSetLocation;
    private final Optional<ReportWriter> reportWriter;

    RagGuardFacade(EvaluationEngine engine, String testSetLocation, Optional<ReportWriter> reportWriter) {
        this.engine = engine;
        this.testSetLocation = testSetLocation;
        this.reportWriter = reportWriter;
    }

    /** Runs the full evaluation; returns the report (also written to disk when a report dir is configured). */
    public EvaluationReport run() {
        List<RagTestCase> testCases = loadTestSet();
        EvaluationReport report = engine.run(testCases);
        reportWriter.ifPresent(writer -> {
            Path html = writer.write(report);
            log.info("RagGuard report written to {}", html.toAbsolutePath());
        });
        return report;
    }

    public String testSetLocation() {
        return testSetLocation;
    }

    private List<RagTestCase> loadTestSet() {
        String location = testSetLocation == null || testSetLocation.isBlank()
                ? "ragguard-test-set.yml"
                : testSetLocation.trim();
        String resource = location.startsWith("classpath:")
                ? location.substring("classpath:".length())
                : location;
        Path file = Path.of(resource);
        if (Files.isRegularFile(file)) {
            return TestSetLoader.loadYaml(file);
        }
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream(stripLeadingSlash(resource))) {
            if (in == null) {
                throw new TestSetFormatException("test set not found: '" + location
                        + "' (not a file and not on the classpath)");
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return TestSetLoader.loadYaml(reader);
            }
        } catch (IOException e) {
            throw new TestSetFormatException("cannot read test set: " + location, e);
        }
    }

    private static String stripLeadingSlash(String s) {
        return s.startsWith("/") ? s.substring(1) : s;
    }
}
