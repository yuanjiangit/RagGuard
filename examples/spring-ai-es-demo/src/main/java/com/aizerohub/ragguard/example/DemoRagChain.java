package com.aizerohub.ragguard.example;

import com.aizerohub.ragguard.core.model.RagAnswer;
import com.aizerohub.ragguard.core.model.RagChain;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The system under test: retrieve the top-3 documents from Elasticsearch,
 * then answer with Spring AI's ChatClient grounded on those contexts.
 * RagGuard evaluates whatever this chain does — no evaluation code here.
 */
@Component
public class DemoRagChain implements RagChain {

    private static final int TOP_K = 3;

    private final VectorStore vectorStore;
    private final ChatClient chatClient;

    public DemoRagChain(VectorStore vectorStore, ChatClient.Builder chatClientBuilder) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClientBuilder
                .defaultSystem("""
                        You answer questions about RagGuard based ONLY on the provided context.
                        If the context does not contain the answer, say you don't know.
                        Answer in English.
                        """)
                .build();
    }

    @Override
    public RagAnswer answer(String question) {
        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder().query(question).topK(TOP_K).build());
        List<String> contexts = documents == null ? List.of()
                : documents.stream().map(Document::getText).toList();
        String contextBlock = String.join("\n\n---\n\n", contexts);
        String answer = chatClient.prompt()
                .user("Context:\n" + contextBlock + "\n\nQuestion: " + question)
                .call()
                .content();
        return new RagAnswer(answer, contexts);
    }
}
