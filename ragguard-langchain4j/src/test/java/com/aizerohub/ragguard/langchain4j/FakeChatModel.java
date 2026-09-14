package com.aizerohub.ragguard.langchain4j;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;

import java.util.Map;

/**
 * Fixed-output fake langchain4j ChatModel, routed per judge task by a
 * substring of the system message.
 */
final class FakeChatModel implements ChatModel {

    private final Map<String, String> responsesByTask;
    int calls;

    FakeChatModel(Map<String, String> responsesByTask) {
        this.responsesByTask = responsesByTask;
    }

    @Override
    public ChatResponse chat(dev.langchain4j.data.message.ChatMessage... messages) {
        calls++;
        String text = "{}";
        for (dev.langchain4j.data.message.ChatMessage message : messages) {
            if (message instanceof SystemMessage system) {
                String systemText = system.text();
                for (var entry : responsesByTask.entrySet()) {
                    if (systemText != null && systemText.contains(entry.getKey())) {
                        text = entry.getValue();
                        break;
                    }
                }
            }
        }
        return ChatResponse.builder().aiMessage(AiMessage.from(text)).build();
    }
}
