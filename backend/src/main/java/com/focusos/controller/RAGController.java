package com.focusos.controller;

import com.focusos.dto.request.ChatRequest;
import com.focusos.dto.response.ApiResponse;
import com.focusos.dto.response.ChatResponse;
import com.focusos.dto.response.KnowledgeDocumentResponse;
import com.focusos.entity.User;
import com.focusos.service.RAGService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/rag")
@RequiredArgsConstructor
public class RAGController {

    private final RAGService ragService;

    @PostMapping("/documents/upload")
    public ApiResponse<KnowledgeDocumentResponse> uploadDocument(
            @AuthenticationPrincipal User user,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "documentType", required = false) String documentType,
            @RequestParam(value = "priority", required = false, defaultValue = "3") Integer priority,
            @RequestParam(value = "tags", required = false) String tags) {
        return ApiResponse.success("文档上传成功",
                ragService.uploadDocument(user.getId(), title, category, documentType, priority, tags, file));
    }

    @PostMapping("/documents/{documentId}/vectorize")
    public ApiResponse<KnowledgeDocumentResponse> vectorizeDocument(
            @AuthenticationPrincipal User user,
            @PathVariable Long documentId) {
        return ApiResponse.success("文档向量化成功", ragService.vectorizeDocument(user.getId(), documentId));
    }

    /**
     * 重建单个文档的向量（使用 Milvus 持久化存储）。
     * 场景：Milvus 数据丢失/切换向量数据库后数据恢复。
     */
    @PostMapping("/documents/{documentId}/rebuild-vectors")
    public ApiResponse<KnowledgeDocumentResponse> rebuildDocumentVectors(
            @AuthenticationPrincipal User user,
            @PathVariable Long documentId) {
        return ApiResponse.success("文档向量重建成功",
                ragService.rebuildDocumentVectors(user.getId(), documentId));
    }

    /**
     * 重建该用户全部文档的向量。
     */
    @PostMapping("/documents/rebuild-all-vectors")
    public ApiResponse<Integer> rebuildAllUserVectors(@AuthenticationPrincipal User user) {
        int count = ragService.rebuildAllUserDocumentVectors(user.getId());
        return ApiResponse.success("已重建 " + count + " 个文档的向量", count);
    }

    /**
     * 全库向量重建（管理员级：用于 Milvus 迁移/清空后的数据恢复）。
     * 仅用于 Sprint 5-A 迁移验证场景。
     */
    @PostMapping("/vectors/system-rebuild")
    public ApiResponse<String> rebuildAllVectorsSystemWide() {
        ragService.rebuildAllVectorsSystemWide();
        return ApiResponse.success("全库向量重建已触发完成", "OK");
    }

    @GetMapping("/documents")
    public ApiResponse<List<KnowledgeDocumentResponse>> getDocuments(@AuthenticationPrincipal User user) {
        return ApiResponse.success(ragService.getUserDocuments(user.getId()));
    }

    @GetMapping("/documents/category/{category}")
    public ApiResponse<List<KnowledgeDocumentResponse>> getDocumentsByCategory(
            @AuthenticationPrincipal User user,
            @PathVariable String category) {
        return ApiResponse.success(ragService.getUserDocumentsByCategory(user.getId(), category));
    }

    @DeleteMapping("/documents/{documentId}")
    public ApiResponse<Void> deleteDocument(
            @AuthenticationPrincipal User user,
            @PathVariable Long documentId) {
        ragService.deleteDocument(user.getId(), documentId);
        return ApiResponse.success("文档删除成功", null);
    }

    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ChatRequest request) {
        return ApiResponse.success(ragService.chatWithKnowledge(user.getId(), request));
    }

    @GetMapping("/search")
    public ApiResponse<String> searchKnowledge(
            @AuthenticationPrincipal User user,
            @RequestParam String query) {
        return ApiResponse.success("搜索完成", ragService.searchKnowledge(user.getId(), query));
    }
}
