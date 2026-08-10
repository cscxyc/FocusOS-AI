package com.focusos.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Sprint 8-C: 创建/更新 用户长期记忆请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateUserMemoryRequest {

    /** 所属用户 ID（必填，用于用户隔离） */
    @NotNull(message = "userId 必填")
    private Long userId;

    /** 记忆类型: SKILL / PROJECT / EXPERIENCE / GOAL / LEARNING_PROGRESS / PREFERENCE / ACHIEVEMENT */
    @NotBlank(message = "memoryType 必填")
    private String memoryType;

    /** 记忆 Key: 技能名 / 项目名 / 目标名 */
    @NotBlank(message = "memoryKey 必填")
    private String memoryKey;

    /** 记忆内容: 详细描述（如"完成Milvus性能优化实验，HNSW索引调优从100ms降到12ms"） */
    @NotBlank(message = "memoryValue 必填")
    private String memoryValue;

    /** 来源: LEARNING_COMPLETED / PROJECT_SUBMISSION / RESUME / CHAT_HISTORY / MANUAL ... */
    private String source;

    /** 可信度 0.0~1.0（空则默认 0.8） */
    private Double confidence;
}
