package com.aizerohub.ragguard.core.judge;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * File-backed {@link JudgeCache}: a single YAML file mapping cache keys to
 * raw judge responses, loaded lazily and rewritten atomically on insert.
 * Intended for a local working directory (the cache is not meant to be
 * shared or committed; add its file name to .gitignore).
 *
 * <p>Thread-safe (synchronized); writes are full-file rewrites of an
 * append-mostly map — fine for evaluation-sized caches (thousands of
 * entries at most).
 */
public final class FileJudgeCache implements JudgeCache {

    /** Default cache file name; add to .gitignore when enabled. */
    public static final String DEFAULT_FILE_NAME = "ragguard-judge-cache.yml";

    private final Path file;
    private final Object lock = new Object();
    private volatile Map<String, String> entries;
    private volatile boolean dirty;

    public FileJudgeCache(Path file) {
        this.file = file;
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(load().get(key));
    }

    @Override
    public void put(String key, String rawResponse) {
        synchronized (lock) {
            Map<String, String> map = load();
            if (rawResponse == null || map.containsKey(key)) {
                return; // never overwrite: a cached verdict is frozen history
            }
            map.put(key, rawResponse);
            dirty = true;
            save();
        }
    }

    /** Number of cached entries (for tests and cache-size reporting). */
    public int size() {
        return load().size();
    }

    private Map<String, String> load() {
        Map<String, String> map = entries;
        if (map != null) {
            return map;
        }
        synchronized (lock) {
            if (entries != null) {
                return entries;
            }
            entries = readFromDisk();
            return entries;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> readFromDisk() {
        if (!Files.isRegularFile(file)) {
            return new LinkedHashMap<>();
        }
        try {
            Object loaded = new Yaml().load(Files.readString(file, StandardCharsets.UTF_8));
            if (loaded == null) {
                return new LinkedHashMap<>();
            }
            if (!(loaded instanceof Map)) {
                throw new IllegalStateException("judge cache file is malformed: " + file);
            }
            return new LinkedHashMap<>((Map<String, String>) loaded);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read judge cache: " + file, e);
        } catch (RuntimeException e) {
            throw new IllegalStateException("cannot parse judge cache file: " + file, e);
        }
    }

    private void save() {
        if (!dirty) {
            return;
        }
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            String yaml = new Yaml(options).dump(entries);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, yaml, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            dirty = false;
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write judge cache: " + file, e);
        }
    }
}
