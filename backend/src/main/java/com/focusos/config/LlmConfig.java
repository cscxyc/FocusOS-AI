package com.focusos.config;

import com.focusos.service.LLMLoggingService;
import dev.langchain4j.http.client.HttpClientBuilder;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Duration;

@Configuration
@Profile("!mock")
public class LlmConfig {

    @Value("${focusos.ai.base-url}")
    String baseUrl;

    @Value("${focusos.ai.model}")
    String model;

    @Value("${focusos.ai.embedding-model}")
    String embeddingModel;

    @Value("${focusos.ai.api-key:}")
    String apiKey;

    @Bean
    public ChatLanguageModel chatLanguageModel(LLMLoggingService loggingService) {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("LLM_API_KEY 未配置。请设置环境变量 LLM_API_KEY 或在 application.yml 中指定 focusos.ai.api-key");
        }
        // Sprint 6-A: 使用自定义 SimpleHttpClientBuilder（JDK HttpURLConnection）
        // 解决 Jetty HttpClient 默认 30s idle timeout 导致 LLM 调用超时的问题
        HttpClientBuilder httpClientBuilder = new SimpleHttpClientBuilder()
                .readTimeout(Duration.ofSeconds(120))
                .connectTimeout(Duration.ofSeconds(15));

        ChatLanguageModel real = OpenAiChatModel.builder()
                .httpClientBuilder(httpClientBuilder)
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(model)
                .temperature(0.7)
                .maxTokens(4096)
                .timeout(Duration.ofSeconds(120))
                .logRequests(true)
                .logResponses(true)
                .build();

        // Sprint 7-C-B: 用 LoggingChatLanguageModel 装饰器包装，自动记录 LLM 调用
        return new LoggingChatLanguageModel(real, loggingService, model);
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        if (apiKey.isBlank()) {
            throw new IllegalStateException("LLM_API_KEY 未配置，无法初始化 EmbeddingModel");
        }
        HttpClientBuilder httpClientBuilder = new SimpleHttpClientBuilder()
                .readTimeout(Duration.ofSeconds(60))
                .connectTimeout(Duration.ofSeconds(15));

        return OpenAiEmbeddingModel.builder()
                .httpClientBuilder(httpClientBuilder)
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(embeddingModel)
                .dimensions(1536)
                .build();
    }
}
