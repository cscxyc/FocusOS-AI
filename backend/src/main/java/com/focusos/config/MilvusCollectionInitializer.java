package com.focusos.config;

import com.focusos.entity.DocumentChunk;
import com.focusos.entity.KnowledgeDocument;
import com.focusos.repository.DocumentChunkRepository;
import com.focusos.repository.KnowledgeDocumentRepository;
import com.focusos.store.MilvusEmbeddingStore;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Spring 启动后自动执行：
 * 1. 检查 Milvus 连接，确保 collection 存在并建好索引
 * 2. 根据 focusos.milvus.auto-rebuild 决定是否自动把数据库中已有 chunk 重建为向量写入 Milvus
 *
 * 仅当 MilvusEmbeddingStore Bean 存在时才启用（focusos.milvus.enabled=true）。
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
@ConditionalOnBean(MilvusEmbeddingStore.class)
public class MilvusCollectionInitializer implements ApplicationRunner {

    private final MilvusEmbeddingStore milvusEmbeddingStore;
    private final MilvusProperties properties;
    private final KnowledgeDocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final EmbeddingModel embeddingModel;

    @Override
    public void run(ApplicationArguments args) {
        log.info("===== Milvus Auto-Init Start =====");
        long start = System.currentTimeMillis();

        try {
            milvusEmbeddingStore.ensureCollectionReady();
            log.info("Milvus collection check passed ({}ms)", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("Milvus collection initialization FAILED. Please check focusos.milvus.* config and Milvus service status.", e);
            return;
        }

        if (properties.isAutoRebuild()) {
            rebuildAllVectors();
        } else {
            log.info("Milvus auto-rebuild is disabled (focusos.milvus.auto-rebuild=false). Skipping vector rebuild.");
        }

        log.info("===== Milvus Auto-Init Done ({}ms) =====", System.currentTimeMillis() - start);
    }

    /**
     * 从数据库读取所有 chunk，重新向量化并写入 Milvus。
     * 用于迁移 InMemory -> Milvus 或 Milvus 清空后的数据恢复。
     */
    public void rebuildAllVectors() {
        log.warn("Starting full vector rebuild... (this may take a while)");
        try {
            milvusEmbeddingStore.clearAll();
        } catch (Exception e) {
            log.error("Failed to clear old vectors before rebuild", e);
        }

        List<KnowledgeDocument> docs = documentRepository.findAll();
        log.info("Found {} documents in DB for vector rebuild", docs.size());

        int vectorizedDocs = 0;
        int totalChunks = 0;
        for (KnowledgeDocument doc : docs) {
            try {
                List<DocumentChunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(doc.getId());
                if (chunks.isEmpty()) continue;

                String fileName = extractFileName(doc.getFilePath());

                for (DocumentChunk chunk : chunks) {
                    try {
                        TextSegment segment = TextSegment.from(chunk.getContent());
                        segment.metadata().put("userId", String.valueOf(doc.getUserId()));
                        segment.metadata().put("documentId", String.valueOf(doc.getId()));
                        segment.metadata().put("documentTitle", doc.getTitle() == null ? "" : doc.getTitle());
                        segment.metadata().put("fileName", fileName);
                        segment.metadata().put("chunkIndex", String.valueOf(chunk.getChunkIndex()));

                        Response<Embedding> embedResp = embeddingModel.embed(segment);
                        milvusEmbeddingStore.add(embedResp.content(), segment);
                        totalChunks++;
                    } catch (Exception ex) {
                        log.warn("Failed to embed chunk docId={} index={}", doc.getId(), chunk.getChunkIndex(), ex);
                    }
                }
                doc.setIsVectorized(true);
                documentRepository.save(doc);
                vectorizedDocs++;
            } catch (Exception e) {
                log.error("Failed to rebuild vectors for document {}", doc.getId(), e);
            }
        }
        log.info("Rebuild complete: {}/{} documents vectorized, {} chunks written.",
                vectorizedDocs, docs.size(), totalChunks);
    }

    private static String extractFileName(String filePath) {
        if (filePath == null) return "";
        int idx = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        return idx >= 0 ? filePath.substring(idx + 1) : filePath;
    }
}
