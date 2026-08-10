package com.focusos.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.focusos.entity.AgentEvaluationRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Sprint 8-D: Agent 评估记录响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EvaluationRecordResponse {

    private Long id;
    private Long userId;
    private String workflowId;
    private String agentType;
    private String evaluationType;
    private String input;
    private String output;
    private Integer score;
    private String metricsJson;
    private String feedback;
    private String promptVersion;
    private String createdAt;

    /**
     * 从 Entity 转换为响应 DTO
     */
    public static EvaluationRecordResponse from(AgentEvaluationRecord e) {
        if (e == null) return null;
        return EvaluationRecordResponse.builder()
                .id(e.getId())
                .userId(e.getUserId())
                .workflowId(e.getWorkflowId())
                .agentType(e.getAgentType())
                .evaluationType(e.getEvaluationType())
                .input(e.getInput())
                .output(e.getOutput())
                .score(e.getScore())
                .metricsJson(e.getMetricsJson())
                .feedback(e.getFeedback())
                .promptVersion(e.getPromptVersion())
                .createdAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null)
                .build();
    }

    public static List<EvaluationRecordResponse> fromList(List<AgentEvaluationRecord> list) {
        if (list == null) return List.of();
        return list.stream().map(EvaluationRecordResponse::from).collect(Collectors.toList());
    }
}
