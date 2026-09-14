package com.aizerohub.ragguard.core.generate;

import com.aizerohub.ragguard.core.judge.EmbeddingModel;
import com.aizerohub.ragguard.core.math.CosineSimilarity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Generates a deduplicated test-set candidate list from documents:
 * paragraph-boundary chunking → LLM QA generation per chunk →
 * normalization + (optional) embedding-similarity dedup → candidates for
 * human confirmation.
 *
 * <p>The output is deliberately NOT a ready-to-run test set: a human reviews
 * and edits the candidates (via {@link TestSetWriter#toYaml}) before they
 * become evaluation ground truth. That review step is the point.
 */
public final class TestSetGenerator {

    /** Default similarity above which two questions count as duplicates. */
    public static final double DEFAULT_SIMILARITY_THRESHOLD = 0.92;

    private final QuestionGenerator questionGenerator;
    private final EmbeddingModel embeddingModel; // nullable
    private final double similarityThreshold;
    private final int maxQuestionsPerChunk;
    private final int maxChunkChars;

    private TestSetGenerator(Builder b) {
        this.questionGenerator = b.questionGenerator;
        this.embeddingModel = b.embeddingModel;
        this.similarityThreshold = b.similarityThreshold;
        this.maxQuestionsPerChunk = b.maxQuestionsPerChunk;
        this.maxChunkChars = b.maxChunkChars;
    }

    public static Builder builder(QuestionGenerator questionGenerator) {
        return new Builder(questionGenerator);
    }

    /**
     * @param documents named documents, e.g. file name → content
     * @return deduplicated candidates, in generation order
     */
    public List<GeneratedTestCase> generate(java.util.Map<String, String> documents) {
        Objects.requireNonNull(documents, "documents");
        Set<String> normalizedQuestions = new HashSet<>();
        List<double[]> seenEmbeddings = new ArrayList<>();
        List<GeneratedTestCase> candidates = new ArrayList<>();
        for (var docEntry : documents.entrySet()) {
            List<String> chunks = Chunker.chunk(docEntry.getValue(), maxChunkChars);
            for (int i = 0; i < chunks.size(); i++) {
                String chunk = chunks.get(i);
                List<GeneratedQuestion> generated;
                try {
                    generated = questionGenerator.generateFor(chunk, maxQuestionsPerChunk);
                } catch (RuntimeException e) {
                    // one bad chunk must not abort the whole run — surfaced per chunk by the caller if needed
                    continue;
                }
                for (GeneratedQuestion gq : generated) {
                    if (normalizedQuestions.add(normalize(gq.question()))) {
                        if (embeddingModel != null && isNearDuplicate(gq.question(), seenEmbeddings)) {
                            normalizedQuestions.remove(normalize(gq.question()));
                            continue;
                        }
                        if (embeddingModel != null) {
                            seenEmbeddings.add(embeddingModel.embed(gq.question()));
                        }
                        candidates.add(new GeneratedTestCase(gq.question(), gq.answer(),
                                chunk.isEmpty() ? docEntry.getKey() : chunk));
                    }
                }
            }
        }
        return List.copyOf(candidates);
    }

    private boolean isNearDuplicate(String question, List<double[]> seenEmbeddings) {
        if (seenEmbeddings.isEmpty()) {
            return false;
        }
        double[] vector;
        try {
            vector = embeddingModel.embed(question);
        } catch (RuntimeException e) {
            return false; // dedup is best-effort; never block generation on it
        }
        for (double[] seen : seenEmbeddings) {
            if (seen.length == vector.length
                    && Math.abs(CosineSimilarity.of(vector, seen)) >= similarityThreshold) {
                return true;
            }
        }
        return false;
    }

    static String normalize(String question) {
        return question.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}\\s]+", " ")
                .strip();
    }

    public static final class Builder {

        private final QuestionGenerator questionGenerator;
        private EmbeddingModel embeddingModel;
        private double similarityThreshold = DEFAULT_SIMILARITY_THRESHOLD;
        private int maxQuestionsPerChunk = 3;
        private int maxChunkChars = 1600;

        private Builder(QuestionGenerator questionGenerator) {
            this.questionGenerator = Objects.requireNonNull(questionGenerator, "questionGenerator");
        }

        /** Enables embedding-based near-duplicate detection. */
        public Builder embedding(EmbeddingModel embeddingModel) {
            this.embeddingModel = embeddingModel;
            return this;
        }

        public Builder similarityThreshold(double threshold) {
            if (threshold <= 0 || threshold > 1) {
                throw new IllegalArgumentException("similarityThreshold must be within (0, 1]");
            }
            this.similarityThreshold = threshold;
            return this;
        }

        public Builder maxQuestionsPerChunk(int n) {
            if (n < 1) {
                throw new IllegalArgumentException("maxQuestionsPerChunk must be >= 1");
            }
            this.maxQuestionsPerChunk = n;
            return this;
        }

        public Builder maxChunkChars(int maxChunkChars) {
            if (maxChunkChars < 100) {
                throw new IllegalArgumentException("maxChunkChars must be >= 100");
            }
            this.maxChunkChars = maxChunkChars;
            return this;
        }

        public TestSetGenerator build() {
            return new TestSetGenerator(this);
        }
    }
}
