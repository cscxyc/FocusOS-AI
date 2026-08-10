package com.focusos.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "focusos.milvus")
public class MilvusProperties {

    private String host = "localhost";
    private int port = 19530;
    private String username = "";
    private String password = "";
    private String collectionName = "focusos_rag_vectors";
    private int embeddingDimension = 1536;
    private boolean autoInit = true;
    private boolean autoRebuild = false;
    /**
     * 是否启用 Milvus。false 时降级到 InMemoryEmbeddingStore（本地测试用）。
     */
    private boolean enabled = true;
}
