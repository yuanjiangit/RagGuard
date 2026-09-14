package com.aizerohub.ragguard.core.judge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JudgeCacheTest {

    @TempDir
    Path tempDir;

    @Test
    void fileCache_roundtripAndPersistence() {
        Path file = tempDir.resolve("cache.yml");
        FileJudgeCache first = new FileJudgeCache(file);
        first.put(JudgeCache.keyFor("gpt-4o-mini", "v1", "sys", "user"), "{\"claims\":[]}");
        assertTrue(Files.isRegularFile(file));

        // new instance over the same file → persisted entries are visible
        FileJudgeCache second = new FileJudgeCache(file);
        assertEquals(Optional.of("{\"claims\":[]}"),
                second.get(JudgeCache.keyFor("gpt-4o-mini", "v1", "sys", "user")));
        assertEquals(1, second.size());
        assertEquals(Optional.empty(), second.get("missing"));
    }

    @Test
    void cacheKey_changesWhenAnyComponentChanges() {
        String base = JudgeCache.keyFor("model", "v1", "sys", "user");
        assertEquals(base, JudgeCache.keyFor("model", "v1", "sys", "user"));
        assertNotEquals(base, JudgeCache.keyFor("other-model", "v1", "sys", "user"));
        assertNotEquals(base, JudgeCache.keyFor("model", "v2", "sys", "user"));
        assertNotEquals(base, JudgeCache.keyFor("model", "v1", "other sys", "user"));
        assertNotEquals(base, JudgeCache.keyFor("model", "v1", "sys", "other user"));
    }

    @Test
    void put_doesNotOverwriteExistingEntry() {
        FileJudgeCache cache = new FileJudgeCache(tempDir.resolve("cache.yml"));
        String key = JudgeCache.keyFor("m", "v1", "s", "u");
        cache.put(key, "first");
        cache.put(key, "second");
        assertEquals(Optional.of("first"), cache.get(key));
    }

    @Test
    void concurrentPut_allEntriesSurvive() throws Exception {
        FileJudgeCache cache = new FileJudgeCache(tempDir.resolve("cache.yml"));
        Thread[] threads = new Thread[8];
        for (int t = 0; t < threads.length; t++) {
            final int id = t;
            threads[t] = new Thread(() -> cache.put("key-" + id, "value-" + id));
            threads[t].start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        assertEquals(8, cache.size());
    }
}
