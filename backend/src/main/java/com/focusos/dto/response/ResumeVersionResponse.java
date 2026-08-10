package com.focusos.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.focusos.entity.ResumeVersion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Sprint 7-C-A: 简历版本响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResumeVersionResponse {

    private Long id;
    private Long userId;
    private String targetPosition;
    private String versionName;
    private String content;
    private Long sourceReportId;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ResumeVersionResponse fromEntity(ResumeVersion entity) {
        return ResumeVersionResponse.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .targetPosition(entity.getTargetPosition())
                .versionName(entity.getVersionName())
                .content(entity.getContent())
                .sourceReportId(entity.getSourceReportId())
                .isActive(entity.getIsActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * 列表视图（不含 content 大字段，避免传输过大）
     */
    public static ResumeVersionResponse fromEntitySummary(ResumeVersion entity) {
        return ResumeVersionResponse.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .targetPosition(entity.getTargetPosition())
                .versionName(entity.getVersionName())
                .sourceReportId(entity.getSourceReportId())
                .isActive(entity.getIsActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .content(null)  // 列表不返回 content
                .build();
    }
}
