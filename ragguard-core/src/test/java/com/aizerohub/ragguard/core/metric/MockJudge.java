package com.aizerohub.ragguard.core.metric;

import com.aizerohub.ragguard.core.judge.Judge;
import com.aizerohub.ragguard.core.model.Claim;
import com.aizerohub.ragguard.core.model.ClaimAttribution;
import com.aizerohub.ragguard.core.model.ClaimVerdict;
import com.aizerohub.ragguard.core.model.ContextUsefulness;
import com.aizerohub.ragguard.core.model.SupportVerdict;

import java.util.List;

/**
 * Fixed-output mock judge shared by the offline metric tests. Not picked up
 * by surefire (name does not match the *Test include pattern).
 */
public final class MockJudge implements Judge {

    public List<ClaimVerdict> faithfulnessVerdicts;
    public RuntimeException faithfulnessError;
    public List<ClaimAttribution> recallAttributions;
    public RuntimeException recallError;
    public List<ContextUsefulness> precisionVerdicts;
    public RuntimeException precisionError;
    public List<String> reverseQuestions;
    public RuntimeException reverseError;

    public static ClaimVerdict supported(String text) {
        return new ClaimVerdict(new Claim(text), SupportVerdict.SUPPORTED, "in contexts");
    }

    public static ClaimVerdict refuted(String text) {
        return new ClaimVerdict(new Claim(text), SupportVerdict.REFUTED, "contradicts contexts");
    }

    public static ClaimVerdict notEnoughInfo(String text) {
        return new ClaimVerdict(new Claim(text), SupportVerdict.NOT_ENOUGH_INFO, "not covered");
    }

    @Override
    public List<ClaimVerdict> verifyFaithfulness(String answer, List<String> contexts) {
        if (faithfulnessError != null) {
            throw faithfulnessError;
        }
        return faithfulnessVerdicts;
    }

    @Override
    public List<ClaimAttribution> attributeContextRecall(String expectedAnswer, List<String> contexts) {
        if (recallError != null) {
            throw recallError;
        }
        return recallAttributions;
    }

    @Override
    public List<ContextUsefulness> judgeContextPrecision(String question, List<String> contexts) {
        if (precisionError != null) {
            throw precisionError;
        }
        return precisionVerdicts;
    }

    @Override
    public List<String> generateReverseQuestions(String answer, int n) {
        if (reverseError != null) {
            throw reverseError;
        }
        return reverseQuestions;
    }
}
