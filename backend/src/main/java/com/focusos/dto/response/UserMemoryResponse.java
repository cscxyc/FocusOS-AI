package com.focusos.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.focusos.entity.UserMemory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Sprint 8-C: 用户长期记忆响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserMemoryResponse {

    private Long id;
    private Long userId;

    /** SKILL / PROJECT / EXPERIENCE / GOAL / LEARNING_PROGRESS / PREFERENCE / ACHIEVEMENT */
    private String memoryType;

    /** 技能名 / 项目名 等 key */
    private String memoryKey;

    /** 记忆描述 */
    private String memoryValue;

    /** 来源 */
    private String source;

    /** 置信度 0.0~1.0 */
    private Double confidence;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 从 Entity 转换为响应 DTO
     */
    public static UserMemoryResponse from(UserMemory entity) {
        if (entity == null) return null;
        return UserMemoryResponse.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .memoryType(entity.getMemoryType())
                .memoryKey(entity.getMemoryKey())
                .memoryValue(entity.getMemoryValue())
                .source(entity.getSource())
                .confidence(entity.getConfidence())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public static List<UserMemoryResponse> fromList(List<UserMemory> list) {
        if (list == null) return List.of();
        return list.stream().map(UserMemoryResponse::from).collect(Collectors.toList());
    }
}
