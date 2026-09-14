package com.aizerohub.ragguard.example;

import com.aizerohub.ragguard.core.model.RagAnswer;
import com.aizerohub.ragguard.core.model.RagChain;

import java.util.List;

/**
 * The demo system under test: a trivial static knowledge base with a
 * keyword-overlap retriever and a langchain4j chat model as generator.
 * Swap the retriever for a real embedding/ES search in your app —
 * RagGuard evaluates whatever this interface does.
 */
public final class DemoRagChain implements RagChain {

    private static final List<String> KNOWLEDGE_BASE = List.of(
            "RagGuard is a quality-assurance framework for RAG applications on the JVM. "
                    + "It turns RAG evaluation into unit tests with four Ragas-paper metrics.",
            "RagGuard's LLM-as-Judge runs at temperature 0 with structured JSON output and "
                    + "marks JUDGE_FAILED instead of silently scoring when the judge output cannot be parsed.",
            "The four RagGuard metrics are faithfulness, answer relevance, context recall and "
                    + "context precision. Context precision measures the ranking quality of retrieved chunks.");

    private final dev.langchain4j.model.chat.ChatModel chatModel;

    public DemoRagChain(dev.langchain4j.model.chat.ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public RagAnswer answer(String question) {
        // keyword-overlap "retrieval" — intentionally simple
        List<String> contexts = KNOWLEDGE_BASE.stream()
                .filter(doc -> overlap(question, doc) > 0)
                .limit(3)
                .toList();
        String contextBlock = String.join("\n\n---\n\n", contexts);
        String answer = chatModel.chat(
                "Answer based ONLY on the context.\n\nContext:\n" + contextBlock + "\n\nQuestion: " + question);
        return new RagAnswer(answer, contexts);
    }

    private static long overlap(String question, String doc) {
        String d = doc.toLowerCase();
        return java.util.Arrays.stream(question.toLowerCase().split("\\W+"))
                .filter(word -> !word.isBlank() && d.contains(word))
                .distinct()
                .count();
    }
}
