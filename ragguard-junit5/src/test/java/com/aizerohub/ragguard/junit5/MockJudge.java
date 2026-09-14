package com.aizerohub.ragguard.junit5;

import com.aizerohub.ragguard.core.judge.Judge;
import com.aizerohub.ragguard.core.model.Claim;
import com.aizerohub.ragguard.core.model.ClaimAttribution;
import com.aizerohub.ragguard.core.model.ClaimVerdict;
import com.aizerohub.ragguard.core.model.ContextUsefulness;
import com.aizerohub.ragguard.core.model.SupportVerdict;

import java.util.List;

/**
 * Local fixed-output mock judge for extension tests (a copy of the core
 * test fixture — test classes are not shared between modules).
 */
final class MockJudge implements Judge {

    List<ClaimVerdict> faithfulnessVerdicts;
    RuntimeException faithfulnessError;
    List<ClaimAttribution> recallAttributions;
    RuntimeException recallError;
    List<ContextUsefulness> precisionVerdicts;
    RuntimeException precisionError;
    List<String> reverseQuestions;
    RuntimeException reverseError;

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

    static ClaimVerdict supported(String text) {
        return new ClaimVerdict(new Claim(text), SupportVerdict.SUPPORTED, "in contexts");
    }
}
