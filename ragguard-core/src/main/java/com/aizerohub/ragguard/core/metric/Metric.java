package com.aizerohub.ragguard.core.metric;

import com.aizerohub.ragguard.core.model.EvaluationInput;
import com.aizerohub.ragguard.core.model.MetricResult;
import com.aizerohub.ragguard.core.model.MetricType;

/**
 * A single metric: turns one {@link EvaluationInput} into one
 * {@link MetricResult}. Implementations are stateless and thread-safe;
 * judge/embedding calls are confined to the {@link com.aizerohub.ragguard.core.judge}
 * abstractions so every metric is verifiable offline with fixed mocks.
 */
public interface Metric {

    MetricType type();

    MetricResult evaluate(EvaluationInput input);
}
