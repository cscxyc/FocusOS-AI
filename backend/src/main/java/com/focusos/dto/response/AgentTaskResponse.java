package com.focusos.dto.response;

import com.focusos.entity.AgentTask;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTaskResponse {
    private Long id;
    private String goal;
    private String taskType;
    private String agentType;
    private String status;
    private Long dependsOn;
    private String result;
    private String inputParams;
    private String errorMessage;
    private String workflowId;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    /** Sprint 6-B: 任务开始执行时间 */
    private LocalDateTime startedAt;
    /** Sprint 6-B: 任务耗时（毫秒） */
    private Long durationMs;

    public static AgentTaskResponse fromEntity(AgentTask task) {
        return AgentTaskResponse.builder()
                .id(task.getId())
                .goal(task.getGoal())
                .taskType(task.getTaskType())
                .agentType(task.getAgentType())
                .status(task.getStatus())
                .dependsOn(task.getDependsOn())
                .result(task.getResult())
                .inputParams(task.getInputParams())
                .errorMessage(task.getErrorMessage())
                .workflowId(task.getWorkflowId())
                .createdAt(task.getCreatedAt())
                .completedAt(task.getCompletedAt())
                .startedAt(task.getStartedAt())
                .durationMs(task.getDurationMs())
                .build();
    }
}
