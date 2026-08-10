package com.focusos.config;

import com.focusos.store.MilvusEmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class EmbeddingStoreConfig {

    private final ObjectProvider<MilvusEmbeddingStore> milvusEmbeddingStoreProvider;
    private final MilvusProperties milvusProperties;

    /**
     * 主 EmbeddingStore：
     * - focusos.milvus.enabled=true（默认）→ MilvusEmbeddingStore（生产级持久化）
     * - focusos.milvus.enabled=false → InMemoryEmbeddingStore（降级模式，重启丢失向量）
     *
     * 降级模式仅用于本地开发/测试环境无 Milvus 服务的场景。
     */
    @Bean
    @Primary
    public EmbeddingStore<?> embeddingStore() {
        if (milvusProperties.isEnabled()) {
            MilvusEmbeddingStore milvusStore = milvusEmbeddingStoreProvider.getIfAvailable();
            if (milvusStore != null) {
                log.info("Using MilvusEmbeddingStore as primary EmbeddingStore (persistent production-grade)");
                return milvusStore;
            }
        }
        log.warn("Milvus is DISABLED or unavailable. Falling back to InMemoryEmbeddingStore. " +
                "Vectors will be LOST on restart — for local testing only.");
        return new InMemoryEmbeddingStore<>();
    }
}
