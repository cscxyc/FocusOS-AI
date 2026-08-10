package com.focusos.config;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import com.focusos.service.LLMLoggingService;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.util.Arrays;
import java.util.List;

@Configuration
@Profile("mock")
public class MockLlmConfig {

    @Bean
    @Primary
    public ChatLanguageModel mockChatLanguageModel(LLMLoggingService loggingService) {
        ChatLanguageModel mock = new ChatLanguageModel() {
            @Override
            public String chat(String prompt) {
                return "[MOCK LLM RESPONSE]\n" + prompt;
            }

            @Override
            public ChatResponse chat(List<ChatMessage> messages) {
                StringBuilder sb = new StringBuilder("[MOCK LLM RESPONSE]\n");
                for (ChatMessage msg : messages) {
                    sb.append(msg.type()).append(": ").append(msg.text()).append("\n");
                }
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from(sb.toString()))
                        .build();
            }
        };
        // Sprint 7-C-B: mock 模式同样包装日志装饰器
        return new LoggingChatLanguageModel(mock, loggingService, "mock-model");
    }

    @Bean
    @Primary
    public EmbeddingModel mockEmbeddingModel() {
        return new EmbeddingModel() {
            @Override
            public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
                float[] vector = new float[1536];
                Arrays.fill(vector, 0.1f);
                Embedding embedding = new Embedding(vector);
                List<Embedding> embeddings = segments.stream()
                        .map(s -> embedding)
                        .toList();
                return Response.from(embeddings);
            }
        };
    }
}