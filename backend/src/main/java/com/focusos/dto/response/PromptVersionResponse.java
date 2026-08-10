package com.focusos.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.focusos.entity.PromptVersion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Sprint 8-D: Prompt 版本响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PromptVersionResponse {

    private Long id;
    private String agentType;
    private String version;
    private String promptContent;
    private Boolean enabled;
    private String description;
    private Double avgScore;
    private Integer evalCount;
    private String createdAt;

    /**
     * 从 Entity 转换为响应 DTO
     */
    public static PromptVersionResponse from(PromptVersion p) {
        if (p == null) return null;
        return PromptVersionResponse.builder()
                .id(p.getId())
                .agentType(p.getAgentType())
                .version(p.getVersion())
                .promptContent(p.getPromptContent())
                .enabled(p.getEnabled())
                .description(p.getDescription())
                .avgScore(p.getAvgScore())
                .evalCount(p.getEvalCount())
                .createdAt(p.getCreatedAt() != null ? p.getCreatedAt().toString() : null)
                .build();
    }

    public static List<PromptVersionResponse> fromList(List<PromptVersion> list) {
        if (list == null) return List.of();
        return list.stream().map(PromptVersionResponse::from).collect(Collectors.toList());
    }
}
