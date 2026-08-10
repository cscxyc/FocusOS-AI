package com.focusos.service;

import com.focusos.agent.LLMCallContext;
import com.focusos.agent.RAGAgent;
import com.focusos.config.MilvusCollectionInitializer;
import com.focusos.dto.request.ChatRequest;
import com.focusos.dto.response.ChatResponse;
import com.focusos.dto.response.KnowledgeDocumentResponse;
import com.focusos.entity.DocumentChunk;
import com.focusos.entity.KnowledgeDocument;
import com.focusos.exception.BusinessException;
import com.focusos.exception.ResourceNotFoundException;
import com.focusos.repository.DocumentChunkRepository;
import com.focusos.repository.KnowledgeDocumentRepository;
import com.focusos.store.MilvusEmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RAGService {

    private final KnowledgeDocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final RAGAgent ragAgent;
    // Milvus 依赖设为可选：Milvus 不可用时降级到 InMemoryEmbeddingStore，这两个 bean 不会存在
    private final ObjectProvider<MilvusEmbeddingStore> milvusEmbeddingStoreProvider;
    private final ObjectProvider<MilvusCollectionInitializer> milvusCollectionInitializerProvider;

    @Value("${focusos.file.upload-dir}")
    private String uploadDir;

    @Transactional
    public KnowledgeDocumentResponse uploadDocument(Long userId, String title, String category,
                                                     String documentType, Integer priority, String tags,
                                                     MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        String fileType = getFileExtension(originalFilename);
        String fileName = UUID.randomUUID().toString() + "_" + originalFilename;
        Path filePath = Paths.get(uploadDir, fileName);

        try {
            Files.createDirectories(filePath.getParent());
            file.transferTo(filePath.toFile());
        } catch (IOException e) {
            log.error("Failed to save file", e);
            throw new BusinessException("文件保存失败");
        }

        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setUserId(userId);
        doc.setTitle(title != null ? title : originalFilename);
        doc.setFileType(fileType);
        doc.setFilePath(filePath.toString());
        doc.setFileSize(file.getSize());
        doc.setCategory(category != null ? category : "other");
        doc.setDocumentType(documentType != null ? documentType : "other");
        doc.setPriority(priority != null ? priority : 3);
        doc.setSource("upload");
        doc.setTags(tags);
        doc.setIsVectorized(false);

        KnowledgeDocument savedDoc = documentRepository.save(doc);
        log.info("Document uploaded: {} for user: {} (category={}, type={})",
                savedDoc.getTitle(), userId, category, documentType);
        return KnowledgeDocumentResponse.fromEntity(savedDoc);
    }

    @Transactional
    public KnowledgeDocumentResponse vectorizeDocument(Long userId, Long documentId) {
        KnowledgeDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("文档", documentId));

        if (!doc.getUserId().equals(userId)) {
            throw new BusinessException("无权操作此文档");
        }

        String content = extractText(doc.getFilePath(), doc.getFileType());
        if (content == null || content.isEmpty()) {
            throw new BusinessException("文档内容为空或无法解析");
        }

        chunkRepository.deleteByDocumentId(documentId);
        // 清理 Milvus 中该文档的旧向量（防止重复/不一致）；Milvus 不可用时跳过
        deleteMilvusVectorsIfAvailable(userId, documentId);

        List<String> chunks = ragAgent.splitIntoChunks(content, 500);
        int index = 0;
        for (String chunk : chunks) {
            DocumentChunk documentChunk = new DocumentChunk();
            documentChunk.setDocumentId(documentId);
            documentChunk.setChunkIndex(index++);
            documentChunk.setContent(chunk);
            chunkRepository.save(documentChunk);
        }

        String fileName = extractFileName(doc.getFilePath());
        ragAgent.addToVectorStore(doc.getTitle(), fileName, chunks, userId, documentId,
                doc.getCategory(), doc.getDocumentType(), doc.getPriority(), doc.getSource());

        doc.setIsVectorized(true);
        KnowledgeDocument savedDoc = documentRepository.save(doc);
        log.info("Document vectorized: {} ({} chunks)", doc.getTitle(), chunks.size());
        return KnowledgeDocumentResponse.fromEntity(savedDoc);
    }

    /**
     * 重建单个文档的向量（只更新 Milvus 向量，不重新分块）
     */
    @Transactional
    public KnowledgeDocumentResponse rebuildDocumentVectors(Long userId, Long documentId) {
        KnowledgeDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("文档", documentId));
        if (!doc.getUserId().equals(userId)) {
            throw new BusinessException("无权操作此文档");
        }
        List<DocumentChunk> chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);
        if (chunks.isEmpty()) {
            return vectorizeDocument(userId, documentId);
        }
        deleteMilvusVectorsIfAvailable(userId, documentId);
        List<String> chunkTexts = chunks.stream()
                .sorted((a, b) -> Integer.compare(a.getChunkIndex(), b.getChunkIndex()))
                .map(DocumentChunk::getContent)
                .collect(Collectors.toList());
        String fileName = extractFileName(doc.getFilePath());
        ragAgent.addToVectorStore(doc.getTitle(), fileName, chunkTexts, userId, documentId,
                doc.getCategory(), doc.getDocumentType(), doc.getPriority(), doc.getSource());
        doc.setIsVectorized(true);
        KnowledgeDocument saved = documentRepository.save(doc);
        log.info("Document vectors rebuilt: docId={} chunks={}", documentId, chunkTexts.size());
        return KnowledgeDocumentResponse.fromEntity(saved);
    }

    /**
     * 该用户所有文档重建向量
     *
     * @return 重建的文档数量
     */
    @Transactional
    public int rebuildAllUserDocumentVectors(Long userId) {
        List<KnowledgeDocument> docs = documentRepository.findByUserId(userId);
        int done = 0;
        for (KnowledgeDocument doc : docs) {
            try {
                rebuildDocumentVectors(userId, doc.getId());
                done++;
            } catch (Exception e) {
                log.error("Rebuild vector failed for docId={}", doc.getId(), e);
            }
        }
        return done;
    }

    /**
     * 重建全库所有文档向量（管理员级能力，应用于 Milvus 迁移/清空恢复）
     */
    public void rebuildAllVectorsSystemWide() {
        MilvusCollectionInitializer initializer = milvusCollectionInitializerProvider.getIfAvailable();
        if (initializer != null) {
            initializer.rebuildAllVectors();
        } else {
            log.warn("Milvus is disabled — system-wide vector rebuild skipped (InMemoryEmbeddingStore does not support pre-existing data rebuild)");
        }
    }

    public ChatResponse chatWithKnowledge(Long userId, ChatRequest request) {
        // Sprint 7-C-B: 设置 LLM 调用上下文
        LLMCallContext.set(userId, null, "rag");
        try {
            String response = ragAgent.chatWithRetrieval(request.getMessage(), userId);
            return new ChatResponse(response, null, request.getAgentType(), null);
        } finally {
            LLMCallContext.clear();
        }
    }

    public String searchKnowledge(Long userId, String query) {
        // Sprint 7-C-B: 设置 LLM 调用上下文
        LLMCallContext.set(userId, null, "rag");
        try {
            return ragAgent.searchKnowledgeByUser(query, userId);
        } finally {
            LLMCallContext.clear();
        }
    }

    public List<KnowledgeDocumentResponse> getUserDocuments(Long userId) {
        return documentRepository.findByUserId(userId).stream()
                .map(KnowledgeDocumentResponse::fromEntity)
                .collect(Collectors.toList());
    }

    public List<KnowledgeDocumentResponse> getUserDocumentsByCategory(Long userId, String category) {
        return documentRepository.findByUserIdAndCategory(userId, category).stream()
                .map(KnowledgeDocumentResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteDocument(Long userId, Long documentId) {
        KnowledgeDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("文档", documentId));

        if (!doc.getUserId().equals(userId)) {
            throw new BusinessException("无权操作此文档");
        }

        // 同步删除 Milvus 中的向量，避免孤立数据；Milvus 不可用时跳过
        deleteMilvusVectorsIfAvailable(userId, documentId);

        chunkRepository.deleteByDocumentId(documentId);
        documentRepository.delete(doc);

        try {
            Files.deleteIfExists(Path.of(doc.getFilePath()));
        } catch (IOException e) {
            log.warn("Failed to delete file: {}", doc.getFilePath());
        }

        log.info("Document deleted: {}", doc.getTitle());
    }

    private String extractText(String filePath, String fileType) {
        try {
            if ("pdf".equalsIgnoreCase(fileType)) {
                try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(new File(filePath))) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    return stripper.getText(document);
                }
            } else if ("txt".equalsIgnoreCase(fileType) || "md".equalsIgnoreCase(fileType)) {
                return Files.readString(Path.of(filePath));
            } else if ("docx".equalsIgnoreCase(fileType) || "doc".equalsIgnoreCase(fileType)) {
                log.warn("Word document parsing not fully supported for: {}", filePath);
                return "Word文档内容（建议转换为PDF或TXT格式以获得更好的解析效果）";
            }
            return Files.readString(Path.of(filePath));
        } catch (Exception e) {
            log.error("Failed to extract text from: {}", filePath, e);
            return "";
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null) return "";
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(dotIndex + 1).toLowerCase() : "";
    }

    private static String extractFileName(String filePath) {
        if (filePath == null) return "";
        int idx = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        return idx >= 0 ? filePath.substring(idx + 1) : filePath;
    }

    /**
     * 如果 MilvusEmbeddingStore 可用，删除指定用户+文档的向量；否则跳过（InMemoryEmbeddingStore 模式下由新增覆盖）。
     */
    private void deleteMilvusVectorsIfAvailable(Long userId, Long documentId) {
        MilvusEmbeddingStore store = milvusEmbeddingStoreProvider.getIfAvailable();
        if (store != null) {
            try {
                store.deleteByUserAndDocument(userId, documentId);
            } catch (Exception e) {
                log.warn("Failed to delete Milvus vectors for userId={}, documentId={}: {}", userId, documentId, e.getMessage());
            }
        }
    }
}
