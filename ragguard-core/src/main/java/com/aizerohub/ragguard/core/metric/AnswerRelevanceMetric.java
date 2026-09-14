package com.aizerohub.ragguard.core.metric;

import com.aizerohub.ragguard.core.judge.EmbeddingModel;
import com.aizerohub.ragguard.core.judge.Judge;
import com.aizerohub.ragguard.core.math.CosineSimilarity;
import com.aizerohub.ragguard.core.model.EvaluationInput;
import com.aizerohub.ragguard.core.model.MetricResult;
import com.aizerohub.ragguard.core.model.MetricStatus;
import com.aizerohub.ragguard.core.model.MetricType;
import com.aizerohub.ragguard.core.model.ReverseQuestion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Answer relevance via reverse question generation (design notes §2): the
 * judge reverse-generates N plausible questions from the answer, and the
 * score is the mean embedding cosine similarity between each generated
 * question and the original question. Avoids any dependency on a
 * ground-truth answer.
 */
public final class AnswerRelevanceMetric implements Metric {

    /** Default number of reverse-generated questions, following the Ragas paper (N=3). */
    public static final int DEFAULT_NUM_QUESTIONS = 3;

    private final Judge judge;
    private final EmbeddingModel embeddingModel;
    private final int numQuestions;

    public AnswerRelevanceMetric(Judge judge, EmbeddingModel embeddingModel) {
        this(judge, embeddingModel, DEFAULT_NUM_QUESTIONS);
    }

    public AnswerRelevanceMetric(Judge judge, EmbeddingModel embeddingModel, int numQuestions) {
        this.judge = Objects.requireNonNull(judge, "judge");
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embeddingModel");
        if (numQuestions < 1) {
            throw new IllegalArgumentException("numQuestions must be >= 1");
        }
        this.numQuestions = numQuestions;
    }

    @Override
    public MetricType type() {
        return MetricType.ANSWER_RELEVANCE;
    }

    @Override
    public MetricResult evaluate(EvaluationInput input) {
        if (FaithfulnessMetric.isBlank(input.answer())) {
            return MetricResult.builder(MetricType.ANSWER_RELEVANCE)
                    .status(MetricStatus.SKIPPED)
                    .message("answer is blank")
                    .build();
        }
        List<String> generated;
        try {
            generated = judge.generateReverseQuestions(input.answer(), numQuestions);
        } catch (RuntimeException e) {
            return MetricResult.builder(MetricType.ANSWER_RELEVANCE)
                    .status(MetricStatus.JUDGE_FAILED)
                    .message("judge call failed: " + e.getMessage())
                    .build();
        }
        if (generated == null || generated.isEmpty()) {
            return MetricResult.builder(MetricType.ANSWER_RELEVANCE)
                    .status(MetricStatus.JUDGE_FAILED)
                    .message("judge returned no reverse questions")
                    .build();
        }
        double[] questionEmbedding;
        try {
            questionEmbedding = embeddingModel.embed(input.question());
        } catch (RuntimeException e) {
            return embeddingFailed(e);
        }
        List<ReverseQuestion> details = new ArrayList<>(generated.size());
        double sum = 0;
        try {
            for (String q : generated) {
                double similarity = CosineSimilarity.of(embeddingModel.embed(q), questionEmbedding);
                details.add(new ReverseQuestion(q, similarity));
                sum += similarity;
            }
        } catch (RuntimeException e) {
            return embeddingFailed(e);
        }
        return MetricResult.builder(MetricType.ANSWER_RELEVANCE)
                .score(sum / generated.size())
                .reverseQuestions(details)
                .build();
    }

    private static MetricResult embeddingFailed(RuntimeException e) {
        return MetricResult.builder(MetricType.ANSWER_RELEVANCE)
                .status(MetricStatus.JUDGE_FAILED)
                .message("embedding call failed: " + e.getMessage())
                .build();
    }
}
