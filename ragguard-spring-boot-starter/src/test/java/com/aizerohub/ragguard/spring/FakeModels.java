package com.aizerohub.ragguard.spring;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.chat.messages.Message;

/**
 * Fake Spring AI model beans for auto-configuration tests: the chat model
 * returns a fixed canned response (JSON) for every call; the embedding
 * model returns a fixed unit vector.
 */
final class FakeModels {

    private FakeModels() {
    }

    static final class FakeChatModel implements ChatModel {

        private final Map<String, String> responsesByTask;
        final AtomicInteger calls = new AtomicInteger();

        /** Routes a canned response per judge task, keyed by a unique substring of the system prompt. */
        FakeChatModel(Map<String, String> responsesByTask) {
            this.responsesByTask = responsesByTask;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            calls.incrementAndGet();
            for (Message message : prompt.getInstructions()) {
                String content = message.getText();
                for (var entry : responsesByTask.entrySet()) {
                    if (content.contains(entry.getKey())) {
                        return new ChatResponse(List.of(new Generation(new AssistantMessage(entry.getValue()))));
                    }
                }
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage("{}"))));
        }
    }

    static final class FakeEmbeddingModel implements org.springframework.ai.embedding.EmbeddingModel {

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = request.getInstructions().stream()
                    .map(text -> new Embedding(new float[] {1f, 0f}, 0))
                    .toList();
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(org.springframework.ai.document.Document document) {
            return new float[] {1f, 0f};
        }
    }
}
