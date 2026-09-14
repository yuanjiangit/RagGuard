package com.aizerohub.ragguard.core.generate;

import com.aizerohub.ragguard.core.judge.EmbeddingModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSetGeneratorTest {

    /** Deterministic generator: distinct questions per chunk, grounded in the chunk text. */
    private static QuestionGenerator generator(int perChunk) {
        return (chunk, n) -> {
            List<GeneratedQuestion> result = new java.util.ArrayList<>();
            for (int i = 0; i < Math.min(perChunk, n); i++) {
                result.add(new GeneratedQuestion(
                        "Question " + i + " about: " + chunk.substring(0, Math.min(20, chunk.length())),
                        "Answer from chunk"));
            }
            return result;
        };
    }

    private static final String DOC = """
            Paragraph one covers the four RagGuard metrics that RagGuard computes for every case.

            Paragraph two covers judge stability engineering: temperature zero and JSON output.

            Paragraph three covers HTML report rendering with run-over-run diff comparison.
            """;

    @Test
    void generatesPerChunk_withSourceExcerpt() {
        TestSetGenerator generator = TestSetGenerator.builder(generator(2))
                .maxQuestionsPerChunk(2)
                .maxChunkChars(120)
                .build();
        List<GeneratedTestCase> candidates = generator.generate(Map.of("doc.md", DOC));
        // 3 paragraphs, each under 60 chars → 3 chunks → 2 questions per chunk = 6
        assertEquals(6, candidates.size());
        for (GeneratedTestCase candidate : candidates) {
            assertTrue(candidate.sourceExcerpt().contains("Paragraph"));
        }
    }

    @Test
    void exactDuplicates_removedByNormalization() {
        QuestionGenerator same = (chunk, n) -> List.of(new GeneratedQuestion("  What  is RagGuard?  ", "x"));
        TestSetGenerator generator = TestSetGenerator.builder(same).build();
        List<GeneratedTestCase> candidates = generator.generate(Map.of(
                "a.md", "Chunk A content.\n\nChunk B content.",
                "b.md", "Chunk C content."));
        assertEquals(1, candidates.size());
    }

    @Test
    void nearDuplicates_removedByEmbeddingSimilarity() {
        Map<String, double[]> vectors = new HashMap<>();
        EmbeddingModel embedding = text -> {
            // same first token → same vector → cosine 1
            return vectors.computeIfAbsent(text.split(" ")[2], k -> new double[] {1, 0});
        };
        QuestionGenerator similar = (chunk, n) -> List.of(
                new GeneratedQuestion("alpha question one", "x"),
                new GeneratedQuestion("alpha question two", "y"));
        TestSetGenerator generator = TestSetGenerator.builder(similar)
                .embedding(embedding)
                .similarityThreshold(0.9)
                .build();
        List<GeneratedTestCase> candidates = generator.generate(Map.of("a.md", "Chunk."));
        assertEquals(1, candidates.size());
    }

    @Test
    void generatorFailure_skipsChunkOnly() {
        QuestionGenerator flaky = (chunk, n) -> {
            if (chunk.contains("Chunk B content")) {
                throw new IllegalStateException("boom");
            }
            return List.of(new GeneratedQuestion("q-" + chunk.substring(0, 15), "a"));
        };
        TestSetGenerator generator = TestSetGenerator.builder(flaky).maxChunkChars(100).build();
        List<GeneratedTestCase> candidates = generator.generate(Map.of(
                "a.md", "Chunk A content with some padding text to exceed the minimum.\n\n"
                        + "Chunk B content with some padding text to exceed the minimum.\n\n"
                        + "Chunk C content with some padding text to exceed the minimum."));
        assertEquals(2, candidates.size());
    }

    @Test
    void writer_producesLoadableYaml(@TempDir Path tempDir) throws Exception {
        List<GeneratedTestCase> candidates = List.of(
                new GeneratedTestCase("What: is it?", "An \"evaluation\" framework\nfor RAG.", "excerpt"));
        String yaml = TestSetWriter.toYaml(candidates);
        Path file = tempDir.resolve("generated.yml");
        Files.writeString(file, yaml);
        // roundtrip through the existing loader — the checklist is directly usable
        var loaded = com.aizerohub.ragguard.core.testset.TestSetLoader.loadYaml(file);
        assertEquals(1, loaded.size());
        assertEquals("gen-1", loaded.get(0).id());
        assertEquals("What: is it?", loaded.get(0).question());
        assertEquals("An \"evaluation\" framework\nfor RAG.", loaded.get(0).expectedAnswer());
    }
}
