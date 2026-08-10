package com.focusos.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.ConnectParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "focusos.milvus.enabled", havingValue = "true", matchIfMissing = true)
public class MilvusConfig {

    private final MilvusProperties properties;

    @Bean(destroyMethod = "close")
    public MilvusServiceClient milvusServiceClient() {
        ConnectParam.Builder builder = ConnectParam.newBuilder()
                .withHost(properties.getHost())
                .withPort(properties.getPort());

        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            builder.withAuthorization(properties.getUsername(),
                    properties.getPassword() == null ? "" : properties.getPassword());
        }

        log.info("Connecting to Milvus: {}:{} (collection={}, dim={})",
                properties.getHost(), properties.getPort(),
                properties.getCollectionName(), properties.getEmbeddingDimension());

        return new MilvusServiceClient(builder.build());
    }
}
