package com.aizerohub.ragguard.spring;

import com.aizerohub.ragguard.core.judge.FileJudgeCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SpringAiJudgeCacheTest {

    private static final String FAITHFULNESS_JSON =
            "{\"claims\":[{\"text\":\"a\",\"verdict\":\"SUPPORTED\",\"reason\":\"ok\"}]}";

    @TempDir
    Path tempDir;

    @Test
    void secondRunHitsCache_noAdditionalModelCalls() {
        FileJudgeCache cache = new FileJudgeCache(tempDir.resolve("cache.yml"));
        FakeModels.FakeChatModel chatModel = new FakeModels.FakeChatModel(
                Map.of("decompose the ANSWER", FAITHFULNESS_JSON));
        SpringAiJudge judge = new SpringAiJudge(chatModel, 3, cache, "test-model");

        var first = judge.verifyFaithfulness("answer", List.of("ctx"));
        assertEquals(1, chatModel.calls.get());
        assertEquals(1, cache.size());

        // a NEW judge over the SAME cache file must not call the model again
        FakeModels.FakeChatModel secondModel = new FakeModels.FakeChatModel(
                Map.of("decompose the ANSWER", FAITHFULNESS_JSON));
        SpringAiJudge freshJudge = new SpringAiJudge(secondModel, 3, cache, "test-model");
        var second = freshJudge.verifyFaithfulness("answer", List.of("ctx"));

        assertEquals(0, secondModel.calls.get());
        assertSame(first.getClass(), second.getClass());
        assertEquals(1, second.size());
        assertEquals(1, cache.size());
    }

    @Test
    void differentModelId_doesNotHitCache() {
        FileJudgeCache cache = new FileJudgeCache(tempDir.resolve("cache.yml"));
        SpringAiJudge a = new SpringAiJudge(new FakeModels.FakeChatModel(
                Map.of("decompose the ANSWER", FAITHFULNESS_JSON)), 3, cache, "model-a");
        a.verifyFaithfulness("answer", List.of("ctx"));

        FakeModels.FakeChatModel modelB = new FakeModels.FakeChatModel(
                Map.of("decompose the ANSWER", FAITHFULNESS_JSON));
        SpringAiJudge b = new SpringAiJudge(modelB, 3, cache, "model-b");
        b.verifyFaithfulness("answer", List.of("ctx"));

        assertEquals(1, modelB.calls.get());
        assertEquals(2, cache.size());
    }

    @Test
    void failedParse_isNeverCached() {
        FileJudgeCache cache = new FileJudgeCache(tempDir.resolve("cache.yml"));
        // both attempts return garbage → judge fails after retry, nothing cached
        FakeModels.FakeChatModel broken = new FakeModels.FakeChatModel(
                Map.of("decompose the ANSWER", "definitely not json"));
        SpringAiJudge judge = new SpringAiJudge(broken, 3, cache, "m");
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> judge.verifyFaithfulness("answer", List.of("ctx")));
        assertEquals(2, broken.calls.get()); // initial attempt + one retry
        assertEquals(0, cache.size());
    }
}
