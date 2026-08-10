package com.focusos.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Sprint 8-C: 手动触发 MemoryAgent 提取长期记忆的请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtractMemoryRequest {

    /** 所属用户 ID（必填） */
    @NotNull(message = "userId 必填")
    private Long userId;

    /** 行为事件类型: LEARNING_COMPLETED / PROJECT_SUBMISSION / INTERVIEW_REVIEW / CHAT_SUMMARY ... */
    @NotBlank(message = "eventType 必填")
    private String eventType;

    /** 原始行为内容（例如"完成Milvus向量检索性能优化实验，HNSW索引m=32 efSearch=128，从102ms降到12ms"） */
    @NotBlank(message = "content 必填")
    private String content;

    /** 来源标识（可选，默认 MANUAL_EXTRACT） */
    private String source;
}
