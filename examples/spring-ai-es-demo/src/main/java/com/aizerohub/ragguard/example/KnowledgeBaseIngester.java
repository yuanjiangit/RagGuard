package com.aizerohub.ragguard.example;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * One-time ingestion of the demo knowledge base: every {@code .md} file in
 * {@code classpath:docs/} is embedded and written to Elasticsearch. Activate
 * with the {@code ingest} profile.
 */
@Component
@Profile("ingest")
public class KnowledgeBaseIngester implements ApplicationRunner {

    private final VectorStore vectorStore;

    public KnowledgeBaseIngester(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        var resolver = new PathMatchingResourcePatternResolver();
        var resources = resolver.getResources("classpath:docs/*.md");
        for (var resource : resources) {
            String text;
            try (var in = resource.getInputStream()) {
                text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            // Simple paragraph chunking — the demo is intentionally minimal.
            for (String paragraph : text.split("\n\n+")) {
                if (paragraph.isBlank()) {
                    continue;
                }
                vectorStore.add(List.of(new Document(paragraph.strip())));
            }
            System.out.println("Ingested " + resource.getFilename());
        }
        System.out.println("Ingestion complete.");
    }
}
